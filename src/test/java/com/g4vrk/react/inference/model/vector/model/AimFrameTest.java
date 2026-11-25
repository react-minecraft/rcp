package com.g4vrk.react.inference.model.vector.model;

import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies fast six-decimal quantization matches CSV string formatting bit-for-bit.
 */
final class AimFrameTest {

    @Test
    void roundToSixDecimalsMatchesStringFormatParity() {

        final float[] edges = {
                0.0F,
                -0.0F,
                1.0E-7F,
                -1.0E-7F,
                0.0000005F,
                -0.0000005F,
                0.0000015F,
                -0.0000015F,
                1.0F / 128.0F,
                -1.0F / 128.0F,
                65536.0F + 1.0F / 128.0F,
                -(65536.0F + 1.0F / 128.0F),
                Float.MIN_VALUE,
                -Float.MIN_VALUE,
                Float.MIN_NORMAL,
                -Float.MIN_NORMAL,
                8_388_607.5F,
                8_388_608.0F,
                1.0E15F,
                Float.MAX_VALUE,
                -Float.MAX_VALUE,
                Float.NaN,
                Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY
        };
        for (final float value : edges) {
            assertParity(value);
        }

        final Random random = new Random(2026L);
        for (int index = 0; index < 50_000; index++) {
            final float sample = (random.nextFloat() - 0.5F) * 2_000.0F;
            assertParity(sample);
        }

        final float[] vector = new float[VectorFeature.COUNT];
        vector[VectorFeature.PITCH.ordinal()] = 12.3456789F;
        final AimFrame frame = new AimFrame(
                1.2345678F,
                -0.0000005F,
                0.0078125F,
                -1.0F / 128.0F,
                0.0F,
                -0.0F,
                0.9999999F,
                0.1234564F,
                vector
        );
        final float[] inference = frame.inferenceFeatures();
        assertEquals(
                Float.parseFloat(String.format(Locale.US, "%.6f", 1.2345678F)),
                inference[0]
        );
        assertEquals(
                Float.parseFloat(String.format(Locale.US, "%.6f", 12.3456789F)),
                inference[VectorFeature.PITCH.datasetIndex()]
        );

    }

    private static void assertParity(final float value) {

        final float expected = Float.parseFloat(String.format(Locale.US, "%.6f", value));
        final float actual = AimFrame.roundToSixDecimals(value);
        assertEquals(
                Float.floatToRawIntBits(expected),
                Float.floatToRawIntBits(actual),
                "Bit parity mismatch for value " + value
        );

    }

}
