/*
 * ARCHITECTURAL INVARIANT LOCK TEST
 *
 * Do NOT weaken, delete, disable, or rewrite these assertions to pass broken changes.
 * Fix production code or modular boundaries instead.
 */
package com.g4vrk.react.inference.architecture;

import com.g4vrk.react.api.extension.listener.ListenerRegistry;
import com.g4vrk.react.api.rcp.environment.RCPEnvironment;
import com.g4vrk.react.inference.config.ActionsConfig;
import com.g4vrk.react.inference.config.ConnectionConfig;
import com.g4vrk.react.inference.config.FlowerConfig;
import com.g4vrk.react.inference.config.InferenceConfigRegistry;
import com.g4vrk.react.inference.config.VectorConfig;
import com.g4vrk.react.inference.http.model.InferencePayload;
import com.g4vrk.react.inference.json.InferenceJsonCodec;
import com.g4vrk.react.inference.link.Credential;
import com.g4vrk.react.inference.link.LinkStore;
import com.g4vrk.react.inference.model.ModelId;
import com.g4vrk.react.inference.model.RequestDispatcher;
import com.g4vrk.react.inference.model.flower.protocol.FlowerCodec;
import com.g4vrk.react.inference.model.flower.protocol.schema.FlowerResponse;
import com.g4vrk.react.inference.model.vector.VectorModel;
import com.g4vrk.react.inference.model.vector.feature.AimModeTracker;
import com.g4vrk.react.inference.model.vector.model.AimFrame;
import com.g4vrk.react.inference.model.vector.model.VectorFeature;
import com.g4vrk.react.inference.model.vector.protocol.FrameCodec;
import com.g4vrk.react.inference.model.vector.protocol.schema.InferenceResponse;
import com.g4vrk.react.inference.model.vector.session.AimState;
import com.g4vrk.react.inference.server.InferenceServer;
import com.google.flatbuffers.FlatBufferBuilder;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards platform-agnostic RCP modularity, binary/JSON codec safety, credential storage limits,
 * config bounds, and hot-path invariants.
 */
final class RcpArchitectureAndContractLockTest {

    private static final List<String> PLATFORM_SPECIFIC_IMPORTS = List.of(
            "org.bukkit",
            "io.papermc",
            "com.destroystokyo.paper",
            "net.minecraft",
            "net.fabricmc",
            "net.minestom",
            "org.spongepowered.api",
            "com.g4vrk.functionalActions.defaults.DefaultActionSet"
    );
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^import\\s+(?:static\\s+)?([\\w.*]+);", Pattern.MULTILINE);

    @Test
    void rcpSourcesRemainPlatformAgnosticAndLeaveNoEmptyDirectories() throws IOException {

        final Path repo = repoRoot();
        final Path mainJava = repo.resolve("src/main/java");
        final List<String> violations = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(mainJava)) {
            for (final Path file : walk.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                final String content = Files.readString(file);
                final Matcher matcher = IMPORT_PATTERN.matcher(content);
                while (matcher.find()) {
                    final String imported = matcher.group(1);
                    for (final String forbidden : PLATFORM_SPECIFIC_IMPORTS) {
                        if (imported.startsWith(forbidden)) {
                            violations.add(mainJava.relativize(file) + " imports forbidden " + imported);
                        }
                    }
                    if (imported.endsWith(".*")) {
                        violations.add(mainJava.relativize(file) + " uses wildcard import " + imported);
                    }
                }
            }
        }
        try (Stream<Path> dirs = Files.walk(repo.resolve("src"))) {
            for (final Path dir : dirs.filter(Files::isDirectory).toList()) {
                try (Stream<Path> children = Files.list(dir)) {
                    if (children.findAny().isEmpty()) {
                        violations.add("Empty source directory present: " + repo.relativize(dir));
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), "RCP architecture violation: " + violations);

    }

    @Test
    void flowerAndVectorCodecsRejectControlRunIdsAndInvalidModes() {

        final FlatBufferBuilder badFlower = new FlatBufferBuilder();
        final int badFlowerRunId = badFlower.createString("run\nid");
        FlowerResponse.finishFlowerResponseBuffer(
                badFlower,
                FlowerResponse.createFlowerResponse(badFlower, 2, 0.5D, 0.8D, 0, false, badFlowerRunId)
        );
        assertThrows(IllegalArgumentException.class, () -> FlowerCodec.decodeAnswer(badFlower.sizedByteArray()));

        final FlatBufferBuilder badVector = new FlatBufferBuilder();
        final int badVectorRunId = badVector.createString("run\u0007id");
        InferenceResponse.finishInferenceResponseBuffer(
                badVector,
                InferenceResponse.createInferenceResponse(badVector, FrameCodec.SCHEMA_VERSION, 0.5D, 0.8D, 0, false, badVectorRunId)
        );
        final byte[] inner = badVector.sizedByteArray();
        final byte[] packet = ByteBuffer.allocate(14 + inner.length)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(new byte[] {'G', 'A', 'I', 'B'})
                .putShort((short) 1)
                .putDouble(0.5D)
                .put(inner)
                .array();
        assertThrows(IllegalArgumentException.class, () -> FrameCodec.decodePrediction(packet));

        final FlatBufferBuilder blankVector = new FlatBufferBuilder();
        final int blankRunId = blankVector.createString("   ");
        InferenceResponse.finishInferenceResponseBuffer(
                blankVector,
                InferenceResponse.createInferenceResponse(blankVector, FrameCodec.SCHEMA_VERSION, 0.5D, 0.8D, 0, false, blankRunId)
        );
        final byte[] blankInner = blankVector.sizedByteArray();
        final byte[] blankPacket = ByteBuffer.allocate(14 + blankInner.length)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(new byte[] {'G', 'A', 'I', 'B'})
                .putShort((short) 1)
                .putDouble(0.5D)
                .put(blankInner)
                .array();
        assertThrows(IllegalArgumentException.class, () -> FrameCodec.decodePrediction(blankPacket));

    }

    @Test
    void jsonCodecRejectsNonFiniteNumbersOnBothReadAndWrite() {

        assertThrows(IllegalArgumentException.class, () -> InferenceJsonCodec.readObject("{\"v\":1e500}"));
        assertThrows(IllegalArgumentException.class, () -> InferenceJsonCodec.readObject("{\"v\":-1e500}"));
        assertThrows(IllegalArgumentException.class, () -> InferenceJsonCodec.writeObject(Map.of("v", Double.POSITIVE_INFINITY)));

    }

    @Test
    void linkStoreRejectsOversizedAndMalformedLineCounts(@TempDir final Path dir) throws Exception {

        final Path oversized = dir.resolve("big.link");
        Files.write(oversized, new byte[20_000]);
        assertNull(new LinkStore(oversized, "fp").load());

        final Path twoLines = dir.resolve("two.link");
        encrypt(twoLines, "fp", "token-only\nextra-line");
        assertNull(new LinkStore(twoLines, "fp").load());

        final Path valid = dir.resolve("ok.link");
        encrypt(valid, "fp", "rx_unsigned");
        final Credential credential = new LinkStore(valid, "fp").load();
        assertEquals("rx_unsigned", credential != null ? credential.accessToken() : null);

    }

    @Test
    void aimStateAndTrackerHandleModesWithoutNonFiniteCorruption() {

        final AimState state = new AimState();
        state.capture(null, 10.0F, 5.0F);
        assertThrows(IllegalArgumentException.class, () -> state.capture(null, Float.NaN, 0.0F));
        assertThrows(IllegalArgumentException.class, () -> state.capture(null, 0.0F, Float.POSITIVE_INFINITY));

        final AimModeTracker tracker = new AimModeTracker();
        for (int i = 0; i < 100; i++) {
            tracker.update(i * 1.2F, i * 1.2F + 0.6F, 0.6F, 0.3F);
        }
        final float[] features = new float[VectorFeature.COUNT];
        tracker.writeTo(features);
        assertEquals(1.0F, features[VectorFeature.MODE_YAW_VALID.ordinal()]);

    }

    @Test
    void configRegistryValidatesPositiveLimitsAndServerRejectsClosedCalls() {

        final ConnectionConfig validConnection = new ConnectionConfig(
                new ConnectionConfig.TimeoutConfig(Duration.ofSeconds(1), Duration.ofSeconds(5))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> InferenceConfigRegistry.validate(
                        new ConnectionConfig(new ConnectionConfig.TimeoutConfig(Duration.ZERO, Duration.ofSeconds(5))),
                        new FlowerConfig(1),
                        new VectorConfig(100L)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> InferenceConfigRegistry.validate(validConnection, new FlowerConfig(0), new VectorConfig(100L))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> InferenceConfigRegistry.validate(validConnection, new FlowerConfig(1), new VectorConfig(0L))
        );

        final InferenceServer server = new InferenceServer(
                NOPLogger.NOP_LOGGER,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                null,
                "fp",
                "2.0.0",
                "2.0.0",
                new ActionsConfig.ReasonSet("not-linked", "replaced", "expired", "http")
        );
        server.shutdown();
        assertThrows(
                IllegalStateException.class,
                () -> server.callAsync(new InferencePayload(new byte[] {1}), ModelId.FLOWER_BASELINE)
        );
        assertEquals(HttpClient.Version.HTTP_1_1, InferenceServer.httpVersionFor("http://127.0.0.1:18080"));
        assertEquals(HttpClient.Version.HTTP_2, InferenceServer.httpVersionFor("https://api.react-ac.space"));
        final AimFrame zero = new AimFrame(0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, new float[VectorFeature.COUNT]);
        final float[] dest = new float[AimFrame.INFERENCE_FEATURE_COUNT];
        zero.copyInferenceFeaturesTo(dest, 0);
        assertEquals(0F, dest[0]);

        final ListenerRegistry emptyListeners = (ListenerRegistry) Proxy.newProxyInstance(
                ListenerRegistry.class.getClassLoader(),
                new Class<?>[] {ListenerRegistry.class},
                (proxy, method, args) -> null
        );
        final RCPEnvironment rcpEnvironment = (RCPEnvironment) Proxy.newProxyInstance(
                RCPEnvironment.class.getClassLoader(),
                new Class<?>[] {RCPEnvironment.class},
                (proxy, method, args) -> method.getName().equals("listeners") ? emptyListeners : null
        );
        final VectorModel vectorModel = new VectorModel(
                rcpEnvironment,
                new RequestDispatcher(rcpEnvironment, server),
                100L
        );
        final UUID unknownPlayer = UUID.randomUUID();
        vectorModel.clearHistory(unknownPlayer);
        vectorModel.resetPlayer(unknownPlayer);
        assertNull(vectorModel.stream().findPlayerData(unknownPlayer));

    }

    private static void encrypt(
            final @NotNull Path file,
            final @NotNull String fingerprint,
            final @NotNull String plaintext
    ) throws Exception {

        final byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest((fingerprint + "link").getBytes(StandardCharsets.UTF_8));
        final byte[] iv = new byte[12];
        final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(digest, "AES"), new GCMParameterSpec(128, iv));
        final byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        final byte[] payload = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, payload, 0, iv.length);
        System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
        Files.writeString(file, Base64.getEncoder().encodeToString(payload), StandardCharsets.US_ASCII);

    }

    private static @NotNull Path repoRoot() {

        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("settings.gradle.kts"))
                    && Files.isDirectory(current.resolve("src/main/java"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate RCP repository root");

    }

}
