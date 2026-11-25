package com.g4vrk.react.inference.model.vector.protocol;

import com.g4vrk.react.inference.model.vector.model.AimFrame;
import com.g4vrk.react.inference.model.vector.model.VectorFeature;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Verifies that RCP can read binary responses produced by the backend.
 */
class FrameCodecInteropTest {

    @Test
    void decodesBackendSharedFixture() throws IOException {

        final byte[] encoded;
        try (final InputStream input = FrameCodecInteropTest.class.getResourceAsStream("/protocol/vector-response-v2.b64")) {
            if (input == null) {
                throw new IllegalStateException("VECTOR fixture is missing");
            }
            encoded = Base64.getDecoder().decode(new String(input.readAllBytes(), StandardCharsets.US_ASCII).strip());
        }
        final FrameCodec.Prediction prediction = FrameCodec.decodePrediction(encoded);
        assertEquals(.73456D, prediction.probability());
        assertEquals(.93D, prediction.threshold());
        assertFalse(prediction.enforcementEnabled());
        assertEquals("shared-vector-run", prediction.runId());

    }

    @Test
    void encodeBatchMatchesRequestWithLittleEndianFraming() {

        final float[] vector = new float[VectorFeature.COUNT];
        vector[VectorFeature.ROTATION_PRESENT.ordinal()] = 1.0F;
        vector[VectorFeature.PITCH.ordinal()] = 12.345F;
        final AimFrame frame = new AimFrame(1.5F, -0.25F, 0.1F, -0.05F, 0.02F, 0.01F, 0.001F, 0.002F, vector);
        final AimFrame[] frames = new AimFrame[] {frame, frame};

        final byte[] single = FrameCodec.encodeRequest(frames, "Steve", "rx_token");
        final byte[] batch = FrameCodec.encodeBatch(frames, "Steve", "rx_token");
        final byte[] expected = ByteBuffer.allocate(6 + single.length)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) 1)
                .putInt(single.length)
                .put(single)
                .array();

        assertArrayEquals(expected, batch);

    }

}
