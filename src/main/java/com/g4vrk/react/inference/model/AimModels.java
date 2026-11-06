package com.g4vrk.react.inference.model;

import com.github.retrooper.packetevents.event.PacketListenerCommon;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * The models this RCP build runs. Adding a model means adding it here, nothing else.
 */
public final class AimModels {

    private final List<AimModel> models;

    public AimModels(final @NotNull List<AimModel> models) {
        this.models = List.copyOf(models);
    }

    public @NotNull List<AimModel> all() {
        return models;
    }

    /**
     * The model of the given type, for addons that work with one model's own data.
     */
    public <T extends AimModel> @NotNull Optional<T> find(final @NotNull Class<T> type) {
        return models.stream().filter(type::isInstance).map(type::cast).findFirst();
    }

    public @NotNull List<PacketListenerCommon> packetListeners() {
        return models.stream().flatMap(model -> model.packetListeners().stream()).toList();
    }

    public void clearHistory(final @NotNull UUID player) {
        models.forEach(model -> model.clearHistory(player));
    }

    public void resetPlayer(final @NotNull UUID player) {
        models.forEach(model -> model.resetPlayer(player));
    }

    public void removePlayer(final @NotNull UUID player) {
        models.forEach(model -> model.removePlayer(player));
    }

}
