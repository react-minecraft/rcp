package com.g4vrk.react.inference.service;

import com.g4vrk.react.api.rcp.environment.RCPEnvironment;
import com.g4vrk.react.inference.config.ActionsConfig;
import com.g4vrk.react.inference.config.InferenceConfigs;
import com.g4vrk.react.inference.link.Credential;
import com.g4vrk.react.inference.link.LinkStore;
import com.g4vrk.react.inference.link.ServerFingerprint;
import com.g4vrk.react.inference.server.InferenceServer;
import com.g4vrk.schedula.task.TickSchedule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * The link to the site: the stored credential, the {@link InferenceServer} built from it and the RCP status.
 * <p>
 * A reload builds a new server with the new timeouts and texts and hands it to whoever listens.
 */
public final class ConnectionService {

    private static final String LEGACY_TOKEN = "[A-Za-z0-9_.:-]+";

    private final RCPEnvironment environment;
    private final Logger logger;
    private final String fingerprint;
    private final LinkStore linkStore;

    private Consumer<InferenceServer> onReplaced = server -> { };
    private volatile ActionsConfig.Texts texts;
    private volatile InferenceServer server;

    public ConnectionService(final @NotNull RCPEnvironment environment, final @NotNull InferenceConfigs configs)
            throws IOException {
        this.environment = environment;
        this.logger = environment.logger();
        this.fingerprint = ServerFingerprint.of(environment.plugin().getServer());
        this.linkStore = new LinkStore(environment.directory().resolve(".link"), fingerprint);
        migrateLegacyToken();
        this.texts = configs.actions().texts();
        this.server = newServer(configs);
    }

    public @NotNull InferenceServer server() {
        return server;
    }

    public void onReplaced(final @NotNull Consumer<InferenceServer> listener) {
        this.onReplaced = listener;
    }

    public void link(final @NotNull Credential credential) throws IOException {
        linkStore.save(credential);
        server.use(credential);
        refreshStatus();
    }

    public void reload(final @NotNull InferenceConfigs configs) throws IOException {
        texts = configs.actions().texts();
        final InferenceServer previous = server;
        server = newServer(configs);
        onReplaced.accept(server);
        previous.shutdown();
        refreshStatus();
    }

    public void shutdown() {
        final InferenceServer current = server;
        if (current != null) {
            current.shutdown();
        }
    }

    public void refreshStatus() {
        final InferenceServer current = server;
        if (!current.isLinked()) {
            status(texts.status(false, current.enabledModels()));
            return;
        }
        current.checkConnection().whenComplete((result, failure) -> {
            if (failure != null) {
                status(String.valueOf(failure.getMessage()));
            } else if (result.problem() != null) {
                status(result.problem());
            } else {
                status(texts.status(true, current.enabledModels()));
            }
        });
    }

    private void status(final String status) {
        environment.scheduler().schedule(() -> environment.status(status), TickSchedule.instant());
    }

    private InferenceServer newServer(final InferenceConfigs configs) throws IOException {
        return new InferenceServer(
                logger,
                configs.connection().timeouts().connect(),
                configs.connection().timeouts().call(),
                linkStore.load(),
                fingerprint,
                environment.metadata().version(),
                environment.plugin().getDescription().getVersion(),
                configs.actions().texts().reasons()
        );
    }

    /**
     * Moves the token of a plugin from before the RCP, kept in {@code plugins/React/inference.yml}, into {@code .link}.
     * It has no signing key, so the site keeps such a server bound to its IP until it is linked again.
     */
    private void migrateLegacyToken() {
        final Path legacy = environment.plugin().getDataFolder().toPath().resolve("inference.yml");
        if (!Files.isRegularFile(legacy)) {
            return;
        }
        try {
            final String token = YamlConfigurationLoader.builder().path(legacy).build().load()
                    .node("auth", "value").getString("");
            if (token.matches(LEGACY_TOKEN) && linkStore.load() == null) {
                linkStore.save(Credential.unsigned(token));
                logger.info("Moved the access token from inference.yml into the React RCP");
            }
            Files.delete(legacy);
        } catch (final ConfigurateException exception) {
            logger.warn("Could not read the old inference.yml", exception);
        } catch (final IOException exception) {
            logger.warn("Could not move the old inference.yml", exception);
        }
    }

}
