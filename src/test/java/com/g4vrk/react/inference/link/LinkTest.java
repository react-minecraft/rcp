package com.g4vrk.react.inference.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LinkTest {

    @TempDir
    Path directory;

    @Test
    void signatureMatchesTheSite() {
        assertEquals("c2d97bef7ab430e22342464392f9738e9019c8e66fc57c9343a97e53bf55f00e", RequestSigner.sign("rx_test", "abc", 1700000000L));
    }

    @Test
    void storedKeyOpensOnlyWithTheSameFingerprint() throws Exception {
        final Path file = directory.resolve(".link");
        new LinkStore(file, "first").save("rx_secret");

        assertEquals("rx_secret", new LinkStore(file, "first").load());
        assertNull(new LinkStore(file, "second").load());
        assertNotEquals("rx_secret", Files.readString(file));
    }

    @Test
    void missingOrBrokenFileIsNotLinked() throws Exception {
        final Path file = directory.resolve(".link");
        assertNull(new LinkStore(file, "first").load());
        Files.writeString(file, "not base64 !");
        assertNull(new LinkStore(file, "first").load());
    }

    @Test
    void fingerprintIsHexSha256() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ServerFingerprint.hash("abc"));
    }

}
