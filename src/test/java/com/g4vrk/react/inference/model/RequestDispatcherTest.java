package com.g4vrk.react.inference.model;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies error body sanitization in {@link RequestDispatcher}.
 */
final class RequestDispatcherTest {

    @Test
    void sanitizeErrorBodyStripsControlCharactersAndCapsLength() {

        final byte[] injected = "error\r\nline\t\u001B[31mred\u0000end".getBytes(StandardCharsets.UTF_8);
        final String clean = RequestDispatcher.sanitizeErrorBody(injected);
        assertFalse(clean.contains("\n"));
        assertFalse(clean.contains("\r"));
        assertFalse(clean.contains("\u001B"));
        assertFalse(clean.contains("\u0000"));
        assertEquals("error  line  [31mred end", clean);

        final byte[] oversized = "a".repeat(1000).getBytes(StandardCharsets.UTF_8);
        final String truncated = RequestDispatcher.sanitizeErrorBody(oversized);
        assertTrue(truncated.endsWith("..."));
        assertEquals(259, truncated.length());

    }

}
