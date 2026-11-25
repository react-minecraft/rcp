package com.g4vrk.react.inference.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies device-link polling bounds and transient error retry rules.
 */
final class AccountClientTest {

    @Test
    void clampsPollIntervalAndExpiresIn() {

        assertEquals(1L, AccountClient.clampInterval(-5L));
        assertEquals(1L, AccountClient.clampInterval(0L));
        assertEquals(15L, AccountClient.clampInterval(15L));
        assertEquals(30L, AccountClient.clampInterval(300L));
        assertEquals(30L, AccountClient.clampInterval(Long.MAX_VALUE));

        assertEquals(1L, AccountClient.clampExpiresIn(0L));
        assertEquals(600L, AccountClient.clampExpiresIn(600L));
        assertEquals(3600L, AccountClient.clampExpiresIn(999_999L));

    }

    @Test
    void retriesTransient429And5xxUpToLimit() {

        assertTrue(AccountClient.isRetryablePollStatus(429));
        assertTrue(AccountClient.isRetryablePollStatus(500));
        assertTrue(AccountClient.isRetryablePollStatus(503));
        assertFalse(AccountClient.isRetryablePollStatus(400));
        assertFalse(AccountClient.isRetryablePollStatus(404));

        assertEquals(6L, AccountClient.retryDelaySeconds(429, 3L, 0));
        assertEquals(30L, AccountClient.retryDelaySeconds(429, 20L, 1));
        assertEquals(3L, AccountClient.retryDelaySeconds(503, 3L, 2));
        assertEquals(-1L, AccountClient.retryDelaySeconds(503, 3L, 3));
        assertEquals(-1L, AccountClient.retryDelaySeconds(400, 3L, 0));

    }

}
