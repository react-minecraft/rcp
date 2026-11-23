package com.g4vrk.react.inference.model.vector.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

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

}
