package com.g4vrk.react.inference.model.flower.protocol;

import com.g4vrk.react.inference.model.flower.protocol.schema.FlowerRequest;
import com.g4vrk.react.inference.model.flower.protocol.schema.FlowerResponse;
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
        final float[] rotation = {1F, 2F, 3F, 4F, 5F, 6F, 7F, 8F};
        final FlowerRequest request = FlowerRequest.getRootAsFlowerRequest(
                ByteBuffer.wrap(FlowerCodec.encodeRequest(rotation, "Steve", null)));

        final float[] values = new float[request.framesLength()];
        for (int i = 0; i < values.length; i++) values[i] = request.frames(i);
        // In: pitch, yaw, deltas, jerks, gcd errors. Out: yaw (x) first in every pair.
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
        final float[] rotation = {Float.NaN, 0F, 0F, 0F, 0F, 0F, 0F, 0F};
        assertThrows(IllegalArgumentException.class,
                () -> FlowerCodec.encodeRequest(rotation, "Steve", null));
    }
}
