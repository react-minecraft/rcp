package com.g4vrk.react.inference.model.flower.protocol;

import com.g4vrk.react.inference.model.flower.protocol.schema.FlowerRequest;
import com.g4vrk.react.inference.model.flower.protocol.schema.FlowerResponse;
import com.g4vrk.react.api.rcp.player.Rotation;
import com.google.flatbuffers.FlatBufferBuilder;
import java.nio.ByteBuffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Flower wire format, see protocol/flower.
 */
public final class FlowerCodec {

    private static final int FEATURES_PER_FRAME = 8;

    private FlowerCodec() {
    }

    public static byte[] encodeRequest(
            final @NotNull Rotation[] frames,
            final @NotNull String playerName,
            final @Nullable String token
    ) {
        final float[] values = new float[frames.length * FEATURES_PER_FRAME];
        int index = 0;
        for (final Rotation frame : frames) {
            index = put(values, index, frame.x());
            index = put(values, index, frame.y());
            index = put(values, index, frame.deltaX());
            index = put(values, index, frame.deltaY());
            index = put(values, index, frame.jerkX());
            index = put(values, index, frame.jerkY());
            index = put(values, index, frame.gcdErrorX());
            index = put(values, index, frame.gcdErrorY());
        }
        final FlatBufferBuilder builder = new FlatBufferBuilder(64 + values.length * 4);
        final int framesOffset = FlowerRequest.createFramesVector(builder, values);
        final int nameOffset = builder.createString(playerName);
        final int tokenOffset = token == null || token.isBlank() ? 0 : builder.createString(token);
        FlowerRequest.finishFlowerRequestBuffer(builder,
                FlowerRequest.createFlowerRequest(builder, tokenOffset, nameOffset, framesOffset));
        return builder.sizedByteArray();
    }

    private static int put(final float[] values, final int index, final float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("Nonfinite Flower rotation");
        }
        values[index] = value;
        return index + 1;
    }

    public static double decodeProbability(final byte @NotNull [] body) {
        final ByteBuffer buffer = ByteBuffer.wrap(body);
        if (body.length < 8 || !FlowerResponse.FlowerResponseBufferHasIdentifier(buffer)) {
            throw new IllegalArgumentException("Invalid Flower response; expected GAFR");
        }
        final double probability;
        try {
            probability = FlowerResponse.getRootAsFlowerResponse(buffer).cheatProbability();
        } catch (final IndexOutOfBoundsException malformed) {
            throw new IllegalArgumentException("Malformed Flower response", malformed);
        }
        if (!Double.isFinite(probability) || probability < 0 || probability > 1) {
            throw new IllegalArgumentException("Invalid Flower probability");
        }
        return probability;
    }

}
