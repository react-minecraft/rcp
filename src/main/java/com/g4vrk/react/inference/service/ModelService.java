package com.g4vrk.react.inference.service;

import com.g4vrk.react.api.rcp.environment.RCPEnvironment;
import com.g4vrk.react.api.rcp.event.RCPPlayerListener;
import com.g4vrk.react.api.rcp.player.RCPPlayer;
import com.g4vrk.react.api.rcp.player.Rotation;
import com.g4vrk.react.inference.config.InferenceConfigs;
import com.g4vrk.react.inference.model.AimModel;
import com.g4vrk.react.inference.model.AimModels;
import com.g4vrk.react.inference.model.ModelId;
import com.g4vrk.react.inference.model.RequestDispatcher;
import com.g4vrk.react.inference.model.flower.FlowerModel;
import com.g4vrk.react.inference.model.vector.VectorModel;
import com.g4vrk.react.inference.server.InferenceServer;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * The Flower and Vector models, their packet listeners and the dispatcher that sends their requests.
 * <p>
 * Packet listeners cannot be removed while the RCP runs, so the models live as long as the RCP;
 * a reload only reconfigures them.
 */
public final class ModelService implements RCPPlayerListener {

    private final RequestDispatcher dispatcher;
    private final FlowerModel flowerBaseline;
    private final FlowerModel flowerUltra;
    private final VectorModel vector;
    private final AimModels models;

    public ModelService(
            final @NotNull RCPEnvironment environment,
            final @NotNull InferenceServer server,
            final @NotNull InferenceConfigs configs
    ) {
        this.dispatcher = new RequestDispatcher(environment, server);
        this.flowerBaseline = new FlowerModel(ModelId.FLOWER_BASELINE, dispatcher, configs.flower().step());
        this.flowerUltra = new FlowerModel(ModelId.FLOWER_ULTRA, dispatcher, configs.flower().step());
        this.vector = new VectorModel(environment, dispatcher, configs.vector().combatTicks());
        this.models = new AimModels(List.of(flowerBaseline, flowerUltra, vector));

        models.packetListeners().forEach(environment::registerPacketListener);
        environment.listen(this);
    }

    public @NotNull AimModels models() {
        return models;
    }

    public void use(final @NotNull InferenceServer server) {
        dispatcher.use(server);
    }

    public void reload(final @NotNull InferenceConfigs configs) {
        flowerBaseline.step(configs.flower().step());
        flowerUltra.step(configs.flower().step());
        vector.combatTicks(configs.vector().combatTicks());
    }

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
