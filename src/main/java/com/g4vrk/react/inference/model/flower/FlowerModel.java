package com.g4vrk.react.inference.model.flower;

import com.g4vrk.react.api.rcp.player.RCPPlayer;
import com.g4vrk.react.api.rcp.player.RotationSample;
import com.g4vrk.react.inference.http.model.HttpRequest;
import com.g4vrk.react.inference.model.AimModel;
import com.g4vrk.react.inference.model.ModelAnswer;
import com.g4vrk.react.inference.model.ModelId;
import com.g4vrk.react.inference.model.RequestDispatcher;
import com.g4vrk.react.inference.model.flower.protocol.FlowerCodec;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;

/**
 * Flower models read the last rotations of a player in combat, sent as FlatBuffers
 * ({@code protocol/flower}).
 */
public final class FlowerModel implements AimModel {

    private final ModelId id;
    private final RequestDispatcher dispatcher;
    private volatile int step;
    private final Map<UUID, AtomicInteger> samples = new ConcurrentHashMap<>();

    /**
     * @param step new rotations between two requests, from {@code models/flower.yml}
     */
    public FlowerModel(final @NotNull ModelId id, final @NotNull RequestDispatcher dispatcher, final int step) {
        this.id = id;
        this.dispatcher = dispatcher;
        this.step = Math.max(1, step);
    }

    public void step(final int step) {
        this.step = Math.max(1, step);
    }

    @Override
    public @NotNull ModelId id() {
        return id;
    }

    @Override
    public void onRotation(final @NotNull RCPPlayer player, final float @NotNull [] sample) {
        if (!player.inCombat()) {
            return;
        }
        final AtomicInteger counter = samples.computeIfAbsent(player.uniqueId(), ignored -> new AtomicInteger());
        if (counter.incrementAndGet() < step || !dispatcher.tryStart(player, id)) {
            return;
        }
        final float[] history = player.rotations();
        final int frames = RotationSample.count(history);
        if (frames < id.window()) {
            dispatcher.cancel(player, id);
            return;
        }
        counter.set(0);
        final float[] snapshot = Arrays.copyOfRange(
                history, (frames - id.window()) * RotationSample.STRIDE, history.length);
        final String name = player.name();
        dispatcher.send(player, this, token -> new HttpRequest(FlowerCodec.encodeRequest(snapshot, name, token)));
    }

    @Override
    public @NotNull ModelAnswer readResponse(final byte @NotNull [] body) {
        return new ModelAnswer(FlowerCodec.decodeProbability(body), Double.NaN, true);
    }

    @Override
    public void resetPlayer(final @NotNull UUID player) {
        samples.remove(player);
    }

}
