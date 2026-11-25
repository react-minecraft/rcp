package com.g4vrk.react.inference.model.vector.feature;

import com.g4vrk.react.api.platform.AimSnapshot;
import com.g4vrk.react.inference.model.vector.model.VectorFeature;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies target aim angle computation without intermediate vector allocations.
 */
final class TargetFeatureTest {

    @Test
    void computesAimAngleAcrossClientProtocols() {

        final AimSnapshot v17Snapshot = snapshotWithProtocol(47);
        final float[] features = new float[VectorFeature.COUNT];
        TargetFeature.writeTo(features, v17Snapshot, 0.0F, 0.0F, 45.0F, 0.0F);
        /*
         * Target box center lies along +Z (yaw 0); protocol >= 47 includes (lastYaw=0, pitch=0).
         */
        assertTrue(features[VectorFeature.TARGET_AIM_ANGLE.ordinal()] < 15.0F);

        final AimSnapshot legacySnapshot = snapshotWithProtocol(5);
        final float[] legacyFeatures = new float[VectorFeature.COUNT];
        TargetFeature.writeTo(legacyFeatures, legacySnapshot, 0.0F, 0.0F, 90.0F, 0.0F);
        /*
         * Legacy protocol < 47 uses only current (yaw=90, pitch=0), looking perpendicular to +Z.
         */
        assertTrue(legacyFeatures[VectorFeature.TARGET_AIM_ANGLE.ordinal()] > 60.0F);
        assertEquals(0.0F, legacyFeatures[VectorFeature.TARGET_REL_X.ordinal()]);
        assertEquals(0.9F, legacyFeatures[VectorFeature.TARGET_REL_Y.ordinal()]);
        assertEquals(3.0F, legacyFeatures[VectorFeature.TARGET_REL_Z.ordinal()]);

    }

    private static AimSnapshot snapshotWithProtocol(final int clientProtocol) {

        return new AimSnapshot(
                0.0,
                0.0,
                0.0,
                1.62,
                true,
                -0.3,
                0.0,
                2.7,
                0.3,
                1.8,
                3.3,
                1,
                "minecraft:diamond_sword",
                false,
                false,
                20.0F,
                clientProtocol
        );

    }

}
