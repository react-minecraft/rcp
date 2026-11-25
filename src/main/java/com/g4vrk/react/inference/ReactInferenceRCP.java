package com.g4vrk.react.inference;

import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.api.rcp.environment.RCPEnvironment;
import com.g4vrk.react.api.rcp.provider.RCPProvider;
import com.g4vrk.react.inference.api.InferenceApi;
import com.g4vrk.react.inference.command.LinkCommand;
import com.g4vrk.react.inference.config.InferenceConfigRegistry;
import com.g4vrk.react.inference.model.AimModelRegistry;
import com.g4vrk.react.inference.service.ConnectionService;
import com.g4vrk.react.inference.service.ModelService;
import java.util.List;
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
    private LinkCommand linkCommand;

    public ReactInferenceRCP(final @NotNull RCPEnvironment environment) {

        super(environment);
        this.environment = environment;
        this.actionParser = environment.actions();

    }

    /**
     * The running RCP, for addons that read the models.
     */
    public static @Nullable ReactInferenceRCP instance() {

        return instance;

    }

    public @NotNull AimModelRegistry models() {

        return models.models();

    }

    @Override
    public void onEnable() throws Exception {

        final InferenceConfigRegistry configs = InferenceConfigRegistry.load(environment.directory(), environment.logger());
        connection = new ConnectionService(environment, configs);
        models = new ModelService(environment, connection.server(), configs);
        connection.onReplaced(models::use);

        linkCommand = new LinkCommand(
                connection::server,
                connection::link,
                environment.scheduler(),
                environment.logger()
        );
        linkCommand.reload(actionParser, configs.actions());
        linkCommand.register(environment.commands());

        instance = this;
        connection.refreshStatus();
        final List<String> modelIds = models.models().all().stream().map(model -> model.id().identity()).toList();
        environment.services().register(InferenceApi.class, () -> modelIds);

    }

    @Override
    public void onReload() throws Exception {

        final InferenceConfigRegistry configs = InferenceConfigRegistry.load(environment.directory(), environment.logger());
        models.reload(configs);
        linkCommand.reload(actionParser, configs.actions());
        connection.reload(configs);

    }

    @Override
    public void onDisable() {

        instance = null;
        if (models != null) {
            models.shutdown();
        }
        if (connection != null) {
            connection.shutdown();
        }

    }

}
