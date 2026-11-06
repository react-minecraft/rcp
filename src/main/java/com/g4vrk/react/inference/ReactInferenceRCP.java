package com.g4vrk.react.inference;

import com.g4vrk.functionalActions.defaults.DefaultActions;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.functionalActions.parser.impl.SimpleActionParser;
import com.g4vrk.functionalActions.registry.ActionRegistry;
import com.g4vrk.functionalActions.registry.impl.SimpleActionRegistry;
import com.g4vrk.react.api.rcp.environment.RCPEnvironment;
import com.g4vrk.react.api.rcp.event.RCPPlayerListener;
import com.g4vrk.react.api.rcp.player.RCPPlayer;
import com.g4vrk.react.api.rcp.player.Rotation;
import com.g4vrk.react.api.rcp.provider.RCPProvider;
import com.g4vrk.react.inference.command.LinkCommand;
import com.g4vrk.react.inference.config.InferenceConfigs;
import com.g4vrk.react.inference.link.LinkStore;
import com.g4vrk.react.inference.link.ServerFingerprint;
import com.g4vrk.react.inference.model.AimModel;
import com.g4vrk.react.inference.model.AimModels;
import com.g4vrk.react.inference.model.ModelId;
import com.g4vrk.react.inference.model.RequestDispatcher;
import com.g4vrk.react.inference.model.flower.FlowerModel;
import com.g4vrk.react.inference.model.vector.VectorModel;
import com.g4vrk.react.inference.server.InferenceServer;
import com.g4vrk.schedula.task.TickSchedule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * The React RCP: the Flower and Vector models and the link to www.react-ac.space.
 * <p>
 * Packet listeners cannot be removed while the RCP runs, so the models live as long as the RCP and a reload
 * only reconfigures them and rebuilds the connection.
 */
public final class ReactInferenceRCP extends RCPProvider {

    private static final String LEGACY_KEY = "[A-Za-z0-9_.:-]+";

    private static volatile @Nullable ReactInferenceRCP instance;

    private final RCPEnvironment environment;
    private final Logger logger;
    private final ActionParser<Audience> actionParser;

    private String fingerprint;
    private LinkStore linkStore;
    private LinkCommand linkCommand;
    private RequestDispatcher dispatcher;
    private AimModels models;
    private FlowerModel flowerBaseline;
    private FlowerModel flowerUltra;
    private VectorModel vector;
    private volatile InferenceServer server;

    public ReactInferenceRCP(final @NotNull RCPEnvironment environment) {
        super(environment);
        this.environment = environment;
        this.logger = environment.logger();
        final ActionRegistry<Audience> registry = new SimpleActionRegistry<>(true);
        DefaultActions.Adventure.registerDefaults(registry, MiniMessage.miniMessage()::deserialize, ";");
        this.actionParser = new SimpleActionParser<>(registry);
    }

    /**
     * The running RCP, for addons that read the models.
     */
    public static @Nullable ReactInferenceRCP instance() {
        return instance;
    }

    public @NotNull AimModels models() {
        return models;
    }

    @Override
    public void onEnable() throws Exception {
        fingerprint = ServerFingerprint.of(environment.plugin().getServer());
        linkStore = new LinkStore(environment.directory().resolve(".link"), fingerprint);
        migrateLegacyKey();

        final InferenceConfigs configs = InferenceConfigs.load(environment.directory(), logger);
        server = newServer(configs);
        dispatcher = new RequestDispatcher(environment, server);
        flowerBaseline = new FlowerModel(ModelId.FLOWER_BASELINE, dispatcher, configs.flower().step());
        flowerUltra = new FlowerModel(ModelId.FLOWER_ULTRA, dispatcher, configs.flower().step());
        vector = new VectorModel(environment, dispatcher, configs.vector().combatTicks());
        models = new AimModels(List.of(flowerBaseline, flowerUltra, vector));

        models.packetListeners().forEach(environment::registerPacketListener);
        environment.listen(new PlayerListener());

        linkCommand = new LinkCommand(() -> server, this::link, environment.scheduler(), logger);
        linkCommand.reload(actionParser, configs.actions().link());
        environment.registerCommand(linkCommand);

        instance = this;
        refreshStatus();
    }

    @Override
    public void onReload() throws Exception {
        final InferenceConfigs configs = InferenceConfigs.load(environment.directory(), logger);
        flowerBaseline.step(configs.flower().step());
        flowerUltra.step(configs.flower().step());
        vector.combatTicks(configs.vector().combatTicks());
        linkCommand.reload(actionParser, configs.actions().link());
        replaceServer(newServer(configs));
    }

    @Override
    public void onDisable() {
        instance = null;
        final InferenceServer current = server;
        if (current != null) {
            current.shutdown();
        }
    }

    private void link(final @NotNull String apiKey) throws IOException {
        linkStore.save(apiKey);
        server.useKey(apiKey);
        refreshStatus();
    }

    private InferenceServer newServer(final InferenceConfigs configs) throws IOException {
        return new InferenceServer(
                logger,
                configs.connection().timeouts().connect(),
                configs.connection().timeouts().call(),
                linkStore.load(),
                fingerprint,
                environment.metadata().version(),
                environment.plugin().getDescription().getVersion()
        );
    }

    private void replaceServer(final InferenceServer replacement) {
        final InferenceServer previous = server;
        server = replacement;
        dispatcher.use(replacement);
        previous.shutdown();
        refreshStatus();
    }

    private void refreshStatus() {
        final InferenceServer current = server;
        if (!current.isLinked()) {
            setStatus("Not linked: /react link");
            return;
        }
        current.checkConnection().whenComplete((result, failure) -> {
            if (failure != null || result.problem() != null) {
                setStatus(failure != null ? failure.getMessage() : result.problem());
            } else {
                setStatus(current.enabledModelsDisplay());
            }
        });
    }

    private void setStatus(final String status) {
        environment.scheduler().schedule(() -> environment.status(status), TickSchedule.instant());
    }

    /**
     * Moves the key of a plugin from before the RCP, kept in {@code plugins/React/inference.yml}, into {@code .link}.
     */
    private void migrateLegacyKey() {
        final Path legacy = environment.plugin().getDataFolder().toPath().resolve("inference.yml");
        if (!Files.isRegularFile(legacy)) {
            return;
        }
        try {
            final String key = YamlConfigurationLoader.builder().path(legacy).build().load()
                    .node("auth", "value").getString("");
            if (key.matches(LEGACY_KEY) && linkStore.load() == null) {
                linkStore.save(key);
                logger.info("Moved the server key from inference.yml into the React RCP");
            }
            Files.delete(legacy);
        } catch (final ConfigurateException exception) {
            logger.warn("Could not read the old inference.yml", exception);
        } catch (final IOException exception) {
            logger.warn("Could not move the old inference.yml", exception);
        }
    }

    private final class PlayerListener implements RCPPlayerListener {

        @Override
        public void onRotation(final @NotNull RCPPlayer player, final @NotNull Rotation rotation) {
            for (final AimModel model : models.all()) {
                model.onRotation(player, rotation);
            }
        }

        @Override
        public void onReload(final @NotNull RCPPlayer player) {
            onReset(player.uniqueId());
        }

        @Override
        public void onReset(final @NotNull UUID player) {
            dispatcher.reset(player);
            models.resetPlayer(player);
        }

        @Override
        public void onQuit(final @NotNull UUID player) {
            dispatcher.remove(player);
            models.removePlayer(player);
        }

    }

}
