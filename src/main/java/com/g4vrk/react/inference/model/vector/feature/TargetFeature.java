package com.g4vrk.react.inference.model.vector.feature;

import com.g4vrk.react.api.platform.AimSnapshot;
import com.g4vrk.react.inference.model.vector.model.VectorFeature;
import org.jetbrains.annotations.NotNull;

/**
 * Writes platform-neutral target geometry into a Vector feature row.
 */
public final class TargetFeature {

    private TargetFeature() {

        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");

    }

    public static void writeTo(
            final float @NotNull [] features,
            final @NotNull AimSnapshot snapshot,
            final float fromYaw,
            final float fromPitch,
            final float yaw,
            final float pitch
    ) {

        final double px = snapshot.playerX();
        final double py = snapshot.playerY();
        final double pz = snapshot.playerZ();
        final double centerX = (snapshot.targetMinX() + snapshot.targetMaxX()) * 0.5;
        final double centerY = (snapshot.targetMinY() + snapshot.targetMaxY()) * 0.5;
        final double centerZ = (snapshot.targetMinZ() + snapshot.targetMaxZ()) * 0.5;
        VectorFeature.TARGET_AIM_ANGLE.put(
                features,
                (float) minAngleToBox(snapshot, fromYaw, fromPitch, yaw, pitch)
        );
        VectorFeature.TARGET_REL_X.put(features, (float) (centerX - px));
        VectorFeature.TARGET_REL_Y.put(features, (float) (centerY - py));
        VectorFeature.TARGET_REL_Z.put(features, (float) (centerZ - pz));
        VectorFeature.TARGETS_IN_RANGE_COUNT.put(features, snapshot.targetsInRange());

    }

    private static double minAngleToBox(
            final @NotNull AimSnapshot snapshot,
            final float lastYaw,
            final float lastPitch,
            final float yaw,
            final float pitch
    ) {

        final int clientProtocol = snapshot.clientProtocol();
        final int directions = clientProtocol >= 0 && clientProtocol < 47
                ? 1
                : (clientProtocol < 0 || clientProtocol >= 107 ? 3 : 2);
        final double pitchRadians = Math.toRadians(pitch);
        final double pitchCos = Math.cos(pitchRadians);
        final double pitchSin = -Math.sin(pitchRadians);
        final double yawRadians = Math.toRadians(-yaw);
        final double rawD0x = Math.sin(yawRadians) * pitchCos;
        final double rawD0z = Math.cos(yawRadians) * pitchCos;
        final double s0 = normalizeScale(rawD0x, pitchSin, rawD0z);
        final double d0x = rawD0x * s0;
        final double d0y = pitchSin * s0;
        final double d0z = rawD0z * s0;

        double d1x = 0.0;
        double d1y = 0.0;
        double d1z = 0.0;
        double d2x = 0.0;
        double d2y = 0.0;
        double d2z = 0.0;
        if (directions >= 2) {
            final double lastYawRadians = Math.toRadians(-lastYaw);
            final double lastYawSin = Math.sin(lastYawRadians);
            final double lastYawCos = Math.cos(lastYawRadians);
            final double rawD1x = lastYawSin * pitchCos;
            final double rawD1z = lastYawCos * pitchCos;
            final double s1 = normalizeScale(rawD1x, pitchSin, rawD1z);
            d1x = rawD1x * s1;
            d1y = pitchSin * s1;
            d1z = rawD1z * s1;
            if (directions >= 3) {
                final double lastPitchRadians = Math.toRadians(lastPitch);
                final double lastPitchCos = Math.cos(lastPitchRadians);
                final double rawD2x = lastYawSin * lastPitchCos;
                final double rawD2y = -Math.sin(lastPitchRadians);
                final double rawD2z = lastYawCos * lastPitchCos;
                final double s2 = normalizeScale(rawD2x, rawD2y, rawD2z);
                d2x = rawD2x * s2;
                d2y = rawD2y * s2;
                d2z = rawD2z * s2;
            }
        }

        double maxDot = Double.NEGATIVE_INFINITY;
        final double eyeY = snapshot.playerY() + snapshot.eyeHeight();
        for (int point = 0; point < 9; point++) {
            final double dx = pointX(snapshot, point) - snapshot.playerX();
            final double dy = pointY(snapshot, point) - eyeY;
            final double dz = pointZ(snapshot, point) - snapshot.playerZ();
            final double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < 0.001) {
                return 0.0;
            }
            final double nx = dx / length;
            final double ny = dy / length;
            final double nz = dz / length;
            final double dot0 = d0x * nx + d0y * ny + d0z * nz;
            if (dot0 > maxDot) {
                maxDot = dot0;
            }
            if (directions >= 2) {
                final double dot1 = d1x * nx + d1y * ny + d1z * nz;
                if (dot1 > maxDot) {
                    maxDot = dot1;
                }
                if (directions >= 3) {
                    final double dot2 = d2x * nx + d2y * ny + d2z * nz;
                    if (dot2 > maxDot) {
                        maxDot = dot2;
                    }
                }
            }
        }
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, maxDot))));

    }

    private static double normalizeScale(
            final double x,
            final double y,
            final double z
    ) {

        final double length = Math.sqrt(x * x + y * y + z * z);
        return length == 0.0 ? 1.0 : 1.0 / length;

    }

    private static double pointX(
            final @NotNull AimSnapshot snapshot,
            final int point
    ) {

        if (point == 0) {
            return (snapshot.targetMinX() + snapshot.targetMaxX()) * 0.5;
        }
        return ((point - 1) & 1) == 0 ? snapshot.targetMinX() : snapshot.targetMaxX();

    }

    private static double pointY(
            final @NotNull AimSnapshot snapshot,
            final int point
    ) {

        if (point == 0) {
            return (snapshot.targetMinY() + snapshot.targetMaxY()) * 0.5;
        }
        return ((point - 1) & 2) == 0 ? snapshot.targetMinY() : snapshot.targetMaxY();

    }

    private static double pointZ(
            final @NotNull AimSnapshot snapshot,
            final int point
    ) {

        if (point == 0) {
            return (snapshot.targetMinZ() + snapshot.targetMaxZ()) * 0.5;
        }
        return ((point - 1) & 4) == 0 ? snapshot.targetMinZ() : snapshot.targetMaxZ();

    }

}
