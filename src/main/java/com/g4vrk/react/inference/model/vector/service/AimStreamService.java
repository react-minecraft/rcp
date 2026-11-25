package com.g4vrk.react.inference.model.vector.service;

import com.g4vrk.react.api.event.rcp.ModelFrameEvent;
import com.g4vrk.react.api.extension.listener.ListenerRegistry;
import com.g4vrk.react.api.platform.AimSnapshot;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.api.rcp.environment.RCPEnvironment;
import com.g4vrk.react.api.rcp.player.RCPPlayer;
import com.g4vrk.react.api.rcp.player.RotationSample;
import com.g4vrk.react.inference.model.RequestDispatcher;
import com.g4vrk.react.inference.model.vector.model.AimFrame;
import com.g4vrk.react.inference.model.vector.session.AimSession;
import com.g4vrk.react.inference.model.vector.session.AimState;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Turns React's platform-neutral attack and rotation callbacks into Vector aim frames.
 */
public final class AimStreamService {

    private static final List<String> DATASET_COLUMNS = List.of(AimFrame.csvHeader().substring("is_cheating,".length()).split(","));
    private static final TickSchedule CAPTURE_SCHEDULE = TickSchedule.instant();

    private final RCPEnvironment environment;
    private final RequestDispatcher dispatcher;
    private final ListenerRegistry events;
    private final TaskScheduler<PlatformPlayer> scheduler;
    private final Map<UUID, AimSession> playerData = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private final BiConsumer<RCPPlayer, AimSession> onFrame;
    private volatile long combatTicks;
    private volatile boolean closed;

    public AimStreamService(
            final @NotNull RCPEnvironment environment,
            final @NotNull RequestDispatcher dispatcher,
            final long combatTicks,
            final @NotNull BiConsumer<RCPPlayer, AimSession> onFrame
    ) {

        this.environment = environment;
        this.dispatcher = dispatcher;
        this.events = environment.listeners();
        this.scheduler = environment.scheduler();
        this.combatTicks = Math.max(1L, combatTicks);
        this.onFrame = onFrame;

    }

    public void combatTicks(final long combatTicks) {

        this.combatTicks = Math.max(1L, combatTicks);

    }

    public @Nullable AimSession findPlayerData(final @NotNull UUID uuid) {

        return playerData.get(uuid);

    }

    public @NotNull AimSession claimPlayerData(final @NotNull UUID uuid) {

        return playerData.computeIfAbsent(uuid, ignored -> new AimSession());

    }

    public void removePlayerData(final @NotNull UUID uuid) {

        synchronized (dispatcher.playerLock(uuid)) {
            final AimSession removed = playerData.remove(uuid);
            if (removed != null) {
                removed.reset();
            }
        }

    }

    public void onAttack(
            final @NotNull RCPPlayer player,
            final int targetEntityId
    ) {

        if (closed) {
            return;
        }
        final UUID uuid = player.uniqueId();
        final long generation = dispatcher.inputGeneration(uuid);
        synchronized (dispatcher.playerLock(uuid)) {
            if (closed || generation != dispatcher.inputGeneration(uuid)) {
                return;
            }
            final AimSession data = claimPlayerData(uuid);
            synchronized (data) {
                final AimState state = data.activate();
                if (!data.combatActivity.isActive()) {
                    data.clearHistory();
                }
                state.onAttack(targetEntityId);
                data.combatActivity.extend(combatTicks);
            }
        }

    }

    public void onRotation(
            final @NotNull RCPPlayer player,
            final float @NotNull [] sample
    ) {

        if (closed || !player.inCombat()) {
            return;
        }
        final UUID uuid = player.uniqueId();
        final long generation = dispatcher.inputGeneration(uuid);
        final AimSession data;
        final AimState state;
        synchronized (dispatcher.playerLock(uuid)) {
            if (closed || generation != dispatcher.inputGeneration(uuid)) {
                return;
            }
            data = claimPlayerData(uuid);
            synchronized (data) {
                state = data.activate();
            }
        }
        final float yaw = RotationSample.yaw(sample, 0);
        final float pitch = RotationSample.pitch(sample, 0);
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            return;
        }
        scheduler.scheduleEntity(
                player.platform(),
                () -> capture(player, data, state, generation, yaw, pitch),
                CAPTURE_SCHEDULE
        );

    }

    public @NotNull AutoCloseable subscribe(
            final @NotNull Predicate<RCPPlayer> interested,
            final @NotNull BiConsumer<RCPPlayer, AimFrame> consumer
    ) {

        if (closed) {
            return () -> { };
        }
        final Subscription subscription = new Subscription(interested, consumer);
        subscriptions.add(subscription);
        if (closed) {
            subscriptions.remove(subscription);
            return () -> { };
        }
        return () -> subscriptions.remove(subscription);

    }

    public void shutdown() {

        closed = true;
        subscriptions.clear();
        for (final UUID uuid : List.copyOf(playerData.keySet())) {
            synchronized (dispatcher.playerLock(uuid)) {
                dispatcher.invalidateInput(uuid);
                final AimSession removed = playerData.remove(uuid);
                if (removed != null) {
                    removed.reset();
                }
            }
        }
        playerData.clear();

    }

    private void capture(
            final @NotNull RCPPlayer player,
            final @NotNull AimSession data,
            final @NotNull AimState state,
            final long generation,
            final float yaw,
            final float pitch
    ) {

        if (closed) {
            return;
        }
        final UUID uuid = player.uniqueId();
        final AimFrame frame;
        final boolean combatActive;
        synchronized (dispatcher.playerLock(uuid)) {
            synchronized (data) {
                if (closed
                        || generation != dispatcher.inputGeneration(uuid)
                        || !player.platform().online()
                        || playerData.get(uuid) != data
                        || !data.isCurrent(state)) {
                    return;
                }
                final AimSnapshot snapshot = environment.platform().aimSnapshot(player.platform(), state.targetEntityId());
                frame = state.capture(snapshot, yaw, pitch);
                combatActive = data.combatActivity.isActive();
                if (!combatActive) {
                    data.clearHistory();
                    return;
                }
                data.push(frame);
                onFrame.accept(player, data);
            }
        }
        if (closed) {
            return;
        }
        events.post(new ModelFrameEvent(
                "vector",
                player.uniqueId(),
                DATASET_COLUMNS,
                frame.datasetFeatures(),
                combatActive
        ));
        notifySubscribers(player, frame);

    }

    private void notifySubscribers(
            final @NotNull RCPPlayer player,
            final @NotNull AimFrame frame
    ) {

        for (final Subscription subscription : subscriptions) {
            if (subscription.interested().test(player)) {
                subscription.consumer().accept(player, frame);
            }
        }

    }

    private record Subscription(
            @NotNull Predicate<RCPPlayer> interested,
            @NotNull BiConsumer<RCPPlayer, AimFrame> consumer
    ) {

    }

}
