package com.g4vrk.react.inference.link;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.jetbrains.annotations.NotNull;

/**
 * SHA-256 helpers used by linked-server credential storage and request signing.
 */
public final class ServerFingerprint {

    private ServerFingerprint() {
    }

    static @NotNull String hash(final @NotNull String value) {

        return HexCodec.encode(sha256(value));

    }

    static byte[] sha256(final @NotNull String value) {

        return sha256(value.getBytes(StandardCharsets.UTF_8));

    }

    static byte[] sha256(final byte @NotNull [] value) {

        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }

    }

}
