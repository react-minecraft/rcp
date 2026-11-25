package com.g4vrk.react.inference.model;

import com.g4vrk.react.api.rcp.player.RCPPlayer;
import com.g4vrk.react.api.rcp.player.RotationSample;
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
     * Called on the netty thread after every rotation the plugin recorded for the player.
     *
     * <p>{@code sample} holds one rotation laid out as {@link RotationSample} documents; the plugin
     * reuses it, so copy it to keep the values.</p>
     */
    default void onRotation(
            final @NotNull RCPPlayer player,
            final float @NotNull [] sample
    ) {

    }

    default void onAttack(
            final @NotNull RCPPlayer player,
            final int targetEntityId
    ) {

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

    /**
     * Releases any player state or subscriptions retained by this model on shutdown.
     */
    default void shutdown() {

    }

}
