package com.g4vrk.react.inference.model.vector.feature;

import com.g4vrk.react.inference.model.vector.model.VectorFeature;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Keeps the model input stable for a sequence with overlapping mode buckets.
 */
class AimModeTrackerCompatibilityTest {

    @Test
    void preservesModeForOverlappingBuckets() {

        final AimModeTracker tracker = new AimModeTracker();
        final Random random = new Random(23);
        for (int index = 0; index < 69; index++) {
            final float yaw = (1 + random.nextInt(40)) * (0.010f + random.nextInt(30) * 0.0001f);
            final float pitch = (1 + random.nextInt(40)) * (0.010f + random.nextInt(30) * 0.0001f);
            tracker.update(0.0f, yaw, yaw, pitch);
        }
        final float[] features = new float[VectorFeature.COUNT];
        tracker.writeTo(features);
        assertEquals(0.0f, features[VectorFeature.MODE_YAW.ordinal()]);
        assertEquals(0.0f, features[VectorFeature.MODE_YAW_VALID.ordinal()]);
        assertEquals(0.0f, features[VectorFeature.DELTA_DOTS_YAW.ordinal()]);

    }

}
