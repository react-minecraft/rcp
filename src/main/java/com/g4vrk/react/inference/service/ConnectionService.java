package com.g4vrk.react.inference.service;

import com.g4vrk.react.api.rcp.environment.RCPEnvironment;
import com.g4vrk.react.inference.config.ActionsConfig;
import com.g4vrk.react.inference.config.InferenceConfigRegistry;
import com.g4vrk.react.inference.link.Credential;
import com.g4vrk.react.inference.link.LinkStore;
import com.g4vrk.react.inference.server.AccountClient;
import com.g4vrk.react.inference.server.InferenceServer;
import com.g4vrk.schedula.task.TickSchedule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * The link to the site: the stored credential, the {@link InferenceServer} built from it and the RCP status.
 *
 * <p>A reload builds a new server with the new timeouts and texts and hands it to whoever listens.</p>
 */
public final class ConnectionService {

    private static final String LEGACY_TOKEN = "[A-Za-z0-9_.:-]+";

    private final RCPEnvironment environment;
    private final Logger logger;
    private final String fingerprint;
    private final LinkStore linkStore;
    private final @Nullable Credential localCredential;

    private Consumer<InferenceServer> onReplaced = server -> { };
    private volatile ActionsConfig.TextSet texts;
    private volatile InferenceServer server;
    private volatile boolean closed;
    private long statusRevision;

    public ConnectionService(
            final @NotNull RCPEnvironment environment,
            final @NotNull InferenceConfigRegistry configs
    ) throws IOException {

        this.environment = environment;
        this.logger = environment.logger();
        this.fingerprint = environment.platform().serverFingerprint();
        this.linkStore = new LinkStore(environment.directory().resolve(".link"), fingerprint);
        this.localCredential = InferenceServer.localCredential();
        if (localCredential == null) {
            migrateLegacyToken();
        }
        this.texts = configs.actions().texts();
        this.server = newServer(configs);

    }

    public @NotNull InferenceServer server() {

        return server;

    }

    public void onReplaced(final @NotNull Consumer<InferenceServer> listener) {

        this.onReplaced = listener;

    }

    public synchronized void link(
            final @NotNull InferenceServer source,
            final @NotNull AccountClient.ConnectResult result
    ) throws IOException {

        if (closed || source != server) {
            throw new IllegalStateException("The link attempt was replaced by a reload or shutdown");
        }
        if (localCredential != null) {
            throw new IllegalStateException("Local backend credentials are set by react.api.token");
        }
        synchronized (source) {
            if (!source.account().isCurrentLogin(result.attempt()) || result.credential() == null) {
                throw new IllegalStateException("The link attempt was replaced");
            }
            linkStore.save(result.credential());
            source.use(result.credential());
        }
        refreshStatus();

    }

    public synchronized void reload(final @NotNull InferenceConfigRegistry configs) throws IOException {

        if (closed) {
            return;
        }
        texts = configs.actions().texts();
        final InferenceServer previous = server;
        server = newServer(configs);
        onReplaced.accept(server);
        previous.shutdown();
        refreshStatus();

    }

    public synchronized void shutdown() {

        closed = true;
        statusRevision++;
        final InferenceServer current = server;
        if (current != null) {
            current.shutdown();
        }

    }

    public synchronized void refreshStatus() {

        if (closed) {
            return;
        }
        final long revision = ++statusRevision;
        final InferenceServer current = server;
        final ActionsConfig.TextSet currentTexts = texts;
        if (!current.isLinked()) {
            status(revision, currentTexts.status(false, current.enabledModels()));
            return;
        }
        current.account().checkConnection().whenComplete((result, failure) -> {
            if (failure != null) {
                status(revision, String.valueOf(failure.getMessage()));
            } else if (result.problem() != null) {
                status(revision, result.problem());
            } else {
                status(revision, currentTexts.status(true, current.enabledModels()));
            }
        });

    }

    private void status(
            final long revision,
            final @NotNull String status
    ) {

        synchronized (this) {
            if (closed || statusRevision != revision) {
                return;
            }
        }
        environment.scheduler().schedule(() -> {
            synchronized (this) {
                if (!closed && statusRevision == revision) {
                    environment.status(status);
                }
            }
        }, TickSchedule.instant());

    }

    private @NotNull InferenceServer newServer(final @NotNull InferenceConfigRegistry configs) throws IOException {

        return new InferenceServer(
                logger,
                configs.connection().timeouts().connect(),
                configs.connection().timeouts().call(),
                localCredential == null ? linkStore.load() : localCredential,
                fingerprint,
                environment.metadata().version(),
                environment.platform().pluginVersion(),
                configs.actions().texts().reasons()
        );

    }

    /**
     * Copies the token of a plugin from before the RCP, kept in {@code plugins/React/inference.yml}, into {@code .link}.
     * It has no signing key, so the site keeps such a server bound to its IP until it is linked again.
     */
    private void migrateLegacyToken() {

        final Path legacy = environment.platform().dataDirectory().resolve("inference.yml");
        if (!Files.isRegularFile(legacy) || Files.exists(environment.directory().resolve(".link"))) {
            return;
        }
        try {
            final String token = YamlConfigurationLoader.builder().path(legacy).build().load()
                    .node("auth", "value").getString("");
            if (token.matches(LEGACY_TOKEN)) {
                linkStore.save(Credential.unsigned(token));
                logger.info("Copied the access token from inference.yml into the React RCP");
            }
        } catch (final ConfigurateException exception) {
            logger.warn("Could not read the old inference.yml", exception);
        } catch (final IOException exception) {
            logger.warn("Could not move the old inference.yml", exception);
        }

    }

}
