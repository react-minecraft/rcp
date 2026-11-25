package com.g4vrk.react.inference.model.vector.service;

import com.g4vrk.react.api.event.Event;
import com.g4vrk.react.api.event.rcp.ModelFrameEvent;
import com.g4vrk.react.api.extension.listener.ListenerRegistry;
import com.g4vrk.react.api.platform.PlatformServer;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.api.rcp.environment.RCPEnvironment;
import com.g4vrk.react.api.rcp.player.RCPPlayer;
import com.g4vrk.react.api.rcp.player.RotationSample;
import com.g4vrk.react.inference.config.ActionsConfig;
import com.g4vrk.react.inference.model.RequestDispatcher;
import com.g4vrk.react.inference.model.vector.session.AimSession;
import com.g4vrk.react.inference.server.InferenceServer;
import com.g4vrk.schedula.task.Task;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link AimStreamService} combatTicks contract, generation guard, event flags, and shutdown cleanup.
 */
final class AimStreamServiceTest {

    @Test
    void rotationDoesNotExtendCombatTicksAndGenerationAndCombatActiveArePropagated() throws Exception {

        final List<Runnable> scheduled = new ArrayList<>();
        final List<Event> posted = new ArrayList<>();
        final AtomicBoolean playerInCombat = new AtomicBoolean(true);
        final UUID playerId = UUID.randomUUID();
        final RCPEnvironment environment = environmentProxy(scheduled, posted);
        final InferenceServer server = new InferenceServer(
                NOPLogger.NOP_LOGGER,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                null,
                "fp",
                "2.0.0",
                "2.0.0",
                new ActionsConfig.ReasonSet("nl", "rep", "exp", "http")
        );
        final RequestDispatcher dispatcher = new RequestDispatcher(environment, server);
        final AtomicInteger frameCallbacks = new AtomicInteger();
        final AimStreamService service = new AimStreamService(
                environment,
                dispatcher,
                20L,
                (p, session) -> frameCallbacks.incrementAndGet()
        );
        final RCPPlayer player = playerProxy(playerId, playerInCombat);
        final float[] sample = new float[RotationSample.STRIDE];
        sample[0] = 5.0F;
        sample[1] = 10.0F;

        service.onRotation(player, sample);
        assertEquals(1, scheduled.size());
        final AimSession session = service.findPlayerData(playerId);
        assertFalse(session.combatActivity.isActive());

        scheduled.remove(0).run();
        assertEquals(0, frameCallbacks.get());
        assertTrue(posted.isEmpty());

        service.onAttack(player, 42);
        assertTrue(session.combatActivity.isActive());

        service.onRotation(player, sample);
        playerInCombat.set(false);
        assertEquals(1, scheduled.size());
        scheduled.remove(0).run();
        assertEquals(1, frameCallbacks.get());
        assertEquals(1, posted.size());
        final ModelFrameEvent frameEvent = (ModelFrameEvent) posted.get(0);
        assertTrue(frameEvent.inCombat());

        playerInCombat.set(true);
        service.onRotation(player, sample);
        assertEquals(1, scheduled.size());
        dispatcher.invalidateInput(playerId);
        scheduled.remove(0).run();
        assertEquals(1, frameCallbacks.get());

        service.subscribe(p -> true, (p, frame) -> { });
        service.shutdown();
        dispatcher.shutdown();
        server.shutdown();
        assertNull(service.findPlayerData(playerId));

    }

    private static RCPEnvironment environmentProxy(
            final List<Runnable> scheduled,
            final List<Event> posted
    ) {

        final ListenerRegistry listeners = (ListenerRegistry) Proxy.newProxyInstance(
                ListenerRegistry.class.getClassLoader(),
                new Class<?>[] {ListenerRegistry.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("post") && args != null && args.length == 1) {
                        posted.add((Event) args[0]);
                    }
                    return null;
                }
        );
        final Task dummyTask = (Task) Proxy.newProxyInstance(
                Task.class.getClassLoader(),
                new Class<?>[] {Task.class},
                (proxy, method, args) -> method.getReturnType().equals(boolean.class) ? false : null
        );
        @SuppressWarnings("unchecked")
        final TaskScheduler<PlatformPlayer> scheduler = (TaskScheduler<PlatformPlayer>) Proxy.newProxyInstance(
                TaskScheduler.class.getClassLoader(),
                new Class<?>[] {TaskScheduler.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("scheduleEntity") && args != null && args.length >= 2 && args[1] instanceof Runnable task) {
                        scheduled.add(task);
                        return dummyTask;
                    }
                    return dummyTask;
                }
        );
        final PlatformServer platform = (PlatformServer) Proxy.newProxyInstance(
                PlatformServer.class.getClassLoader(),
                new Class<?>[] {PlatformServer.class},
                (proxy, method, args) -> null
        );
        return (RCPEnvironment) Proxy.newProxyInstance(
                RCPEnvironment.class.getClassLoader(),
                new Class<?>[] {RCPEnvironment.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "listeners" -> listeners;
                    case "scheduler" -> scheduler;
                    case "platform" -> platform;
                    case "logger" -> NOPLogger.NOP_LOGGER;
                    default -> null;
                }
        );

    }

    private static RCPPlayer playerProxy(
            final UUID uuid,
            final AtomicBoolean inCombat
    ) {

        final PlatformPlayer platformPlayer = (PlatformPlayer) Proxy.newProxyInstance(
                PlatformPlayer.class.getClassLoader(),
                new Class<?>[] {PlatformPlayer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "uniqueId" -> uuid;
                    case "online" -> true;
                    case "name" -> "Steve";
                    default -> null;
                }
        );
        return (RCPPlayer) Proxy.newProxyInstance(
                RCPPlayer.class.getClassLoader(),
                new Class<?>[] {RCPPlayer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "uniqueId" -> uuid;
                    case "name" -> "Steve";
                    case "platform" -> platformPlayer;
                    case "inCombat" -> inCombat.get();
                    case "checked" -> true;
                    default -> null;
                }
        );

    }

}
