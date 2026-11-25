package com.g4vrk.react.inference.server;

import com.g4vrk.react.inference.config.ActionsConfig;
import com.g4vrk.react.inference.json.InferenceJsonCodec;
import com.g4vrk.react.inference.link.Credential;
import com.g4vrk.react.inference.link.RequestSigner;
import com.g4vrk.react.inference.model.ModelId;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Handles account, model-status, and device-link HTTP endpoints on the React site.
 */
public final class AccountClient {

    private static final String CHECK_PATH = "/api/v1/auth/check";
    private static final String CONNECT_PATH = "/api/v1/auth/connect";
    private static final String DEVICE_PATH = "/api/v1/auth/device";
    private static final String DEVICE_TOKEN_PATH = "/api/v1/auth/device/token";
    private static final long DEFAULT_POLL_INTERVAL_SECONDS = 3L;
    private static final long MIN_POLL_INTERVAL_SECONDS = 1L;
    private static final long MAX_POLL_INTERVAL_SECONDS = 30L;
    private static final long MIN_DEVICE_EXPIRES_SECONDS = 1L;
    private static final long MAX_DEVICE_EXPIRES_SECONDS = 3600L;
    private static final int MAX_POLL_TRANSIENT_FAILURES = 3;

    private final InferenceServer server;
    private final Logger logger;
    private final String fingerprint;
    private final ActionsConfig.ReasonSet reasons;
    private final AtomicLong deviceLogin = new AtomicLong();

    AccountClient(
            final @NotNull InferenceServer server,
            final @NotNull Logger logger,
            final @NotNull String fingerprint,
            final @NotNull ActionsConfig.ReasonSet reasons
    ) {

        this.server = server;
        this.logger = logger;
        this.fingerprint = fingerprint;
        this.reasons = reasons;

    }

    /**
     * Asks the site about this server's link right now and updates the enabled models.
     */
    public @NotNull CompletableFuture<ConnectionState> checkConnection() {

        if (!server.isLinked()) {
            return CompletableFuture.completedFuture(ConnectionState.failed(reasons.notLinked()));
        }
        final long epoch = server.requestEpoch();
        return get(CHECK_PATH).handle((response, failure) -> {
            if (failure != null) {
                return ConnectionState.failed(String.valueOf(failure.getMessage()));
            }
            try {
                final Map<String, Object> json = readJson(response);
                if (!isSuccessful(response)) {
                    return ConnectionState.failed(detail(json, response));
                }
                final Set<ModelId> models = readEnabledModels(json);
                server.markModelRefreshSuccess(epoch, models);
                return new ConnectionState(String.valueOf(json.get("server")), readModelStates(json), null);
            } catch (final RuntimeException exception) {
                return ConnectionState.failed(isSuccessful(response)
                        ? String.valueOf(exception.getMessage())
                        : http(response));
            }
        });

    }

    /**
     * Exchanges a code from the server's page on the site for this server's credential.
     */
    public @NotNull CompletableFuture<ConnectResult> connect(final @NotNull String code) {

        final long attempt = beginLogin();
        final KeyPair keys = RequestSigner.newKeyPair();
        return post(
                CONNECT_PATH,
                Map.of(
                        "code",
                        code,
                        "fingerprint",
                        fingerprint,
                        "public_key",
                        RequestSigner.publicKey(keys.getPublic())
                )
        ).thenApply(response -> isCurrentAttempt(attempt)
                ? connectResult(response, keys, attempt)
                : ConnectResult.refused(reasons.replaced()));

    }

    /**
     * Starts a link login and completes once the owner answers on the site.
     *
     * <p>A newer login replaces the one still waiting.</p>
     *
     * @param onLink receives the link to show the owner
     */
    public @NotNull CompletableFuture<ConnectResult> connectWithLink(final @NotNull Consumer<DeviceLink> onLink) {

        final long attempt = beginLogin();
        final KeyPair keys = RequestSigner.newKeyPair();
        return post(DEVICE_PATH, Map.of("fingerprint", fingerprint)).thenCompose(response -> {
            if (!isCurrentAttempt(attempt)) {
                return CompletableFuture.completedFuture(ConnectResult.refused(reasons.replaced()));
            }
            final Map<String, Object> json;
            try {
                json = readJson(response);
            } catch (final RuntimeException exception) {
                return CompletableFuture.completedFuture(ConnectResult.refused(http(response)));
            }
            if (!isSuccessful(response)
                    || !(json.get("device_code") instanceof String deviceCode)
                    || !(json.get("expires_in") instanceof Number expiresIn)
                    || !(json.get("verification_uri_complete") instanceof String verificationUrl)
                    || !(json.get("user_code") instanceof String userCode)) {
                return CompletableFuture.completedFuture(ConnectResult.refused(detail(json, response)));
            }
            final long intervalSeconds = clampInterval(json.get("interval") instanceof Number number
                    ? number.longValue()
                    : DEFAULT_POLL_INTERVAL_SECONDS);
            final long expiresInSeconds = clampExpiresIn(expiresIn.longValue());
            onLink.accept(new DeviceLink(verificationUrl, userCode, expiresInSeconds));
            final Map<String, Object> body = Map.of(
                    "device_code",
                    deviceCode,
                    "fingerprint",
                    fingerprint,
                    "public_key",
                    RequestSigner.publicKey(keys.getPublic())
            );
            return pollDeviceLogin(
                    attempt,
                    body,
                    keys,
                    intervalSeconds,
                    intervalSeconds,
                    System.nanoTime() + TimeUnit.SECONDS.toNanos(expiresInSeconds),
                    0
            );
        });

    }

    public boolean isCurrentLogin(final long attempt) {

        synchronized (server) {
            return !server.isClosed() && attempt == deviceLogin.get();
        }

    }

    void refreshModels(final long epoch) {

        try {
            get(CHECK_PATH).whenComplete((response, failure) -> {
                if (failure != null) {
                    /*
                     * A network failure keeps the last known models instead of stopping every check.
                     */
                    logger.debug("Could not refresh the enabled models", failure);
                    return;
                }
                if (response.statusCode() == 401 || response.statusCode() == 403) {
                    server.clearEnabledModelsIfCurrent(epoch, () ->
                            logger.warn("The site refused this server's link: link it again with /react link"));
                    return;
                }
                if (!isSuccessful(response)) {
                    return;
                }
                try {
                    final Set<ModelId> models = readEnabledModels(readJson(response));
                    server.updateEnabledModelsIfCurrent(epoch, models);
                } catch (final RuntimeException exception) {
                    logger.debug("Could not read the enabled models", exception);
                }
            });
        } catch (final RuntimeException exception) {
            logger.debug("Could not refresh the enabled models", exception);
        }

    }

    void cancelPendingLogins() {

        deviceLogin.incrementAndGet();

    }

    static long clampInterval(final long seconds) {

        return Math.max(MIN_POLL_INTERVAL_SECONDS, Math.min(MAX_POLL_INTERVAL_SECONDS, seconds));

    }

    static long clampExpiresIn(final long seconds) {

        return Math.max(MIN_DEVICE_EXPIRES_SECONDS, Math.min(MAX_DEVICE_EXPIRES_SECONDS, seconds));

    }

    static boolean isRetryablePollStatus(final int status) {

        return status == 429 || (status >= 500 && status < 600);

    }

    static long retryDelaySeconds(
            final int status,
            final long baseIntervalSeconds,
            final int transientFailures
    ) {

        if (!isRetryablePollStatus(status) || transientFailures >= MAX_POLL_TRANSIENT_FAILURES) {
            return -1L;
        }
        return status == 429
                ? Math.min(MAX_POLL_INTERVAL_SECONDS, baseIntervalSeconds * 2L)
                : baseIntervalSeconds;

    }

    private CompletableFuture<ConnectResult> pollDeviceLogin(
            final long attempt,
            final @NotNull Map<String, Object> body,
            final @NotNull KeyPair keys,
            final long baseIntervalSeconds,
            final long delaySeconds,
            final long deadlineNanos,
            final int transientFailures
    ) {

        if (!isCurrentAttempt(attempt)) {
            return CompletableFuture.completedFuture(ConnectResult.refused(reasons.replaced()));
        }
        if (System.nanoTime() - deadlineNanos >= 0L) {
            return CompletableFuture.completedFuture(ConnectResult.refused(reasons.expired()));
        }
        return CompletableFuture.runAsync(
                        () -> { },
                        CompletableFuture.delayedExecutor(delaySeconds, TimeUnit.SECONDS)
                )
                .thenCompose(ignored -> {
                    if (!isCurrentAttempt(attempt)) {
                        return CompletableFuture.completedFuture(null);
                    }
                    if (System.nanoTime() - deadlineNanos >= 0L) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return post(DEVICE_TOKEN_PATH, body);
                })
                .thenCompose(response -> handlePollResponse(
                        attempt,
                        body,
                        keys,
                        baseIntervalSeconds,
                        deadlineNanos,
                        transientFailures,
                        response
                ));

    }

    private CompletableFuture<ConnectResult> handlePollResponse(
            final long attempt,
            final @NotNull Map<String, Object> body,
            final @NotNull KeyPair keys,
            final long baseIntervalSeconds,
            final long deadlineNanos,
            final int transientFailures,
            final @Nullable HttpResponse<byte[]> response
    ) {

        if (!isCurrentAttempt(attempt)) {
            return CompletableFuture.completedFuture(ConnectResult.refused(reasons.replaced()));
        }
        if (response == null) {
            return CompletableFuture.completedFuture(ConnectResult.refused(reasons.expired()));
        }
        final int status = response.statusCode();
        if (status == 202) {
            return pollDeviceLogin(
                    attempt,
                    body,
                    keys,
                    baseIntervalSeconds,
                    baseIntervalSeconds,
                    deadlineNanos,
                    0
            );
        }
        final long nextDelay = retryDelaySeconds(status, baseIntervalSeconds, transientFailures);
        if (nextDelay > 0L) {
            logger.debug(
                    "Retrying device poll after transient HTTP {} ({}/{})",
                    status,
                    transientFailures + 1,
                    MAX_POLL_TRANSIENT_FAILURES
            );
            return pollDeviceLogin(
                    attempt,
                    body,
                    keys,
                    baseIntervalSeconds,
                    nextDelay,
                    deadlineNanos,
                    transientFailures + 1
            );
        }
        return CompletableFuture.completedFuture(connectResult(response, keys, attempt));

    }

    private boolean isCurrentAttempt(final long attempt) {

        return !server.isClosed() && attempt == deviceLogin.get();

    }

    private long beginLogin() {

        synchronized (server) {
            return deviceLogin.incrementAndGet();
        }

    }

    private CompletableFuture<HttpResponse<byte[]>> get(final @NotNull String path) {

        return server.sendAccountRequest("GET", path, new byte[0]);

    }

    private CompletableFuture<HttpResponse<byte[]>> post(
            final @NotNull String path,
            final @NotNull Map<String, Object> json
    ) {

        return server.sendAccountRequest(
                "POST",
                path,
                InferenceJsonCodec.writeObject(json).getBytes(StandardCharsets.UTF_8)
        );

    }

    private ConnectResult connectResult(
            final @NotNull HttpResponse<byte[]> response,
            final @NotNull KeyPair keys,
            final long attempt
    ) {

        final Map<String, Object> result;
        try {
            result = readJson(response);
        } catch (final RuntimeException exception) {
            return ConnectResult.refused(http(response));
        }
        if (!isSuccessful(response) || !(result.get("api_key") instanceof String accessToken)) {
            return ConnectResult.refused(detail(result, response));
        }
        return new ConnectResult(
                new Credential(accessToken, keys),
                String.valueOf(result.get("server")),
                readModelStates(result),
                "",
                attempt
        );

    }

    private String detail(
            final @NotNull Map<String, Object> json,
            final @NotNull HttpResponse<?> response
    ) {

        final Object value = json.get("detail");
        return value != null ? String.valueOf(value) : http(response);

    }

    private String http(final @NotNull HttpResponse<?> response) {

        return reasons.http().replace("{status}", Integer.toString(response.statusCode()));

    }

    private static Set<ModelId> readEnabledModels(final @NotNull Map<String, Object> json) {

        final EnumSet<ModelId> models = EnumSet.noneOf(ModelId.class);
        readModelStates(json).forEach((model, enabled) -> {
            if (enabled) {
                models.add(model);
            }
        });
        return Collections.unmodifiableSet(models);

    }

    private static Map<ModelId, Boolean> readModelStates(final @NotNull Map<String, Object> json) {

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

    private static Map<String, Object> readJson(final @NotNull HttpResponse<byte[]> response) {

        return InferenceJsonCodec.readObject(new String(response.body(), StandardCharsets.UTF_8));

    }

    private static boolean isSuccessful(final @NotNull HttpResponse<?> response) {

        return response.statusCode() >= 200 && response.statusCode() < 300;

    }

    /**
     * The outcome of {@code /react link check}.
     *
     * @param server  server name reported by the backend
     * @param models  every model the account can use, mapped to whether it is enabled
     * @param problem why the check failed, or null
     */
    public record ConnectionState(
            @NotNull String server,
            @NotNull Map<ModelId, Boolean> models,
            @Nullable String problem
    ) {

        static @NotNull ConnectionState failed(final @NotNull String problem) {

            return new ConnectionState("", Map.of(), problem);

        }

    }

    /**
     * The outcome of a link attempt.
     *
     * @param credential the new credential, or null when the site did not accept the link
     * @param server     server name reported by the backend
     * @param models     every model the account can use, mapped to whether it is enabled
     * @param detail     failure description when refused
     * @param attempt    monotonic attempt identifier
     */
    public record ConnectResult(
            @Nullable Credential credential,
            @NotNull String server,
            @NotNull Map<ModelId, Boolean> models,
            @NotNull String detail,
            long attempt
    ) {

        static @NotNull ConnectResult refused(final @NotNull String detail) {

            return new ConnectResult(null, "", Map.of(), detail, -1L);

        }

    }

    /**
     * The link {@code /react link} prints: the owner opens it and approves the server.
     *
     * @param url              verification URI with pre-filled user code
     * @param code             short user code
     * @param expiresInSeconds link lifetime in seconds
     */
    public record DeviceLink(
            @NotNull String url,
            @NotNull String code,
            long expiresInSeconds
    ) {

    }

}
