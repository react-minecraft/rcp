package com.g4vrk.react.inference.model.flower.protocol;

import com.g4vrk.react.inference.model.flower.protocol.schema.FlowerRequest;
import com.g4vrk.react.inference.model.flower.protocol.schema.FlowerResponse;
import com.g4vrk.react.inference.model.ModelAnswer;
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
        for (final float value : frames) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Nonfinite Flower rotation");
            }
        }
        final FlatBufferBuilder builder = new FlatBufferBuilder(64 + frames.length * 4);
        /*
         * The sample stores every pair pitch first; FlatBuffers vectors populate back-to-front so
         * pushing (pitch, yaw) yields (yaw, pitch) at index 0 without a temporary copy array.
         */
        FlowerRequest.startFramesVector(builder, frames.length);
        for (int i = frames.length - 2; i >= 0; i -= 2) {
            builder.addFloat(frames[i]);
            builder.addFloat(frames[i + 1]);
        }
        final int framesOffset = builder.endVector();
        final int nameOffset = builder.createString(playerName);
        final int tokenOffset = token == null || token.isBlank() ? 0 : builder.createString(token);
        FlowerRequest.finishFlowerRequestBuffer(
                builder,
                FlowerRequest.createFlowerRequest(builder, tokenOffset, nameOffset, framesOffset)
        );
        return builder.sizedByteArray();

    }

    public static @NotNull ModelAnswer decodeAnswer(final byte @NotNull [] body) {

        if (body.length < 12) {
            throw new IllegalArgumentException("Invalid Flower response; expected GAFR");
        }
        final ByteBuffer buffer = ByteBuffer.wrap(body);
        try {
            if (!FlowerResponse.FlowerResponseBufferHasIdentifier(buffer)) {
                throw new IllegalArgumentException("Invalid Flower response; expected GAFR");
            }
            final FlowerResponse response = FlowerResponse.getRootAsFlowerResponse(buffer);
            final double probability = response.cheatProbability();
            if (!Double.isFinite(probability) || probability < 0.0D || probability > 1.0D) {
                throw new IllegalArgumentException("Invalid Flower probability");
            }
            final long schemaVersion = response.schemaVersion();
            if (schemaVersion == 0L) {
                return new ModelAnswer(probability, Double.NaN, false);
            }
            final double threshold = response.decisionThreshold();
            final int mode = response.deploymentMode();
            final boolean enforcement = response.enforcementEnabled();
            final ByteBuffer encodedRunId = response.runIdAsByteBuffer();
            if (schemaVersion != 2L || mode < 0 || mode > 1 || enforcement != (mode == 1)
                    || !Double.isFinite(threshold) || threshold < 0.0D || threshold > 1.0D
                    || !response.hasEnforcementEnabled()
                    || (encodedRunId != null && encodedRunId.remaining() > 1024)) {
                throw new IllegalArgumentException("Invalid Flower response metadata");
            }
            final String runId = response.runId();
            if (runId != null) {
                if (runId.isBlank()) {
                    throw new IllegalArgumentException("Invalid Flower run identifier");
                }
                for (int index = 0; index < runId.length(); index++) {
                    if (Character.isISOControl(runId.charAt(index))) {
                        throw new IllegalArgumentException("Invalid Flower run identifier");
                    }
                }
            }
            return new ModelAnswer(probability, threshold, enforcement, runId);
        } catch (final IndexOutOfBoundsException | NegativeArraySizeException | ArithmeticException malformed) {
            throw new IllegalArgumentException("Malformed Flower response", malformed);
        }

    }

    public static double decodeProbability(final byte @NotNull [] body) {

        return decodeAnswer(body).probability();

    }

}
