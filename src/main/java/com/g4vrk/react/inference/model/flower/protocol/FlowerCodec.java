package com.g4vrk.react.inference.model.flower.protocol;

import com.g4vrk.react.inference.model.flower.protocol.schema.FlowerRequest;
import com.g4vrk.react.inference.model.flower.protocol.schema.FlowerResponse;
import com.g4vrk.react.api.rcp.player.RotationSample;
import com.google.flatbuffers.FlatBufferBuilder;
import java.nio.ByteBuffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Flower wire format, see protocol/flower.
 */
public final class FlowerCodec {

    private static final int FEATURES_PER_FRAME = RotationSample.STRIDE;

    private FlowerCodec() {
    }

    public static byte[] encodeRequest(
            final float @NotNull [] frames,
            final @NotNull String playerName,
            final @Nullable String token
    ) {
        if (frames.length % FEATURES_PER_FRAME != 0) {
            throw new IllegalArgumentException("Flower frames must be whole rotation samples");
        }
        // The sample stores every pair pitch first; the model reads yaw (x) first, so each pair is swapped.
        final float[] values = new float[frames.length];
        for (int i = 0; i < frames.length; i += 2) {
            values[i] = finite(frames[i + 1]);
            values[i + 1] = finite(frames[i]);
        }
        final FlatBufferBuilder builder = new FlatBufferBuilder(64 + values.length * 4);
        final int framesOffset = FlowerRequest.createFramesVector(builder, values);
        final int nameOffset = builder.createString(playerName);
        final int tokenOffset = token == null || token.isBlank() ? 0 : builder.createString(token);
        FlowerRequest.finishFlowerRequestBuffer(builder,
                FlowerRequest.createFlowerRequest(builder, tokenOffset, nameOffset, framesOffset));
        return builder.sizedByteArray();
    }


    private static float finite(final float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("Nonfinite Flower rotation");
        }
        return value;
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
