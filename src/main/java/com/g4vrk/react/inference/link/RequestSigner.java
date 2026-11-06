package com.g4vrk.react.inference.link;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jetbrains.annotations.NotNull;

/**
 * Signs account requests so a key copied to another machine is refused by the site.
 * <p>
 * The signature is hex HMAC-SHA256 keyed with the api key over {@code fingerprint + "\n" + timestamp}.
 */
public final class RequestSigner {

    public static final String FINGERPRINT_HEADER = "X-React-Fingerprint";
    public static final String TIMESTAMP_HEADER = "X-React-Timestamp";
    public static final String SIGNATURE_HEADER = "X-React-Signature";

    private RequestSigner() {
    }

    public static @NotNull String sign(
            final @NotNull String apiKey,
            final @NotNull String fingerprint,
            final long timestampSeconds
    ) {
        try {
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            final byte[] digest = mac.doFinal((fingerprint + "\n" + timestampSeconds).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (final GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is not available", exception);
        }
    }

}
