package com.g4vrk.react.inference.server;

import com.g4vrk.react.api.rcp.RCPAPI;
import com.g4vrk.react.inference.config.ActionsConfig;
import com.g4vrk.react.inference.http.LimitedBodyHandler;
import com.g4vrk.react.inference.http.model.HttpRequest;
import com.g4vrk.react.inference.json.InferenceJsonCodec;
import com.g4vrk.react.inference.link.Credential;
import com.g4vrk.react.inference.link.RequestSigner;
import com.g4vrk.react.inference.model.ModelId;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The connection to the React site: model calls, the enabled models and linking this server.
 * <p>
 * Account requests carry the fingerprint and, once linked, the access token as a Bearer token and an Ed25519
 * signature of the request. Model requests carry the access token inside the FlatBuffers body.
 */
public final class InferenceServer {

    public static final String BASE_URL = RCPAPI.API_URL;

    private static final String MODEL_PATH = "/api/v2/inference/";
    private static final String CHECK_PATH = "/api/v1/auth/check";
    private static final String RCP_VERSION_HEADER = "X-React-RCP-Version";
    private static final String PLUGIN_VERSION_HEADER = "X-Plugin-Version";
    private static final long MODEL_REFRESH_MILLIS = 60_000L;
    private static final LimitedBodyHandler BODY_HANDLER = new LimitedBodyHandler(64 * 1024);
    private static final Method HTTP_CLIENT_SHUTDOWN_NOW = findShutdownMethod();

    private final Logger logger;
    private final HttpClient client;
    private final Duration callTimeout;
    private final String fingerprint;
    private final String rcpVersion;
    private final String pluginVersion;
    private final Map<ModelId, URI> modelUrls;
    private final ActionsConfig.Reasons reasons;

    private volatile @Nullable Credential credential;
    private volatile Set<ModelId> enabledModels = Set.of();
    private final AtomicLong nextModelRefresh = new AtomicLong();

    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong requestEpoch = new AtomicLong();
    private final AtomicLong deviceLogin = new AtomicLong();
    private final Set<CompletableFuture<?>> inFlight = ConcurrentHashMap.newKeySet();

    /**
     * @param credential the credential from {@code .link}, or null while the server is not linked
     * @param reasons    what a failed check or link reports
     */
    public InferenceServer(
            final @NotNull Logger logger,
            final @NotNull Duration connectTimeout,
            final @NotNull Duration callTimeout,
            final @Nullable Credential credential,
            final @NotNull String fingerprint,
            final @NotNull String rcpVersion,
            final @NotNull String pluginVersion,
            final @NotNull ActionsConfig.Reasons reasons
    ) {
        this.logger = logger;
        this.callTimeout = callTimeout;
        this.credential = credential;
        this.reasons = reasons;
        this.fingerprint = fingerprint;
        this.rcpVersion = rcpVersion;
        this.pluginVersion = pluginVersion;
        // The access token must never follow a redirect to another host.
        this.client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.modelUrls = modelUrls();
    }

    public boolean isLinked() {
        return credential != null;
    }

    /**
     * Switches to a credential the owner just linked and asks the site for its models at once.
     */
    public void use(final @NotNull Credential linked) {
        credential = linked;
        enabledModels = Set.of();
        nextModelRefresh.set(0L);
        requestEpoch.incrementAndGet();
    }

    public long requestEpoch() {
        return requestEpoch.get();
    }

    /**
     * Runs the final publication of an answer unless the server shut down or reset since the request.
     * <p>
     * Synchronized with {@link #shutdown()}, so nothing is published after it returns.
     */
    public synchronized void publishIfCurrent(final long epoch, final @NotNull Runnable publication) {
        if (!closed.get() && requestEpoch.get() == epoch) {
            publication.run();
        }
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

    private void refreshModelsIfDue() {
        final long now = System.currentTimeMillis();
        final long next = nextModelRefresh.get();
        if (now < next || !nextModelRefresh.compareAndSet(next, now + MODEL_REFRESH_MILLIS)) {
            return;
        }
        if (credential == null || closed.get()) {
            enabledModels = Set.of();
            return;
        }
        try {
            get(CHECK_PATH).whenComplete((response, failure) -> {
                if (failure != null) {
                    // A network failure keeps the last known models instead of stopping every check.
                    logger.debug("Could not refresh the enabled models", failure);
                    return;
                }
                if (response.statusCode() == 401 || response.statusCode() == 403) {
                    enabledModels = Set.of();
                    logger.warn("The site refused this server's link: link it again with /react link");
                    return;
                }
                if (!isSuccessful(response)) {
                    return;
                }
                try {
                    enabledModels = readEnabledModels(readJson(response));
                } catch (final RuntimeException exception) {
                    logger.debug("Could not read the enabled models", exception);
                }
            });
        } catch (final RuntimeException exception) {
            logger.debug("Could not refresh the enabled models", exception);
        }
    }

    /**
     * The outcome of {@code /react link check}.
     *
     * @param models  every model the account can use, mapped to whether it is enabled
     * @param problem why the check failed, or null
     */
    public record ConnectionStatus(String server, Map<ModelId, Boolean> models, @Nullable String problem) {

        static ConnectionStatus failed(final String problem) {
            return new ConnectionStatus("", Map.of(), problem);
        }

    }

    /**
     * Asks the site about this server's link right now and updates the enabled models.
     */
    public @NotNull CompletableFuture<ConnectionStatus> checkConnection() {
        if (credential == null) {
            return CompletableFuture.completedFuture(ConnectionStatus.failed(reasons.notLinked()));
        }
        return get(CHECK_PATH).handle((response, failure) -> {
            if (failure != null) {
                return ConnectionStatus.failed(String.valueOf(failure.getMessage()));
            }
            try {
                final Map<String, Object> json = readJson(response);
                if (!isSuccessful(response)) {
                    final Object detail = json.get("detail");
                    return ConnectionStatus.failed(detail != null ? String.valueOf(detail) : http(response));
                }
                enabledModels = readEnabledModels(json);
                nextModelRefresh.set(System.currentTimeMillis() + MODEL_REFRESH_MILLIS);
                return new ConnectionStatus(String.valueOf(json.get("server")), readModelStates(json), null);
            } catch (final RuntimeException exception) {
                return ConnectionStatus.failed(isSuccessful(response)
                        ? String.valueOf(exception.getMessage()) : http(response));
            }
        });
    }

    /**
     * The outcome of a link attempt.
     *
     * @param credential the new credential, or null when the site did not accept the link
     * @param models     every model the account can use, mapped to whether it is enabled
     */
    public record ConnectResult(
            @Nullable Credential credential,
            String server,
            Map<ModelId, Boolean> models,
            String detail
    ) {

        static ConnectResult refused(final String detail) {
            return new ConnectResult(null, "", Map.of(), detail);
        }

    }

    /**
     * Exchanges a code from the server's page on the site for this server's credential.
     */
    public @NotNull CompletableFuture<ConnectResult> connect(final @NotNull String code) {
        final KeyPair keys = RequestSigner.newKeyPair();
        return post("/api/v1/auth/connect", Map.of(
                "code", code,
                "fingerprint", fingerprint,
                "public_key", RequestSigner.publicKey(keys.getPublic())))
                .thenApply(response -> connectResult(response, keys));
    }

    /**
     * The link {@code /react link} prints: the owner opens it and approves the server.
     */
    public record DeviceLink(String url, String code, long expiresInSeconds) {
    }

    /**
     * Starts a link login and completes once the owner answers on the site.
     * <p>
     * A newer login replaces the one still waiting.
     *
     * @param onLink receives the link to show the owner
     */
    public @NotNull CompletableFuture<ConnectResult> connectWithLink(final @NotNull Consumer<DeviceLink> onLink) {
        final long attempt = deviceLogin.incrementAndGet();
        final KeyPair keys = RequestSigner.newKeyPair();
        return post("/api/v1/auth/device", Map.of("fingerprint", fingerprint)).thenCompose(response -> {
            final Map<String, Object> json;
            try {
                json = readJson(response);
            } catch (final RuntimeException exception) {
                return CompletableFuture.completedFuture(ConnectResult.refused(http(response)));
            }
            if (!isSuccessful(response) || !(json.get("device_code") instanceof String deviceCode)
                    || !(json.get("expires_in") instanceof Number expiresIn)) {
                return CompletableFuture.completedFuture(ConnectResult.refused(String.valueOf(json.get("detail"))));
            }
            final long interval = json.get("interval") instanceof Number number ? Math.max(1L, number.longValue()) : 3L;
            onLink.accept(new DeviceLink(String.valueOf(json.get("verification_uri_complete")),
                    String.valueOf(json.get("user_code")), expiresIn.longValue()));
            final Map<String, Object> body = Map.of(
                    "device_code", deviceCode,
                    "fingerprint", fingerprint,
                    "public_key", RequestSigner.publicKey(keys.getPublic()));
            return pollDeviceLogin(attempt, body, keys, interval,
                    System.currentTimeMillis() + expiresIn.longValue() * 1000L);
        });
    }

    private CompletableFuture<ConnectResult> pollDeviceLogin(
            final long attempt,
            final Map<String, Object> body,
            final KeyPair keys,
            final long intervalSeconds,
            final long deadlineMillis
    ) {
        if (attempt != deviceLogin.get() || closed.get()) {
            return CompletableFuture.completedFuture(ConnectResult.refused(reasons.replaced()));
        }
        if (System.currentTimeMillis() > deadlineMillis) {
            return CompletableFuture.completedFuture(ConnectResult.refused(reasons.expired()));
        }
        return CompletableFuture.runAsync(() -> { }, CompletableFuture.delayedExecutor(intervalSeconds, TimeUnit.SECONDS))
                .thenCompose(ignored -> post("/api/v1/auth/device/token", body))
                .thenCompose(response -> response.statusCode() == 202
                        ? pollDeviceLogin(attempt, body, keys, intervalSeconds, deadlineMillis)
                        : CompletableFuture.completedFuture(connectResult(response, keys)));
    }

    /**
     * Sends one model request; its answer is capped in size and the call is cancelled on shutdown.
     */
    public @NotNull CompletableFuture<HttpResponse<byte[]>> callAsync(
            final @NotNull HttpRequest httpRequest,
            final @NotNull ModelId model
    ) {
        if (closed.get()) {
            throw new IllegalStateException("The inference client is closed");
        }
        final java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder(modelUrls.get(model))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(httpRequest.getBody()))
                .header("Content-Type", HttpRequest.FLATBUFFERS)
                .header("Accept", HttpRequest.FLATBUFFERS)
                // Several models run side by side, so the request names its own model.
                .header("X-React-Player-Scoped", "true")
                .header(RCP_VERSION_HEADER, rcpVersion)
                .header(PLUGIN_VERSION_HEADER, pluginVersion);
        httpRequest.getHeaders().forEach(builder::header);

        final CompletableFuture<HttpResponse<byte[]>> call = client.sendAsync(withTimeout(builder).build(), BODY_HANDLER);
        inFlight.add(call);
        call.whenComplete((response, failure) -> inFlight.remove(call));
        return call;
    }

    public synchronized void shutdown() {
        closed.set(true);
        requestEpoch.incrementAndGet();
        deviceLogin.incrementAndGet();
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

    private CompletableFuture<HttpResponse<byte[]>> get(final String path) {
        return sendAccountRequest("GET", path, new byte[0]);
    }

    private CompletableFuture<HttpResponse<byte[]>> post(final String path, final Map<String, Object> json) {
        return sendAccountRequest("POST", path, InferenceJsonCodec.writeObject(json).getBytes(StandardCharsets.UTF_8));
    }

    private CompletableFuture<HttpResponse<byte[]>> sendAccountRequest(
            final String method,
            final String path,
            final byte[] body
    ) {
        final java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder(apiUrl(path))
                .header(RCP_VERSION_HEADER, rcpVersion)
                .header(PLUGIN_VERSION_HEADER, pluginVersion)
                .header(RequestSigner.FINGERPRINT_HEADER, fingerprint);
        if (method.equals("POST")) {
            builder.header("Content-Type", "application/json; charset=utf-8")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(body));
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
        return client.sendAsync(withTimeout(builder).build(), BODY_HANDLER);
    }

    private java.net.http.HttpRequest.Builder withTimeout(final java.net.http.HttpRequest.Builder builder) {
        return callTimeout.isZero() || callTimeout.isNegative() ? builder : builder.timeout(callTimeout);
    }

    private ConnectResult connectResult(final HttpResponse<byte[]> response, final KeyPair keys) {
        final Map<String, Object> result;
        try {
            result = readJson(response);
        } catch (final RuntimeException exception) {
            return ConnectResult.refused(http(response));
        }
        if (!isSuccessful(response) || !(result.get("api_key") instanceof String accessToken)) {
            return ConnectResult.refused(String.valueOf(result.get("detail")));
        }
        return new ConnectResult(new Credential(accessToken, keys), String.valueOf(result.get("server")),
                readModelStates(result), "");
    }

    private String http(final HttpResponse<?> response) {
        return reasons.http().replace("{status}", Integer.toString(response.statusCode()));
    }

    private static Set<ModelId> readEnabledModels(final Map<String, Object> json) {
        final EnumSet<ModelId> models = EnumSet.noneOf(ModelId.class);
        readModelStates(json).forEach((model, enabled) -> {
            if (enabled) {
                models.add(model);
            }
        });
        return Collections.unmodifiableSet(models);
    }

    private static Map<ModelId, Boolean> readModelStates(final Map<String, Object> json) {
        final Map<ModelId, Boolean> states = new EnumMap<>(ModelId.class);
        if (json.get("models") instanceof List<?> items) {
            for (final Object item : items) {
                if (item instanceof Map<?, ?> entry) {
                    final ModelId model = ModelId.fromIdentity(String.valueOf(entry.get("id")));
                    if (model != null) {
                        states.put(model, Boolean.TRUE.equals(entry.get("enabled")));
                    }
                }
            }
        }
        return states;
    }

    private static Map<String, Object> readJson(final HttpResponse<byte[]> response) {
        return InferenceJsonCodec.readObject(new String(response.body(), StandardCharsets.UTF_8));
    }

    private static boolean isSuccessful(final HttpResponse<?> response) {
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    private static URI apiUrl(final String path) {
        return URI.create(BASE_URL + path);
    }

    private static Map<ModelId, URI> modelUrls() {
        final Map<ModelId, URI> urls = new EnumMap<>(ModelId.class);
        for (final ModelId model : ModelId.values()) {
            urls.put(model, apiUrl(MODEL_PATH
                    + "?model-family=" + URLEncoder.encode(model.family(), StandardCharsets.UTF_8)
                    + "&model-name=" + URLEncoder.encode(model.modelName(), StandardCharsets.UTF_8)));
        }
        return urls;
    }

    private static Method findShutdownMethod() {
        try {
            return HttpClient.class.getMethod("shutdownNow");
        } catch (final NoSuchMethodException ignored) {
            return null;
        }
    }

}
