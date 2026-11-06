package com.g4vrk.react.inference.model.flower.protocol;

import com.g4vrk.react.inference.model.flower.protocol.schema.FlowerRequest;
import com.g4vrk.react.inference.model.flower.protocol.schema.FlowerResponse;
import com.g4vrk.react.api.rcp.player.Rotation;
import com.google.flatbuffers.FlatBufferBuilder;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlowerCodecTest {

    @Test
    void writesRotationsInSchemaOrder() {
        final Rotation rotation = new Rotation(1, 2, 3, 4, 5, 6, 7, 8);
        final FlowerRequest request = FlowerRequest.getRootAsFlowerRequest(
                ByteBuffer.wrap(FlowerCodec.encodeRequest(new Rotation[]{rotation}, "Steve", null)));

        final float[] values = new float[request.framesLength()];
        for (int i = 0; i < values.length; i++) values[i] = request.frames(i);
        // x, y, deltaX, deltaY, jerkX, jerkY, gcdErrorX, gcdErrorY
        assertArrayEquals(new float[]{2, 1, 4, 3, 6, 5, 8, 7}, values);
        assertEquals("Steve", request.playerName());
        assertNull(request.token());
    }

    @Test
    void readsProbabilityAndRejectsOtherBuffers() {
        final FlatBufferBuilder builder = new FlatBufferBuilder();
        FlowerResponse.finishFlowerResponseBuffer(builder, FlowerResponse.createFlowerResponse(builder, .75));
        assertEquals(.75, FlowerCodec.decodeProbability(builder.sizedByteArray()));

        assertThrows(IllegalArgumentException.class, () -> FlowerCodec.decodeProbability("{\"p\":1}".getBytes()));
        final FlatBufferBuilder invalid = new FlatBufferBuilder();
        FlowerResponse.finishFlowerResponseBuffer(invalid, FlowerResponse.createFlowerResponse(invalid, 1.5));
        assertThrows(IllegalArgumentException.class, () -> FlowerCodec.decodeProbability(invalid.sizedByteArray()));
    }

    @Test
    void rejectsNonFiniteRotations() {
        final Rotation rotation = new Rotation(Float.NaN, 0, 0, 0, 0, 0, 0, 0);
        assertThrows(IllegalArgumentException.class,
                () -> FlowerCodec.encodeRequest(new Rotation[]{rotation}, "Steve", null));
    }
}
