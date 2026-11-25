package com.g4vrk.react.inference.model.vector.session;

import com.g4vrk.react.api.platform.AimSnapshot;
import com.g4vrk.react.inference.model.vector.feature.AimModeTracker;
import com.g4vrk.react.inference.model.vector.feature.HeldItemClassifier;
import com.g4vrk.react.inference.model.vector.feature.TargetFeature;
import com.g4vrk.react.inference.model.vector.model.AimFrame;
import com.g4vrk.react.inference.model.vector.model.VectorFeature;
import com.g4vrk.react.inference.model.vector.util.MouseMath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Rotation and target state for one Vector combat session.
 */
public final class AimState {

    private final Object lock = new Object();
    private final AimModeTracker aimModes = new AimModeTracker();
    private float lastYaw;
    private float lastPitch;
    private float lastDeltaYaw;
    private float lastDeltaPitch;
    private float lastAccelYaw;
    private float lastAccelPitch;
    private int targetEntityId = -1;

    public void onAttack(final int targetEntityId) {

        synchronized (lock) {
            this.targetEntityId = targetEntityId;
        }

    }

    public int targetEntityId() {

        synchronized (lock) {
            return targetEntityId;
        }

    }

    public @NotNull AimFrame capture(
            final @Nullable AimSnapshot snapshot,
            final float rawYaw,
            final float rawPitch
    ) {

        if (!Float.isFinite(rawYaw) || !Float.isFinite(rawPitch)) {
            throw new IllegalArgumentException("Non-finite rotation sample");
        }
        synchronized (lock) {
            final float yaw = MouseMath.normalizeAngle(rawYaw);
            final float pitch = Math.max(-90.0f, Math.min(90.0f, rawPitch));
            final float deltaYaw = MouseMath.normalizeAngle(yaw - lastYaw);
            final float deltaPitch = pitch - lastPitch;
            final float accelYaw = MouseMath.calculateAcceleration(deltaYaw, lastDeltaYaw);
            final float accelPitch = MouseMath.calculateAcceleration(deltaPitch, lastDeltaPitch);
            final float jerkYaw = MouseMath.calculateJerk(accelYaw, lastAccelYaw);
            final float jerkPitch = MouseMath.calculateJerk(accelPitch, lastAccelPitch);
            final float gcdErrorYaw = MouseMath.calculateGCDError(deltaYaw, lastDeltaYaw);
            final float gcdErrorPitch = MouseMath.calculateGCDError(deltaPitch, lastDeltaPitch);
            final float[] features = captureFeatures(snapshot, yaw, pitch, deltaYaw, deltaPitch);
            lastYaw = yaw;
            lastPitch = pitch;
            lastDeltaYaw = deltaYaw;
            lastDeltaPitch = deltaPitch;
            lastAccelYaw = accelYaw;
            lastAccelPitch = accelPitch;
            return new AimFrame(
                    deltaYaw,
                    deltaPitch,
                    accelYaw,
                    accelPitch,
                    jerkYaw,
                    jerkPitch,
                    gcdErrorYaw,
                    gcdErrorPitch,
                    features
            );
        }

    }

    private float[] captureFeatures(
            final @Nullable AimSnapshot snapshot,
            final float yaw,
            final float pitch,
            final float deltaYaw,
            final float deltaPitch
    ) {

        final float[] features = new float[VectorFeature.COUNT];
        VectorFeature.ROTATION_PRESENT.put(features, true);
        VectorFeature.PITCH.put(features, pitch);
        aimModes.update(lastYaw, yaw, deltaYaw, deltaPitch);
        aimModes.writeTo(features);
        final boolean targetValid = snapshot != null && snapshot.targetValid();
        VectorFeature.TARGET_VALID.put(features, targetValid);
        if (targetValid) {
            TargetFeature.writeTo(features, snapshot, lastYaw, lastPitch, yaw, pitch);
        }
        if (snapshot != null) {
            VectorFeature.HELD_ITEM_CLASS.put(features, HeldItemClassifier.classify(
                    snapshot.mainHandItemId(),
                    snapshot.mainHandItemEdible(),
                    snapshot.mainHandItemBlock()
            ));
            VectorFeature.PLAYER_PING.put(features, snapshot.ping());
        }
        return features;

    }

}
