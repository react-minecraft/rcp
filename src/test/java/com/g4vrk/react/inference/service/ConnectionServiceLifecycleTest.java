package com.g4vrk.react.inference.service;

import com.g4vrk.react.api.extension.listener.ListenerRegistry;
import com.g4vrk.react.api.platform.PlatformServer;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.api.rcp.environment.RCPEnvironment;
import com.g4vrk.react.api.rcp.meta.RCPMetadata;
import com.g4vrk.react.api.rcp.player.RCPPlayer;
import com.g4vrk.react.inference.config.ActionsConfig;
import com.g4vrk.react.inference.config.ConnectionConfig;
import com.g4vrk.react.inference.config.FlowerConfig;
import com.g4vrk.react.inference.config.InferenceConfigRegistry;
import com.g4vrk.react.inference.config.VectorConfig;
import com.g4vrk.react.inference.model.ModelId;
import com.g4vrk.react.inference.model.vector.VectorModel;
import com.g4vrk.schedula.task.Task;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies {@link ConnectionService} does not schedule status updates after shutdown and
 * {@link ModelService#shutdown()} cleans up state.
 */
final class ConnectionServiceLifecycleTest {

    @Test
    void connectionServiceSkipsSchedulingStatusWhenClosedAndModelServiceShutdownClearsState(
            final @TempDir Path dir
    ) throws Exception {

        final AtomicInteger scheduledCount = new AtomicInteger();
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
                    scheduledCount.incrementAndGet();
                    return dummyTask;
                }
        );
        final PlatformServer platform = (PlatformServer) Proxy.newProxyInstance(
                PlatformServer.class.getClassLoader(),
                new Class<?>[] {PlatformServer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "serverFingerprint" -> "fp";
                    case "dataDirectory" -> dir;
                    case "pluginVersion" -> "2.0.0";
                    default -> null;
                }
        );
        final RCPMetadata metadata = new RCPMetadata(
                "React",
                "1.0.0",
                "com.g4vrk.react.inference.ReactInferenceRCP",
                2,
                null
        );
        final ListenerRegistry listeners = (ListenerRegistry) Proxy.newProxyInstance(
                ListenerRegistry.class.getClassLoader(),
                new Class<?>[] {ListenerRegistry.class},
                (proxy, method, args) -> null
        );
        final RCPEnvironment environment = (RCPEnvironment) Proxy.newProxyInstance(
                RCPEnvironment.class.getClassLoader(),
                new Class<?>[] {RCPEnvironment.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "directory" -> dir;
                    case "logger" -> NOPLogger.NOP_LOGGER;
                    case "platform" -> platform;
                    case "scheduler" -> scheduler;
                    case "metadata" -> metadata;
                    case "listeners" -> listeners;
                    default -> null;
                }
        );
        final InferenceConfigRegistry configs = new InferenceConfigRegistry(
                new ConnectionConfig(new ConnectionConfig.TimeoutConfig(Duration.ofSeconds(1), Duration.ofSeconds(5))),
                new FlowerConfig(1),
                new VectorConfig(64L),
                new ActionsConfig(
                        new ActionsConfig.Link(List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                        new ActionsConfig.TextSet(
                                "nl",
                                "un",
                                "en",
                                "dis",
                                ", ",
                                "-",
                                new ActionsConfig.ReasonSet("nl", "rep", "exp", "http")
                        )
                )
        );

        final ConnectionService connection = new ConnectionService(environment, configs);
        final ModelService models = new ModelService(environment, connection.server(), configs);

        connection.refreshStatus();
        assertEquals(1, scheduledCount.get());

        connection.shutdown();
        connection.refreshStatus();
        assertEquals(1, scheduledCount.get());

        final UUID playerId = UUID.randomUUID();
        final RCPPlayer player = (RCPPlayer) Proxy.newProxyInstance(
                RCPPlayer.class.getClassLoader(),
                new Class<?>[] {RCPPlayer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "uniqueId" -> playerId;
                    case "name" -> "Steve";
                    case "inCombat" -> false;
                    case "checked" -> false;
                    default -> null;
                }
        );
        models.onAttack(player, 7);
        final VectorModel vector = models.models().find(VectorModel.class).orElseThrow();
        assertNotNull(vector.stream().findPlayerData(playerId));

        models.shutdown();
        assertNull(vector.stream().findPlayerData(playerId));
        assertEquals(3, models.models().all().size());
        assertEquals(ModelId.VECTOR_PREVIEW, vector.id());

    }

}
