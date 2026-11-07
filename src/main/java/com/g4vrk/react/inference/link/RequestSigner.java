package com.g4vrk.react.inference.link;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

/**
 * Signs account requests with the Ed25519 key made for this link, so a copied access token is refused by the site.
 * <p>
 * The signed message is {@code METHOD \n path \n fingerprint \n unix seconds \n hex sha256(body)};
 * the signature and the public key travel as base64.
 */
public final class RequestSigner {

    public static final String FINGERPRINT_HEADER = "X-React-Fingerprint";
    public static final String TIMESTAMP_HEADER = "X-React-Timestamp";
    public static final String SIGNATURE_HEADER = "X-React-Signature";

    private static final String ALGORITHM = "Ed25519";
    private static final int RAW_PUBLIC_KEY_LENGTH = 32;

    private RequestSigner() {
    }

    public static @NotNull KeyPair newKeyPair() {
        try {
            return KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair();
        } catch (final GeneralSecurityException exception) {
            throw new IllegalStateException("Ed25519 is not available", exception);
        }
    }

    /**
     * The raw 32 bytes of the key in base64, the form the site stores.
     */
    public static @NotNull String publicKey(final @NotNull PublicKey key) {
        final byte[] encoded = key.getEncoded();
        final byte[] raw = Arrays.copyOfRange(encoded, encoded.length - RAW_PUBLIC_KEY_LENGTH, encoded.length);
        return Base64.getEncoder().encodeToString(raw);
    }

    public static @NotNull String message(
            final @NotNull String method,
            final @NotNull String path,
            final @NotNull String fingerprint,
            final long timestampSeconds,
            final byte @NotNull [] body
    ) {
        final String digest = HexFormat.of().formatHex(ServerFingerprint.sha256(body));
        return method.toUpperCase(Locale.ROOT) + "\n" + path + "\n" + fingerprint + "\n" + timestampSeconds + "\n" + digest;
    }

    public static @NotNull String sign(final @NotNull PrivateKey key, final @NotNull String message) {
        try {
            final Signature signature = Signature.getInstance(ALGORITHM);
            signature.initSign(key);
            signature.update(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (final GeneralSecurityException exception) {
            throw new IllegalStateException("Could not sign the request", exception);
        }
    }

}
