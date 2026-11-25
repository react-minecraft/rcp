package com.g4vrk.react.inference.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies constructor bounds of {@link LimitedBodyHandler}.
 */
final class LimitedBodyHandlerTest {

    @Test
    void rejectsNonPositiveMaximumBytes() {

        assertThrows(IllegalArgumentException.class, () -> new LimitedBodyHandler(0));
        assertThrows(IllegalArgumentException.class, () -> new LimitedBodyHandler(-128));
        assertDoesNotThrow(() -> new LimitedBodyHandler(1024));

    }

}
