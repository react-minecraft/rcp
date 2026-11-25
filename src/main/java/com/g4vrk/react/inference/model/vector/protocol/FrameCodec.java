package com.g4vrk.react.inference.model.vector.protocol;

import com.g4vrk.react.inference.model.vector.model.AimFrame;
import com.g4vrk.react.inference.model.vector.protocol.schema.InferenceRequest;
import com.g4vrk.react.inference.model.vector.protocol.schema.InferenceResponse;
import com.google.flatbuffers.FlatBufferBuilder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Vector wire format encoder and decoder ({@code protocol/vector}).
 */
public final class FrameCodec {

    public static final int SCHEMA_VERSION = 2;
    public static final String SCHEMA_HASH = "b6e9e126d24af9bfeca4926b0c385bcd68f807b6baaf027934ec88cf14486c06";
    private static final int BATCH_ITEM_COUNT = 1;
    private static final int MODEL_ID = 2;

    private FrameCodec() {

    }

    public static byte @NotNull [] encodeRequest(final AimFrame @NotNull [] frames) {

        return encodeRequest(frames, "Unknown", null);

    }

    public static byte @NotNull [] encodeRequest(
            final AimFrame @NotNull [] frames,
            final @NotNull String playerName,
            final @Nullable String token
    ) {

        return buildRequest(frames, playerName, token).sizedByteArray();

    }

    public static byte @NotNull [] encodeBatch(final AimFrame @NotNull [] frames) {

        return encodeBatch(frames, "Unknown", null);

    }

    public static byte @NotNull [] encodeBatch(
            final AimFrame @NotNull [] frames,
            final @NotNull String playerName,
            final @Nullable String token
    ) {

        final FlatBufferBuilder builder = buildRequest(frames, playerName, token);
        final ByteBuffer data = builder.dataBuffer();
        final int payloadLength = data.remaining();
        final byte[] batch = new byte[6 + payloadLength];
        batch[0] = (byte) BATCH_ITEM_COUNT;
        batch[1] = (byte) (BATCH_ITEM_COUNT >>> 8);
        batch[2] = (byte) payloadLength;
        batch[3] = (byte) (payloadLength >>> 8);
        batch[4] = (byte) (payloadLength >>> 16);
        batch[5] = (byte) (payloadLength >>> 24);
        data.get(batch, 6, payloadLength);
        return batch;

    }

    public static double decodeResponse(final byte @NotNull [] body) {

        if (body.length < 14 || !hasBatchIdentifier(body)) {
            throw new IllegalArgumentException("Invalid VECTOR response; expected GAIB");
        }
        final ByteBuffer buffer = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(4);
        if (Short.toUnsignedInt(buffer.getShort()) != 1) {
            throw new IllegalArgumentException("Vector response item count must be 1");
        }
        final double probability = buffer.getDouble();
        if (!Double.isFinite(probability) || probability < 0 || probability > 1) {
            throw new IllegalArgumentException("Invalid VECTOR probability");
        }
        return probability;

    }

    public static @NotNull Prediction decodePrediction(final byte @NotNull [] body) {

        final double prefixProbability = decodeResponse(body);
        final ByteBuffer payload = ByteBuffer.wrap(body, 14, body.length - 14)
                .slice()
                .order(ByteOrder.LITTLE_ENDIAN);
        try {
            return readPrediction(payload, prefixProbability);
        } catch (final IndexOutOfBoundsException | NegativeArraySizeException | ArithmeticException malformed) {
            throw new IllegalArgumentException("Malformed VECTOR response", malformed);
        }

    }

    private static @NotNull FlatBufferBuilder buildRequest(
            final AimFrame @NotNull [] frames,
            final @NotNull String playerName,
            final @Nullable String token
    ) {

        if (frames.length == 0) {
            throw new IllegalArgumentException("Vector frames cannot be empty");
        }
        final float[] features = flatten(frames);
        final FlatBufferBuilder builder = new FlatBufferBuilder(64 + features.length * 4);
        final int featuresOffset = InferenceRequest.createFeaturesVector(builder, features);
        final int nameOffset = builder.createString(playerName);
        final int schemaOffset = builder.createString(SCHEMA_HASH);
        final int tokenOffset = token == null || token.isBlank() ? 0 : builder.createString(token);
        InferenceRequest.startInferenceRequest(builder);
        InferenceRequest.addToken(builder, tokenOffset);
        InferenceRequest.addFeatures(builder, featuresOffset);
        InferenceRequest.addFrameCount(builder, frames.length);
        InferenceRequest.addModelId(builder, MODEL_ID);
        InferenceRequest.addSchemaVersion(builder, SCHEMA_VERSION);
        InferenceRequest.addPlayerName(builder, nameOffset);
        InferenceRequest.addSchemaHash(builder, schemaOffset);
        InferenceRequest.addSupportsObservation(builder, true);
        InferenceRequest.finishInferenceRequestBuffer(builder, InferenceRequest.endInferenceRequest(builder));
        return builder;

    }

    private static @NotNull Prediction readPrediction(
            final @NotNull ByteBuffer payload,
            final double prefixProbability
    ) {

        if (!InferenceResponse.InferenceResponseBufferHasIdentifier(payload)) {
            throw new IllegalArgumentException("Expected GAIR response");
        }
        final InferenceResponse response = InferenceResponse.getRootAsInferenceResponse(payload);
        if (response.schemaVersion() != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported VECTOR response schema");
        }
        final double probability = response.cheatProbability();
        final double threshold = response.decisionThreshold();
        final int mode = response.deploymentMode();
        final boolean enforcementEnabled = response.enforcementEnabled();
        if (mode < 0 || mode > 1 || enforcementEnabled != (mode == 1) || !Double.isFinite(threshold) || threshold < 0 || threshold > 1
                || Double.compare(probability, prefixProbability) != 0) {
            throw new IllegalArgumentException("Inconsistent VECTOR response metadata");
        }
        return new Prediction(probability, threshold, enforcementEnabled, readRunId(response));

    }

    private static @NotNull String readRunId(final @NotNull InferenceResponse response) {

        final ByteBuffer encoded = response.runIdAsByteBuffer();
        if (encoded == null) {
            return "";
        }
        if (encoded.remaining() > 1024) {
            throw new IllegalArgumentException("Invalid run identifier");
        }
        final String runId = response.runId();
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("Invalid run identifier");
        }
        for (int index = 0; index < runId.length(); index++) {
            if (Character.isISOControl(runId.charAt(index))) {
                throw new IllegalArgumentException("Invalid run identifier");
            }
        }
        return runId;

    }

    private static float @NotNull [] flatten(final AimFrame @NotNull [] frames) {

        final int stride = AimFrame.INFERENCE_FEATURE_COUNT;
        final float[] features = new float[frames.length * stride];
        for (int frameIndex = 0; frameIndex < frames.length; frameIndex++) {
            final int base = frameIndex * stride;
            frames[frameIndex].copyInferenceFeaturesTo(features, base);
            for (int index = base; index < base + stride; index++) {
                if (!Float.isFinite(features[index])) {
                    throw new IllegalArgumentException("Nonfinite VECTOR feature");
                }
            }
        }
        return features;

    }

    private static boolean hasBatchIdentifier(final byte @NotNull [] body) {

        return body[0] == 'G' && body[1] == 'A' && body[2] == 'I' && body[3] == 'B';

    }

    /**
     * Decoded inference verdict for one Vector batch.
     *
     * @param probability        cheat probability in [0, 1]
     * @param threshold          backend decision threshold
     * @param enforcementEnabled whether enforcement is enabled
     * @param runId              backend model run identifier
     */
    public record Prediction(
            double probability,
            double threshold,
            boolean enforcementEnabled,
            @NotNull String runId
    ) {

    }

}
