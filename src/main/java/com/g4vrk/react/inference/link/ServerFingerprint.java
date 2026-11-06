package com.g4vrk.react.inference.link;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.bukkit.Server;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

/**
 * Identifies this server installation: SHA-256 of the main world UUID, the server port and the machine id.
 * <p>
 * The machine id comes from {@code /etc/machine-id} or {@code /var/lib/dbus/machine-id}, and the host name
 * stands in for it where neither exists.
 */
public final class ServerFingerprint {

    private static final List<Path> MACHINE_ID_FILES = List.of(
            Path.of("/etc/machine-id"),
            Path.of("/var/lib/dbus/machine-id")
    );

    private ServerFingerprint() {
    }

    public static @NotNull String of(final @NotNull Server server) {
        final List<World> worlds = server.getWorlds();
        final String world = worlds.isEmpty() ? "" : worlds.get(0).getUID().toString();
        return hash(world + "\n" + server.getPort() + "\n" + machineId());
    }

    static @NotNull String hash(final @NotNull String value) {
        return HexFormat.of().formatHex(sha256(value));
    }

    static byte[] sha256(final @NotNull String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static @NotNull String machineId() {
        for (final Path file : MACHINE_ID_FILES) {
            try {
                final String id = Files.readString(file).trim();
                if (!id.isEmpty()) {
                    return id;
                }
            } catch (final IOException | SecurityException ignored) {
                // The next source is tried.
            }
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (final IOException exception) {
            return "unknown";
        }
    }

}
