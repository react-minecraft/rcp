package com.g4vrk.react.inference.model.vector.service;

import com.g4vrk.react.api.event.rcp.ModelFrameEvent;
import com.g4vrk.react.api.extension.ListenerRegistry;
import com.g4vrk.react.api.rcp.environment.RCPEnvironment;
import com.g4vrk.react.api.rcp.player.CombatTarget;
import com.g4vrk.react.api.rcp.player.RCPPlayer;
import com.g4vrk.react.api.rcp.player.registry.RCPPlayerRegistry;
import com.g4vrk.react.inference.model.vector.model.AimFrame;
import com.g4vrk.react.inference.model.vector.session.AimSession;
import com.g4vrk.react.inference.model.vector.session.AimState;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Turns attack and rotation packets into Vector Preview aim frames.
 *
 * <p>Frames are captured on the player's entity thread; a player counts as fighting for
 * {@code combat-ticks} after each attack.</p>
 */
public final class AimStreamService {

    private static final List<String> DATASET_COLUMNS = List.of(AimFrame.csvHeader().substring("is_cheating,".length()).split(","));
    private static final TickSchedule CAPTURE_SCHEDULE = TickSchedule.instant();
    private final ListenerRegistry events;
    private final RCPPlayerRegistry players;
    private final Scheduler scheduler;
    private final Map<UUID, AimSession> playerData = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private final BiConsumer<RCPPlayer, AimSession> onFrame;
    private volatile long combatTicks;

    public AimStreamService(
            final @NotNull RCPEnvironment environment,
            final long combatTicks,
            final @NotNull BiConsumer<RCPPlayer, AimSession> onFrame
    ) {

        this.events = environment.listeners();
        this.players = environment.players();
        this.scheduler = environment.scheduler();
        this.combatTicks = Math.max(1L, combatTicks);
        this.onFrame = onFrame;

    }

    public void combatTicks(final long combatTicks) {

        this.combatTicks = Math.max(1L, combatTicks);

    }

    public @NotNull AimSession claimPlayerData(final @NotNull UUID uuid) {

        return playerData.computeIfAbsent(uuid, ignored -> new AimSession());

    }

    public void removePlayerData(final @NotNull UUID uuid) {

        final AimSession removed = playerData.remove(uuid);
        if (removed != null) {
            removed.reset();
        }

    }

    public void onPacketBeforeJoin(
            final @NotNull UUID uuid,
            final @Nullable Player bukkitPlayer,
            final @NotNull PacketReceiveEvent event
    ) {

        onPacket(uuid, bukkitPlayer, claimPlayerData(uuid), event);

    }

    public void onPacket(final @NotNull RCPPlayer player, final @NotNull PacketReceiveEvent event) {

        onPacket(player.uniqueId(), player.bukkit(), claimPlayerData(player.uniqueId()), event);

    }

    public @NotNull AutoCloseable subscribe(
            final @NotNull Predicate<RCPPlayer> interested,
            final @NotNull BiConsumer<RCPPlayer, AimFrame> consumer
    ) {

        final Subscription subscription = new Subscription(interested, consumer);
        subscriptions.add(subscription);
        return () -> subscriptions.remove(subscription);

    }

    private void onPacket(
            final @NotNull UUID uuid,
            final @Nullable Player bukkitPlayer,
            final @NotNull AimSession data,
            final @NotNull PacketReceiveEvent event
    ) {

        synchronized (data) {
            processPacket(uuid, bukkitPlayer, data, event);
        }

    }

    private void processPacket(
            final @NotNull UUID uuid,
            final @Nullable Player bukkitPlayer,
            final @NotNull AimSession data,
            final @NotNull PacketReceiveEvent event
    ) {

        final AimState state = data.activate();
        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            onInteract(data, state, event);
            return;
        }
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            return;
        }
        final WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
        final boolean rotationChanged = flying.hasRotationChanged();
        state.onFlying(rotationChanged);
        if (!rotationChanged || bukkitPlayer == null) {
            return;
        }
        final float yaw = flying.getLocation().getYaw();
        final float pitch = flying.getLocation().getPitch();
        scheduler.scheduleEntity(bukkitPlayer, () -> capture(uuid, bukkitPlayer, data, state, yaw, pitch), CAPTURE_SCHEDULE);

    }

    private void onInteract(
            final @NotNull AimSession data,
            final @NotNull AimState state,
            final @NotNull PacketReceiveEvent event
    ) {

        final WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
            return;
        }
        final RCPPlayer target = players.byEntityId(interact.getEntityId());
        if (target != null) {
            state.onAttack(target.uniqueId());
        } else {
            state.onAttackEntity(interact.getEntityId());
        }
        if (!data.combatActivity.isActive()) {
            data.clearHistory();
        }
        data.combatActivity.extend(combatTicks);

    }

    private void capture(
            final @NotNull UUID uuid,
            final @NotNull Player bukkitPlayer,
            final @NotNull AimSession data,
            final @NotNull AimState state,
            final float yaw,
            final float pitch
    ) {

        final RCPPlayer player;
        final AimFrame frame;
        final boolean inCombat;
        synchronized (data) {
            player = players.get(uuid);
            if (!bukkitPlayer.isOnline() || playerData.get(uuid) != data || !data.isCurrent(state)) {
                return;
            }
            resolvePendingTarget(bukkitPlayer, player, state);
            frame = state.capture(bukkitPlayer, resolveTarget(data, state), yaw, pitch);
            if (player == null) {
                return;
            }
            inCombat = data.combatActivity.isActive();
            recordFrame(player, data, frame);
        }
        if (data.isCurrent(state)) {
            events.post(new ModelFrameEvent(
                    "vector",
                    player.uniqueId(),
                    DATASET_COLUMNS,
                    frame.datasetFeatures(),
                    inCombat
            ));
            notifySubscribers(player, frame);
        }

    }

    private void resolvePendingTarget(
            final @NotNull Player bukkitPlayer,
            final @Nullable RCPPlayer player,
            final @NotNull AimState state
    ) {

        final int pendingTarget = state.takePendingTargetEntityId();
        if (pendingTarget == -1) {
            return;
        }
        final Entity attacked = findSupportedEntity(bukkitPlayer, pendingTarget);
        state.onAttack(attacked == null ? null : attacked.getUniqueId());
        if (player != null && CombatTarget.isMannequin(attacked)) {
            player.enterCombat();
        }

    }

    private static @Nullable Entity findSupportedEntity(final @NotNull Player bukkitPlayer, final int entityId) {

        for (final Entity entity : bukkitPlayer.getWorld().getEntities()) {
            if (entity.getEntityId() == entityId && CombatTarget.isSupported(entity)) {
                return entity;
            }
        }
        return null;

    }

    private @Nullable Entity resolveTarget(final @NotNull AimSession data, final @NotNull AimState state) {

        final UUID targetId = state.targetId();
        if (targetId == null || !data.combatActivity.isActive()) {
            return null;
        }
        final RCPPlayer targetPlayer = players.get(targetId);
        final Entity target = targetPlayer != null ? targetPlayer.bukkit() : Bukkit.getEntity(targetId);
        return target != null && CombatTarget.isSupported(target) ? target : null;

    }

    private void recordFrame(
            final @NotNull RCPPlayer player,
            final @NotNull AimSession data,
            final @NotNull AimFrame frame
    ) {

        if (!data.combatActivity.isActive()) {
            data.clearHistory();
            return;
        }
        data.push(frame);
        onFrame.accept(player, data);

    }

    private void notifySubscribers(final @NotNull RCPPlayer player, final @NotNull AimFrame frame) {

        for (final Subscription subscription : subscriptions) {
            if (subscription.interested().test(player)) {
                subscription.consumer().accept(player, frame);
            }
        }

    }

    /**
     * A frame consumer and the players it records.
     */
    private record Subscription(
            @NotNull Predicate<RCPPlayer> interested,
            @NotNull BiConsumer<RCPPlayer, AimFrame> consumer
    ) {

    }

}
