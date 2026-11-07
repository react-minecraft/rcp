package com.g4vrk.react.inference.link;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.Signature;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LinkTest {

    @TempDir
    Path directory;

    @Test
    void messageMatchesTheSite() {
        assertEquals("POST\n/api/v1/auth/check\nabc\n1700000000\n"
                        + "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                RequestSigner.message("post", "/api/v1/auth/check", "abc", 1700000000L, new byte[0]));
    }

    @Test
    void signatureVerifiesWithThePublicKey() throws Exception {
        final KeyPair keys = RequestSigner.newKeyPair();
        final String message = RequestSigner.message("GET", "/api/v1/auth/check", "abc", 1L, new byte[0]);
        final Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(keys.getPublic());
        verifier.update(message.getBytes(StandardCharsets.UTF_8));

        assertTrue(verifier.verify(Base64.getDecoder().decode(RequestSigner.sign(keys.getPrivate(), message))));
        assertEquals(32, Base64.getDecoder().decode(RequestSigner.publicKey(keys.getPublic())).length);
    }

    @Test
    void storedCredentialOpensOnlyWithTheSameFingerprint() throws Exception {
        final Path file = directory.resolve(".link");
        final KeyPair keys = RequestSigner.newKeyPair();
        new LinkStore(file, "first").save(new Credential("rx_secret", keys));

        final Credential loaded = new LinkStore(file, "first").load();
        assertNotNull(loaded);
        assertEquals("rx_secret", loaded.accessToken());
        assertNotNull(loaded.keys());
        assertArrayEquals(keys.getPrivate().getEncoded(), loaded.keys().getPrivate().getEncoded());
        assertNull(new LinkStore(file, "second").load());
        assertFalse(Files.readString(file).contains("rx_secret"));
    }

    @Test
    void tokenWithoutKeysOpensUnsigned() throws Exception {
        final Path file = directory.resolve(".link");
        new LinkStore(file, "first").save(Credential.unsigned("rx_old"));

        final Credential loaded = new LinkStore(file, "first").load();
        assertNotNull(loaded);
        assertEquals("rx_old", loaded.accessToken());
        assertNull(loaded.keys());
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
        assertNotEquals(ServerFingerprint.hash("a"), ServerFingerprint.hash("b"));
    }

}
