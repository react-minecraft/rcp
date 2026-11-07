package com.g4vrk.react.inference;

import com.g4vrk.functionalActions.defaults.DefaultActions;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.functionalActions.parser.impl.SimpleActionParser;
import com.g4vrk.functionalActions.registry.ActionRegistry;
import com.g4vrk.functionalActions.registry.impl.SimpleActionRegistry;
import com.g4vrk.react.api.rcp.environment.RCPEnvironment;
import com.g4vrk.react.api.rcp.provider.RCPProvider;
import com.g4vrk.react.inference.command.LinkArgument;
import com.g4vrk.react.inference.config.InferenceConfigs;
import com.g4vrk.react.inference.model.AimModels;
import com.g4vrk.react.inference.service.ConnectionService;
import com.g4vrk.react.inference.service.ModelService;
import com.g4vrk.text.LegacySerializers;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The React RCP: the Flower and Vector models and the link to www.react-ac.space.
 */
public final class ReactInferenceRCP extends RCPProvider {

    private static volatile @Nullable ReactInferenceRCP instance;

    private final RCPEnvironment environment;
    private final ActionParser<Audience> actionParser;

    private ConnectionService connection;
    private ModelService models;
    private LinkArgument linkArgument;

    public ReactInferenceRCP(final @NotNull RCPEnvironment environment) {
        super(environment);
        this.environment = environment;
        final ActionRegistry<Audience> registry = new SimpleActionRegistry<>(true);
        DefaultActions.Adventure.registerDefaults(
                registry, LegacySerializers.INSTANCE.universalSerializer()::serialize, ";");
        this.actionParser = new SimpleActionParser<>(registry);
    }

    /**
     * The running RCP, for addons that read the models.
     */
    public static @Nullable ReactInferenceRCP instance() {
        return instance;
    }

    public @NotNull AimModels models() {
        return models.models();
    }

    @Override
    public void onEnable() throws Exception {
        final InferenceConfigs configs = InferenceConfigs.load(environment.directory(), environment.logger());
        connection = new ConnectionService(environment, configs);
        models = new ModelService(environment, connection.server(), configs);
        connection.onReplaced(models::use);

        linkArgument = new LinkArgument(connection::server, connection::link, environment.scheduler(),
                environment.logger());
        linkArgument.reload(actionParser, configs.actions());
        environment.registerArgument("link", linkArgument);

        instance = this;
        connection.refreshStatus();
    }

    @Override
    public void onReload() throws Exception {
        final InferenceConfigs configs = InferenceConfigs.load(environment.directory(), environment.logger());
        models.reload(configs);
        linkArgument.reload(actionParser, configs.actions());
        connection.reload(configs);
    }

    @Override
    public void onDisable() {
        instance = null;
        if (connection != null) {
            connection.shutdown();
        }
    }

}
