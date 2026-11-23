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
            final @NotNull float[] features,
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
        VectorFeature.TARGET_AIM_ANGLE.put(features, (float) minAngleToBox(snapshot, lookDirections(
                snapshot.clientProtocol(),
                fromYaw,
                fromPitch,
                yaw,
                pitch
        )));
        VectorFeature.TARGET_REL_X.put(features, (float) (centerX - px));
        VectorFeature.TARGET_REL_Y.put(features, (float) (centerY - py));
        VectorFeature.TARGET_REL_Z.put(features, (float) (centerZ - pz));
        VectorFeature.TARGETS_IN_RANGE_COUNT.put(features, snapshot.targetsInRange());

    }

    private static double[] lookDirections(
            final int clientProtocol,
            final float lastYaw,
            final float lastPitch,
            final float yaw,
            final float pitch
    ) {

        if (clientProtocol >= 0 && clientProtocol < 47) {
            return writeLookVector(yaw, pitch, new double[3], 0);
        }
        final boolean includeLastRotation = clientProtocol < 0 || clientProtocol >= 107;
        final double[] directions = new double[3 * (includeLastRotation ? 3 : 2)];
        writeLookVector(yaw, pitch, directions, 0);
        writeLookVector(lastYaw, pitch, directions, 3);
        if (includeLastRotation) {
            writeLookVector(lastYaw, lastPitch, directions, 6);
        }
        return directions;

    }

    private static double[] writeLookVector(final float yaw, final float pitch, final double[] out, final int offset) {

        final double pitchRadians = Math.toRadians(pitch);
        final double yawRadians = Math.toRadians(-yaw);
        final double pitchCos = Math.cos(pitchRadians);
        final double x = Math.sin(yawRadians) * pitchCos;
        final double y = -Math.sin(pitchRadians);
        final double z = Math.cos(yawRadians) * pitchCos;
        final double length = Math.sqrt(x * x + y * y + z * z);
        final double scale = length == 0.0 ? 1.0 : 1.0 / length;
        out[offset] = x * scale;
        out[offset + 1] = y * scale;
        out[offset + 2] = z * scale;
        return out;

    }

    private static double minAngleToBox(final AimSnapshot snapshot, final double[] directions) {

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
            for (int offset = 0; offset < directions.length; offset += 3) {
                final double dot = directions[offset] * (dx / length)
                        + directions[offset + 1] * (dy / length)
                        + directions[offset + 2] * (dz / length);
                if (dot > maxDot) {
                    maxDot = dot;
                }
            }
        }
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, maxDot))));

    }

    private static double pointX(final AimSnapshot snapshot, final int point) {

        if (point == 0) {
            return (snapshot.targetMinX() + snapshot.targetMaxX()) * 0.5;
        }
        return ((point - 1) & 1) == 0 ? snapshot.targetMinX() : snapshot.targetMaxX();

    }

    private static double pointY(final AimSnapshot snapshot, final int point) {

        if (point == 0) {
            return (snapshot.targetMinY() + snapshot.targetMaxY()) * 0.5;
        }
        return ((point - 1) & 2) == 0 ? snapshot.targetMinY() : snapshot.targetMaxY();

    }

    private static double pointZ(final AimSnapshot snapshot, final int point) {

        if (point == 0) {
            return (snapshot.targetMinZ() + snapshot.targetMaxZ()) * 0.5;
        }
        return ((point - 1) & 4) == 0 ? snapshot.targetMinZ() : snapshot.targetMaxZ();

    }

}
