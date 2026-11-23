package com.g4vrk.react.inference.link;

import org.jetbrains.annotations.NotNull;

/**
 * Encodes binary hashes as lowercase hexadecimal for Java 16 runtimes.
 */
final class HexCodec {

    private static final char[] DIGITS = "0123456789abcdef".toCharArray();

    private HexCodec() {
    }

    static @NotNull String encode(final byte @NotNull [] bytes) {

        final char[] encoded = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            final int value = Byte.toUnsignedInt(bytes[index]);
            encoded[index * 2] = DIGITS[value >>> 4];
            encoded[index * 2 + 1] = DIGITS[value & 0x0F];
        }
        return new String(encoded);

    }

}
