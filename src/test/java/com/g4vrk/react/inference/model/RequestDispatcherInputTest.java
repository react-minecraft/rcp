package com.g4vrk.react.inference.model;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestDispatcherInputTest {

    @Test
    void waitingInputIsRejectedAfterReset() throws Exception {

        final PlayerInputGuard guards = new PlayerInputGuard();
        final UUID player = UUID.randomUUID();
        final CountDownLatch started = new CountDownLatch(1);
        final CompletableFuture<Boolean> accepted;
        synchronized (guards.lock(player)) {
            accepted = CompletableFuture.supplyAsync(() -> {
                final long generation = guards.generation(player);
                started.countDown();
                synchronized (guards.lock(player)) {
                    return generation == guards.generation(player);
                }
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            guards.invalidate(player);
        }
        assertFalse(accepted.get(5, TimeUnit.SECONDS));

    }

}
