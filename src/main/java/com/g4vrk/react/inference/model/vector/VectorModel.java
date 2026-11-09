package com.g4vrk.react.inference.model.vector;

import com.g4vrk.react.api.rcp.environment.RCPEnvironment;
import com.g4vrk.react.api.rcp.player.RCPPlayer;
import com.g4vrk.react.inference.http.model.HttpRequest;
import com.g4vrk.react.inference.model.AimModel;
import com.g4vrk.react.inference.model.ModelAnswer;
import com.g4vrk.react.inference.model.ModelId;
import com.g4vrk.react.inference.model.RequestDispatcher;
import com.g4vrk.react.inference.model.vector.listener.VectorListener;
import com.g4vrk.react.inference.model.vector.model.AimFrame;
import com.g4vrk.react.inference.model.vector.protocol.FrameCodec;
import com.g4vrk.react.inference.model.vector.service.AimStreamService;
import com.g4vrk.react.inference.model.vector.session.AimSession;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * Vector Preview: aim frames collected from packets during combat, sent as FlatBuffers
 * ({@code protocol/vector}).
 */
public final class VectorModel implements AimModel {

    private static final ModelId ID = ModelId.VECTOR_PREVIEW;

    private final RCPEnvironment environment;
    private final RequestDispatcher dispatcher;
    private final AimStreamService stream;

    /**
     * @param combatTicks how long a player counts as fighting after an attack, from {@code models/vector.yml}
     */
    public VectorModel(
            final @NotNull RCPEnvironment environment,
            final @NotNull RequestDispatcher dispatcher,
            final long combatTicks
    ) {
        this.environment = environment;
        this.dispatcher = dispatcher;
        this.stream = new AimStreamService(environment, combatTicks, this::onFrame);
    }

    public void combatTicks(final long combatTicks) {
        stream.combatTicks(combatTicks);
    }

    @Override
    public @NotNull ModelId id() {
        return ID;
    }

    /**
     * The frame stream, for addons that read Vector Preview frames.
     */
    public @NotNull AimStreamService stream() {
        return stream;
    }

    @Override
    public @NotNull List<PacketListenerCommon> packetListeners() {
        return List.of(new VectorListener(environment, stream));
    }

    private void onFrame(final @NotNull RCPPlayer player, final @NotNull AimSession session) {
        if (!session.combatActivity.isActive()) {
            return;
        }
        session.resize(ID.window());
        if (!dispatcher.tryStart(player, ID)) {
            return;
        }
        final AimFrame[] snapshot = session.pollSnapshot(ID.step());
        if (snapshot == null) {
            dispatcher.cancel(player, ID);
            return;
        }
        final String name = player.name();
        dispatcher.send(
                player,
                this,
                token -> new HttpRequest(
                        FrameCodec.encodeBatch(snapshot, name, token),
                        Map.of("X-Batch", "1")
                )
        );
    }

    @Override
    public @NotNull ModelAnswer readResponse(final byte @NotNull [] body) {
        final FrameCodec.Prediction prediction = FrameCodec.decodePrediction(body);
        return new ModelAnswer(prediction.probability(), prediction.threshold(), prediction.enforcementEnabled());
    }

    @Override
    public void clearHistory(final @NotNull UUID player) {
        stream.claimPlayerData(player).clearHistory();
    }

    @Override
    public void resetPlayer(final @NotNull UUID player) {
        stream.claimPlayerData(player).reset();
    }

    @Override
    public void removePlayer(final @NotNull UUID player) {
        stream.removePlayerData(player);
    }

}
