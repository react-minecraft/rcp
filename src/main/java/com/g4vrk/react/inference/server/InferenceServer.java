package com.g4vrk.react.inference.server;

import com.g4vrk.react.api.rcp.RCPAPI;
import com.g4vrk.react.inference.config.ActionsConfig;
import com.g4vrk.react.inference.http.LimitedBodyHandler;
import com.g4vrk.react.inference.http.model.InferencePayload;
import com.g4vrk.react.inference.link.Credential;
import com.g4vrk.react.inference.link.RequestSigner;
import com.g4vrk.react.inference.model.ModelId;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The HTTP transport and model-inference client to the React site.
 *
 * <p>Account endpoints are delegated to {@link AccountClient}. Model requests carry the access
 * token inside the FlatBuffers body.</p>
 */
public final class InferenceServer {

    private static final @Nullable String LOCAL_TOKEN = setting("react.api.token");
    public static final String BASE_URL = baseUrl();

    private static final String MODEL_PATH = "/api/v2/inference/";
    private static final String RCP_VERSION_HEADER = "X-React-RCP-Version";
    private static final String PLUGIN_VERSION_HEADER = "X-Plugin-Version";
    private static final long MODEL_REFRESH_NANOS = TimeUnit.SECONDS.toNanos(60L);
    private static final LimitedBodyHandler BODY_HANDLER = new LimitedBodyHandler(64 * 1024);
    private static final Method HTTP_CLIENT_SHUTDOWN_NOW = findShutdownMethod();

    private final Logger logger;
    private final HttpClient client;
    private final Duration callTimeout;
    private final String fingerprint;
    private final String rcpVersion;
    private final String pluginVersion;
    private final Map<ModelId, URI> modelUrls;
    private final AccountClient account;

    private volatile @Nullable Credential credential;
    private volatile Set<ModelId> enabledModels = Set.of();
    private final AtomicLong nextModelRefresh = new AtomicLong(System.nanoTime());

    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong requestEpoch = new AtomicLong();
    private final Set<CompletableFuture<?>> inFlight = ConcurrentHashMap.newKeySet();

    /**
     * @param logger         logger for connection warnings
     * @param connectTimeout HTTP connect timeout
     * @param callTimeout    HTTP request timeout
     * @param credential     the credential from {@code .link}, or null while the server is not linked
     * @param fingerprint    hashed server identity
     * @param rcpVersion     RCP version header value
     * @param pluginVersion  React plugin version header value
     * @param reasons        what a failed check or link reports
     */
    public InferenceServer(
            final @NotNull Logger logger,
            final @NotNull Duration connectTimeout,
            final @NotNull Duration callTimeout,
            final @Nullable Credential credential,
            final @NotNull String fingerprint,
            final @NotNull String rcpVersion,
            final @NotNull String pluginVersion,
            final @NotNull ActionsConfig.ReasonSet reasons
    ) {

        this.logger = logger;
        this.callTimeout = callTimeout;
        this.credential = credential;
        this.fingerprint = fingerprint;
        this.rcpVersion = rcpVersion;
        this.pluginVersion = pluginVersion;
        /*
         * The access token must never follow a redirect to another host, and plain HTTP
         * backends reject Java's default h2c upgrade attempt on POST requests.
         */
        this.client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .version(httpVersionFor(BASE_URL))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.modelUrls = modelUrls();
        this.account = new AccountClient(this, logger, fingerprint, reasons);

    }

    public static @NotNull HttpClient.Version httpVersionFor(final @NotNull String baseUrl) {

        return baseUrl.regionMatches(true, 0, "http:", 0, 5)
                ? HttpClient.Version.HTTP_1_1
                : HttpClient.Version.HTTP_2;

    }

    /**
     * Returns the process credential for an explicitly configured loopback backend.
     */
    public static @Nullable Credential localCredential() {

        return LOCAL_TOKEN == null ? null : Credential.unsigned(LOCAL_TOKEN.strip());

    }

    private static @NotNull String baseUrl() {

        final String environmentUrl = System.getenv("react.api.url");
        if (environmentUrl == null && LOCAL_TOKEN == null) {
            return RCPAPI.API_URL;
        }
        final String url = environmentUrl == null ? System.getProperty("react.api.url") : environmentUrl;
        if (url == null || LOCAL_TOKEN == null || LOCAL_TOKEN.isBlank()) {
            throw new IllegalArgumentException("Set both react.api.url and react.api.token for the local backend");
        }
        if (!LOCAL_TOKEN.strip().matches("[A-Za-z0-9_.:-]+")) {
            throw new IllegalArgumentException("Invalid react.api.token");
        }
        final URI origin;
        try {
            origin = URI.create(url.strip());
        } catch (final IllegalArgumentException ignored) {
            throw new IllegalArgumentException("react.api.url must be a loopback HTTP or HTTPS origin");
        }
        final String host = origin.getHost();
        if (!"127.0.0.1".equals(host) && !"[::1]".equals(host)) {
            throw new IllegalArgumentException("react.api.url must use 127.0.0.1 or [::1] with a process token");
        }
        if (!("http".equalsIgnoreCase(origin.getScheme()) || "https".equalsIgnoreCase(origin.getScheme()))
                || origin.getUserInfo() != null || origin.getQuery() != null || origin.getFragment() != null
                || !origin.getPath().isEmpty() && !"/".equals(origin.getPath())) {
            throw new IllegalArgumentException("react.api.url must be a loopback HTTP or HTTPS origin");
        }
        return url.strip().replaceFirst("/$", "");

    }

    private static @Nullable String setting(final @NotNull String name) {

        final String value = System.getenv(name);
        return value == null ? System.getProperty(name) : value;

    }

    public @NotNull AccountClient account() {

        return account;

    }

    public boolean isLinked() {

        return credential != null;

    }

    /**
     * Switches to a credential the owner just linked and asks the site for its models at once.
     */
    public synchronized void use(final @NotNull Credential linked) {

        credential = linked;
        enabledModels = Set.of();
        nextModelRefresh.set(System.nanoTime());
        requestEpoch.incrementAndGet();

    }

    public long requestEpoch() {

        return requestEpoch.get();

    }

    /**
     * Lock-free check whether this server is still open and on the given epoch.
     */
    public boolean isCurrent(final long epoch) {

        return !closed.get() && requestEpoch.get() == epoch;

    }

    /**
     * @return the access token sent inside model requests
     */
    public @Nullable String accessToken() {

        final Credential current = credential;
        return current == null ? null : current.accessToken();

    }

    /**
     * The models enabled on the site for this server, refreshed in the background once a minute.
     */
    public @NotNull Set<ModelId> enabledModels() {

        refreshModelsIfDue();
        return enabledModels;

    }

    public boolean isModelEnabled(final @NotNull ModelId model) {

        return !closed.get() && enabledModels().contains(model);

    }

    /**
     * Sends one model request; its answer is capped in size and the call is cancelled on shutdown.
     */
    public @NotNull CompletableFuture<HttpResponse<byte[]>> callAsync(
            final @NotNull InferencePayload payload,
            final @NotNull ModelId model
    ) {

        if (closed.get()) {
            throw new IllegalStateException("The inference client is closed");
        }
        final HttpRequest.Builder builder = HttpRequest.newBuilder(modelUrls.get(model))
                .POST(BodyPublishers.ofByteArray(payload.body()))
                .header("Content-Type", InferencePayload.FLATBUFFERS)
                .header("Accept", InferencePayload.FLATBUFFERS)
                /*
                 * Several models run side by side, so the request names its own model.
                 */
                .header("X-React-Player-Scoped", "true")
                .header(RCP_VERSION_HEADER, rcpVersion)
                .header(PLUGIN_VERSION_HEADER, pluginVersion);
        payload.headers().forEach(builder::header);

        final CompletableFuture<HttpResponse<byte[]>> call = client.sendAsync(withTimeout(builder).build(), BODY_HANDLER);
        inFlight.add(call);
        if (closed.get()) {
            call.cancel(true);
            inFlight.remove(call);
            throw new IllegalStateException("The inference client is closed");
        }
        call.whenComplete((response, failure) -> inFlight.remove(call));
        return call;

    }

    public synchronized void shutdown() {

        closed.set(true);
        requestEpoch.incrementAndGet();
        account.cancelPendingLogins();
        inFlight.forEach(call -> call.cancel(true));
        inFlight.clear();
        if (HTTP_CLIENT_SHUTDOWN_NOW == null) {
            return;
        }
        try {
            HTTP_CLIENT_SHUTDOWN_NOW.invoke(client);
        } catch (final IllegalAccessException | InvocationTargetException exception) {
            logger.debug("Could not shut down the inference HTTP client", exception);
        }

    }

    boolean isClosed() {

        return closed.get();

    }

    synchronized void updateEnabledModelsIfCurrent(final long epoch, final @NotNull Set<ModelId> models) {

        if (isCurrent(epoch)) {
            enabledModels = models;
        }

    }

    synchronized void markModelRefreshSuccess(final long epoch, final @NotNull Set<ModelId> models) {

        if (isCurrent(epoch)) {
            enabledModels = models;
            nextModelRefresh.set(System.nanoTime() + MODEL_REFRESH_NANOS);
        }

    }

    synchronized void clearEnabledModelsIfCurrent(final long epoch, final @NotNull Runnable onCleared) {

        if (isCurrent(epoch)) {
            enabledModels = Set.of();
            onCleared.run();
        }

    }

    @NotNull CompletableFuture<HttpResponse<byte[]>> sendAccountRequest(
            final @NotNull String method,
            final @NotNull String path,
            final byte @NotNull [] body
    ) {

        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("The inference client is closed"));
        }
        final HttpRequest.Builder builder = HttpRequest.newBuilder(apiUrl(path))
                .header(RCP_VERSION_HEADER, rcpVersion)
                .header(PLUGIN_VERSION_HEADER, pluginVersion)
                .header(RequestSigner.FINGERPRINT_HEADER, fingerprint);
        if (method.equals("POST")) {
            builder.header("Content-Type", "application/json; charset=utf-8")
                    .POST(BodyPublishers.ofByteArray(body));
        } else {
            builder.GET();
        }
        final Credential current = credential;
        if (current != null) {
            builder.header("Authorization", "Bearer " + current.accessToken());
            final KeyPair keys = current.keys();
            if (keys != null) {
                final long timestamp = System.currentTimeMillis() / 1000L;
                final String message = RequestSigner.message(method, path, fingerprint, timestamp, body);
                builder.header(RequestSigner.TIMESTAMP_HEADER, Long.toString(timestamp))
                        .header(RequestSigner.SIGNATURE_HEADER, RequestSigner.sign(keys.getPrivate(), message));
            }
        }
        final CompletableFuture<HttpResponse<byte[]>> call = client.sendAsync(withTimeout(builder).build(), BODY_HANDLER);
        inFlight.add(call);
        if (closed.get()) {
            call.cancel(true);
            inFlight.remove(call);
            return call;
        }
        call.whenComplete((response, failure) -> inFlight.remove(call));
        return call;

    }

    private void refreshModelsIfDue() {

        final long now = System.nanoTime();
        final long next = nextModelRefresh.get();
        if (now - next < 0L || !nextModelRefresh.compareAndSet(next, now + MODEL_REFRESH_NANOS)) {
            return;
        }
        if (credential == null || closed.get()) {
            enabledModels = Set.of();
            return;
        }
        account.refreshModels(requestEpoch.get());

    }

    private @NotNull HttpRequest.Builder withTimeout(final @NotNull HttpRequest.Builder builder) {

        return callTimeout.isZero() || callTimeout.isNegative() ? builder : builder.timeout(callTimeout);

    }

    private static @NotNull URI apiUrl(final @NotNull String path) {

        return URI.create(BASE_URL + path);

    }

    private static @NotNull Map<ModelId, URI> modelUrls() {

        final Map<ModelId, URI> urls = new EnumMap<>(ModelId.class);
        for (final ModelId model : ModelId.values()) {
            urls.put(
                    model,
                    apiUrl(MODEL_PATH
                            + "?model-family=" + URLEncoder.encode(model.family(), StandardCharsets.UTF_8)
                            + "&model-name=" + URLEncoder.encode(model.modelName(), StandardCharsets.UTF_8))
            );
        }
        return urls;

    }

    private static @Nullable Method findShutdownMethod() {

        try {
            return HttpClient.class.getMethod("shutdownNow");
        } catch (final NoSuchMethodException ignored) {
            return null;
        }

    }

}
