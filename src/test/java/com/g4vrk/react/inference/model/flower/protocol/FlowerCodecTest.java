package com.g4vrk.react.inference.model.flower.protocol;

import com.g4vrk.react.inference.model.ModelAnswer;
import com.g4vrk.react.inference.model.flower.protocol.schema.FlowerRequest;
import com.g4vrk.react.inference.model.flower.protocol.schema.FlowerResponse;
import com.google.flatbuffers.FlatBufferBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowerCodecTest {

    @Test
    void writesRotationsInSchemaOrder() {

        final float[] rotation = {1F, 2F, 3F, 4F, 5F, 6F, 7F, 8F};
        final FlowerRequest request = FlowerRequest.getRootAsFlowerRequest(
                ByteBuffer.wrap(FlowerCodec.encodeRequest(rotation, "Steve", null)));

        final float[] values = new float[request.framesLength()];
        for (int i = 0; i < values.length; i++) {
            values[i] = request.frames(i);
        }
        /*
         * In: pitch, yaw, deltas, jerks, gcd errors. Out: yaw (x) first in every pair.
         */
        assertArrayEquals(new float[]{2, 1, 4, 3, 6, 5, 8, 7}, values);
        assertEquals("Steve", request.playerName());
        assertNull(request.token());

    }

    @Test
    void readsVersionedPredictionAndRejectsOtherBuffers() {

        final FlatBufferBuilder builder = new FlatBufferBuilder();
        final int runId = builder.createString("flower-run-42");
        FlowerResponse.finishFlowerResponseBuffer(builder, FlowerResponse.createFlowerResponse(
                builder, 2, .75, .8, 0, false, runId));
        final ModelAnswer answer = FlowerCodec.decodeAnswer(builder.sizedByteArray());
        assertEquals(.75, answer.probability());
        assertEquals(.8, answer.threshold());
        assertFalse(answer.enforcement());
        assertEquals("flower-run-42", answer.runId());

        assertThrows(IllegalArgumentException.class, () -> FlowerCodec.decodeAnswer("{\"p\":1}".getBytes()));
        final FlatBufferBuilder invalid = new FlatBufferBuilder();
        FlowerResponse.finishFlowerResponseBuffer(invalid, FlowerResponse.createFlowerResponse(invalid, 1.5));
        assertThrows(IllegalArgumentException.class, () -> FlowerCodec.decodeAnswer(invalid.sizedByteArray()));

    }

    @Test
    void acceptsLegacyProbabilityOnlyResponse() {

        final FlatBufferBuilder builder = new FlatBufferBuilder();
        FlowerResponse.finishFlowerResponseBuffer(builder, FlowerResponse.createFlowerResponse(builder, .75));
        final ModelAnswer answer = FlowerCodec.decodeAnswer(builder.sizedByteArray());
        assertEquals(.75, answer.probability());
        assertTrue(answer.enforcement());
        assertTrue(Double.isNaN(answer.threshold()));
        assertNull(answer.runId());

    }

    @Test
    void decodesBackendSharedFixture() throws IOException {

        final byte[] encoded;
        try (final InputStream input = FlowerCodecTest.class.getResourceAsStream("/protocol/flower-response-v2.b64")) {
            if (input == null) {
                throw new IllegalStateException("FLOWER fixture is missing");
            }
            encoded = Base64.getDecoder().decode(new String(input.readAllBytes(), StandardCharsets.US_ASCII).strip());
        }
        final ModelAnswer answer = FlowerCodec.decodeAnswer(encoded);
        assertEquals(.73456D, answer.probability());
        assertEquals(.93D, answer.threshold());
        assertFalse(answer.enforcement());
        assertEquals("shared-flower-run", answer.runId());

    }

    @Test
    void rejectsNonFiniteRotations() {

        final float[] rotation = {Float.NaN, 0F, 0F, 0F, 0F, 0F, 0F, 0F};
        assertThrows(
                IllegalArgumentException.class,
                () -> FlowerCodec.encodeRequest(rotation, "Steve", null)
        );

    }
}
