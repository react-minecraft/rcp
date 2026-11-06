package com.g4vrk.react.inference.model;

import com.g4vrk.react.api.rcp.player.RCPPlayer;
import com.g4vrk.react.api.rcp.player.Rotation;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * One server model the RCP can ask.
 *
 * <p>What a model collects, how it encodes a window and reads an answer stays inside
 * the model's own package. Requests go through the {@link RequestDispatcher}.</p>
 */
public interface AimModel {

    @NotNull ModelId id();

    /**
     * Packet listeners the model needs to collect its input.
     */
    default @NotNull List<PacketListenerCommon> packetListeners() {
        return List.of();
    }

    /**
     * Called on the netty thread after every rotation the plugin recorded for the player.
     */
    default void onRotation(final @NotNull RCPPlayer player, final @NotNull Rotation rotation) {
    }

    /**
     * Reads the model's answer.
     *
     * @throws IllegalArgumentException when the answer is malformed
     */
    @NotNull ModelAnswer readResponse(byte @NotNull [] body);

    /**
     * The server or key changed: windows collected so far no longer apply.
     */
    default void clearHistory(final @NotNull UUID player) {
    }

    /**
     * The player's transient state is reset.
     */
    default void resetPlayer(final @NotNull UUID player) {
    }

    /**
     * The player left; everything kept for them is dropped.
     */
    default void removePlayer(final @NotNull UUID player) {
        resetPlayer(player);
    }

}
