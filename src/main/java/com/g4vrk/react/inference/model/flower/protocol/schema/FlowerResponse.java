package com.g4vrk.react.inference.model.flower.protocol.schema;

import com.google.flatbuffers.Constants;
import com.google.flatbuffers.FlatBufferBuilder;
import com.google.flatbuffers.Table;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Generated FlatBuffers accessors for the FLOWER response table.
 */
@SuppressWarnings("unused")
public final class FlowerResponse extends Table {

    public static void ValidateVersion() {

        Constants.FLATBUFFERS_25_2_10();

    }

    public static FlowerResponse getRootAsFlowerResponse(final ByteBuffer buffer) {

        return getRootAsFlowerResponse(buffer, new FlowerResponse());

    }

    public static FlowerResponse getRootAsFlowerResponse(final ByteBuffer buffer, final FlowerResponse response) {

        buffer.order(ByteOrder.LITTLE_ENDIAN);
        return response.__assign(buffer.getInt(buffer.position()) + buffer.position(), buffer);

    }

    public static boolean FlowerResponseBufferHasIdentifier(final ByteBuffer buffer) {

        return __has_identifier(buffer, "GAFR");

    }

    public void __init(final int position, final ByteBuffer buffer) {

        __reset(position, buffer);

    }

    public FlowerResponse __assign(final int position, final ByteBuffer buffer) {

        __init(position, buffer);
        return this;

    }

    public double cheatProbability() {

        final int offset = __offset(4);
        return offset != 0 ? bb.getDouble(offset + bb_pos) : 0.0D;

    }

    public long schemaVersion() {

        final int offset = __offset(6);
        return offset != 0 ? (long) bb.getInt(offset + bb_pos) & 0xFFFFFFFFL : 0L;

    }

    public double decisionThreshold() {

        final int offset = __offset(8);
        return offset != 0 ? bb.getDouble(offset + bb_pos) : -1.0D;

    }

    public int deploymentMode() {

        final int offset = __offset(10);
        return offset != 0 ? bb.get(offset + bb_pos) & 0xFF : 0xFF;

    }

    public boolean enforcementEnabled() {

        final int offset = __offset(12);
        return offset != 0 && bb.get(offset + bb_pos) != 0;

    }

    public boolean hasEnforcementEnabled() {

        return __offset(12) != 0;

    }

    public String runId() {

        final int offset = __offset(14);
        return offset != 0 ? __string(offset + bb_pos) : null;

    }

    public ByteBuffer runIdAsByteBuffer() {

        return __vector_as_bytebuffer(14, 1);

    }

    public static int createFlowerResponse(
            final FlatBufferBuilder builder,
            final long schemaVersion,
            final double cheatProbability,
            final double decisionThreshold,
            final int deploymentMode,
            final boolean enforcementEnabled,
            final int runIdOffset
    ) {

        builder.startTable(6);
        addDecisionThreshold(builder, decisionThreshold);
        addCheatProbability(builder, cheatProbability);
        addRunId(builder, runIdOffset);
        addSchemaVersion(builder, schemaVersion);
        addEnforcementEnabled(builder, enforcementEnabled);
        addDeploymentMode(builder, deploymentMode);
        return endFlowerResponse(builder);

    }

    public static int createFlowerResponse(final FlatBufferBuilder builder, final double cheatProbability) {

        builder.startTable(1);
        addCheatProbability(builder, cheatProbability);
        return endFlowerResponse(builder);

    }

    public static void startFlowerResponse(final FlatBufferBuilder builder) {

        builder.startTable(6);

    }

    public static void addCheatProbability(final FlatBufferBuilder builder, final double cheatProbability) {

        builder.addDouble(0, cheatProbability, 0.0D);

    }

    public static void addSchemaVersion(final FlatBufferBuilder builder, final long schemaVersion) {

        builder.addInt(1, (int) schemaVersion, 0);

    }

    public static void addDecisionThreshold(final FlatBufferBuilder builder, final double decisionThreshold) {

        builder.addDouble(2, decisionThreshold, -1.0D);

    }

    public static void addDeploymentMode(final FlatBufferBuilder builder, final int deploymentMode) {

        builder.addByte(3, (byte) deploymentMode, (byte) 0xFF);

    }

    public static void addEnforcementEnabled(final FlatBufferBuilder builder, final boolean enforcementEnabled) {

        builder.addBoolean(4, enforcementEnabled, !enforcementEnabled);

    }

    public static void addRunId(final FlatBufferBuilder builder, final int runIdOffset) {

        builder.addOffset(5, runIdOffset, 0);

    }

    public static int endFlowerResponse(final FlatBufferBuilder builder) {

        return builder.endTable();

    }

    public static void finishFlowerResponseBuffer(final FlatBufferBuilder builder, final int offset) {

        builder.finish(offset, "GAFR");

    }

    public static void finishSizePrefixedFlowerResponseBuffer(final FlatBufferBuilder builder, final int offset) {

        builder.finishSizePrefixed(offset, "GAFR");

    }

}
