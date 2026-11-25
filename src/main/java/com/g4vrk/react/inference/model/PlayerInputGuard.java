package com.g4vrk.react.inference.model;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.NotNull;

/**
 * Fixed-size locks and generations for coordinating input with player resets.
 */
final class PlayerInputGuard {

    private final Object[] locks = new Object[64];
    private final AtomicLong[] generations = new AtomicLong[64];

    PlayerInputGuard() {

        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
            generations[i] = new AtomicLong();
        }

    }

    @NotNull Object lock(final @NotNull UUID player) {

        return locks[index(player)];

    }

    long generation(final @NotNull UUID player) {

        return generations[index(player)].get();

    }

    void invalidate(final @NotNull UUID player) {

        generations[index(player)].incrementAndGet();

    }

    void invalidateAll() {

        for (final AtomicLong generation : generations) {
            generation.incrementAndGet();
        }

    }

    private int index(final @NotNull UUID player) {

        return (player.hashCode() & Integer.MAX_VALUE) % locks.length;

    }

}
