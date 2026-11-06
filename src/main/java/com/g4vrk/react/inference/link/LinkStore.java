package com.g4vrk.react.inference.link;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps the server's api key in {@code .link}, encrypted with AES-GCM under a key derived from the fingerprint.
 * <p>
 * The file holds base64 of a 12 byte IV followed by the ciphertext, so a copy on another machine does not open.
 */
public final class LinkStore {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Path file;
    private final SecretKeySpec key;

    public LinkStore(final @NotNull Path file, final @NotNull String fingerprint) {
        this.file = file;
        this.key = new SecretKeySpec(ServerFingerprint.sha256(fingerprint + "link"), "AES");
    }

    /**
     * @return the stored key, or null when there is none or it was saved on another machine
     */
    public @Nullable String load() throws IOException {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        final byte[] data;
        try {
            data = Base64.getDecoder().decode(Files.readString(file, StandardCharsets.US_ASCII).trim());
        } catch (final IllegalArgumentException exception) {
            return null;
        }
        if (data.length <= IV_LENGTH) {
            return null;
        }
        try {
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_LENGTH));
            return new String(cipher.doFinal(data, IV_LENGTH, data.length - IV_LENGTH), StandardCharsets.UTF_8);
        } catch (final GeneralSecurityException exception) {
            return null;
        }
    }

    public void save(final @NotNull String apiKey) throws IOException {
        final byte[] iv = new byte[IV_LENGTH];
        RANDOM.nextBytes(iv);
        final byte[] encrypted;
        try {
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            encrypted = cipher.doFinal(apiKey.getBytes(StandardCharsets.UTF_8));
        } catch (final GeneralSecurityException exception) {
            throw new IOException("Could not encrypt the link", exception);
        }
        final byte[] data = Arrays.copyOf(iv, IV_LENGTH + encrypted.length);
        System.arraycopy(encrypted, 0, data, IV_LENGTH, encrypted.length);

        Files.createDirectories(file.toAbsolutePath().getParent());
        final Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, Base64.getEncoder().encodeToString(data), StandardCharsets.US_ASCII);
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

}
