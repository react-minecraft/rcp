package com.g4vrk.react.inference.model;

import com.g4vrk.react.api.rcp.environment.RCPEnvironment;
import com.g4vrk.react.api.rcp.player.RCPPlayer;
import com.g4vrk.react.api.rcp.verdict.InferenceResult;
import com.g4vrk.react.inference.http.LimitedBodyHandler;
import com.g4vrk.react.inference.http.model.InferencePayload;
import com.g4vrk.react.inference.server.InferenceServer;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Sends model windows and hands the answers to the plugin.
 *
 * <p>Every player has one request slot per model: a model sends its next window only
 * after the previous answer came back. A reset bumps the player's generation, so an
 * answer to a window sent before it is dropped.</p>
 */
public final class RequestDispatcher {

    private static final int MAX_ERROR_BODY_CHARS = 256;

    private final RCPEnvironment environment;
    private volatile InferenceServer server;
    private final Logger logger;
    private final Map<UUID, SlotState> players = new ConcurrentHashMap<>();
    private final PlayerInputGuard inputGuards = new PlayerInputGuard();
    private final AtomicBoolean closed = new AtomicBoolean();

    public RequestDispatcher(
            final @NotNull RCPEnvironment environment,
            final @NotNull InferenceServer server
    ) {

        this.environment = environment;
        this.server = server;
        this.logger = environment.logger();

    }

    /**
     * Serializes a player's model input with reset and removal.
     */
    public @NotNull Object playerLock(final @NotNull UUID player) {

        return inputGuards.lock(player);

    }

    public long inputGeneration(final @NotNull UUID player) {

        return inputGuards.generation(player);

    }

    public void invalidateInput(final @NotNull UUID player) {

        inputGuards.invalidate(player);

    }

    /**
     * Sends later requests through a rebuilt server; requests already sent finish on the old one.
     */
    public void use(final @NotNull InferenceServer server) {

        this.server = server;

    }

    /**
     * Claims the model's request slot for the player.
     *
     * @return the claim, or null when the dispatcher is closed, the player is not checked,
     *         the model is off, or a request is in flight
     */
    public @Nullable Claim tryStart(
            final @NotNull RCPPlayer player,
            final @NotNull ModelId model
    ) {

        if (closed.get() || !player.checked() || !server.isModelEnabled(model)) {
            return null;
        }
        final SlotState slots = slots(player.uniqueId());
        synchronized (slots) {
            if (closed.get() || players.get(player.uniqueId()) != slots || !slots.slot(model).compareAndSet(false, true)) {
                return null;
            }
            return new Claim(slots, model, slots.generation.get());
        }

    }

    /**
     * Gives the claimed slot back when the model had nothing to send after all.
     */
    public void cancel(final @NotNull Claim claim) {

        synchronized (claim.slots) {
            if (claim.slots.generation.get() == claim.generation) {
                claim.slots.slot(claim.model).set(false);
            }
        }

    }

    /**
     * Sends a window for a slot claimed with {@link #tryStart}.
     *
     * @param encoder builds the payload from the access token sent in the body
     */
    public void send(
            final @NotNull RCPPlayer player,
            final @NotNull AimModel model,
            final @NotNull Claim claim,
            final @NotNull Function<String, InferencePayload> encoder
    ) {

        final InferenceServer activeServer = this.server;
        final SlotState slots = claim.slots;
        final long generation = claim.generation;
        final long epoch = activeServer.requestEpoch();
        final String name = player.name();
        if (closed.get() || !activeServer.isCurrent(epoch) || slots.generation.get() != generation) {
            cancel(claim);
            return;
        }
        try {
            activeServer.callAsync(encoder.apply(activeServer.accessToken()), model.id()).whenComplete((response, failure) -> {
                InferenceResult resultToFire = null;
                try {
                    if (failure != null) {
                        logFailure(name, model, failure);
                    } else if (!closed.get() && activeServer.isCurrent(epoch) && slots.generation.get() == generation) {
                        resultToFire = read(name, model, response);
                    }
                } catch (final RuntimeException ex) {
                    logger.warn("Could not process the {} answer for {}", model.id().displayName(), name, ex);
                } finally {
                    synchronized (slots) {
                        if (slots.generation.get() == generation) {
                            slots.slot(model.id()).set(false);
                        } else {
                            resultToFire = null;
                        }
                    }
                }
                if (resultToFire != null
                        && !closed.get()
                        && activeServer.isCurrent(epoch)
                        && slots.generation.get() == generation) {
                    try {
                        environment.fire(player, resultToFire);
                    } catch (final RuntimeException ex) {
                        logger.warn("Could not process the {} answer for {}", model.id().displayName(), name, ex);
                    }
                }
            });
        } catch (final RuntimeException ex) {
            synchronized (slots) {
                if (slots.generation.get() == generation) {
                    slots.slot(model.id()).set(false);
                }
            }
            logger.warn("Could not send the {} request for {}", model.id().displayName(), name, ex);
        }

    }

    /**
     * Drops answers still in flight for the player and frees every slot.
     */
    public void reset(final @NotNull UUID player) {

        final SlotState slots = players.get(player);
        if (slots != null) {
            synchronized (slots) {
                slots.generation.incrementAndGet();
                slots.requesting.values().forEach(flag -> flag.set(false));
            }
        }

    }

    public void remove(final @NotNull UUID player) {

        final SlotState slots = players.get(player);
        if (slots != null) {
            synchronized (slots) {
                slots.generation.incrementAndGet();
                slots.requesting.values().forEach(flag -> flag.set(false));
                players.remove(player, slots);
            }
        }

    }

    /**
     * Prevents new dispatches and clears tracked player slot state on RCP shutdown.
     */
    public void shutdown() {

        closed.set(true);
        inputGuards.invalidateAll();
        for (final UUID player : List.copyOf(players.keySet())) {
            synchronized (playerLock(player)) {
                invalidateInput(player);
                remove(player);
            }
        }
        players.clear();

    }

    @Nullable InferenceResult read(
            final @NotNull String name,
            final @NotNull AimModel model,
            final @NotNull HttpResponse<byte[]> response
    ) {

        final byte[] body = response.body();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            logger.warn(
                    "React rejected the {} request for {}: HTTP {} {}",
                    model.id().displayName(),
                    name,
                    response.statusCode(),
                    sanitizeErrorBody(body)
            );
            return null;
        }
        final ModelAnswer answer = model.readResponse(body);
        final ModelId answered = ModelId.fromIdentity(response.headers().firstValue("X-React-Model-Family").orElse("")
                + "/" + response.headers().firstValue("X-React-Model-Name").orElse(""));
        final ModelId id = answered != null ? answered : model.id();
        return new InferenceResult(
                id.identity(),
                id.displayName(),
                Math.min(1.0D, answer.probability()),
                answer.threshold(),
                answer.enforcement(),
                answer.runId()
        );

    }

    static @NotNull String sanitizeErrorBody(final byte @Nullable [] body) {

        if (body == null || body.length == 0) {
            return "";
        }
        final int maxBytes = Math.min(body.length, MAX_ERROR_BODY_CHARS * 4);
        final String decoded = new String(body, 0, maxBytes, StandardCharsets.UTF_8);
        final StringBuilder clean = new StringBuilder(Math.min(decoded.length(), MAX_ERROR_BODY_CHARS));
        boolean truncated = body.length > maxBytes;
        for (int index = 0; index < decoded.length(); index++) {
            if (clean.length() >= MAX_ERROR_BODY_CHARS) {
                truncated = true;
                break;
            }
            final char character = decoded.charAt(index);
            clean.append(Character.isISOControl(character) ? ' ' : character);
        }
        if (truncated) {
            clean.append("...");
        }
        return clean.toString().trim();

    }

    private void logFailure(
            final @NotNull String name,
            final @NotNull AimModel model,
            final @NotNull Throwable failure
    ) {

        if (LimitedBodyHandler.isTooLarge(failure)) {
            logger.warn("The {} answer for {} is too large", model.id().displayName(), name);
        } else if (!(failure instanceof CancellationException)) {
            logger.warn("Could not send the {} request for {}", model.id().displayName(), name, failure);
        }

    }

    private @NotNull SlotState slots(final @NotNull UUID player) {

        return players.computeIfAbsent(player, ignored -> new SlotState());

    }

    private static final class SlotState {

        private final Map<ModelId, AtomicBoolean> requesting = new EnumMap<>(ModelId.class);
        private final AtomicLong generation = new AtomicLong();

        private SlotState() {

            for (final ModelId model : ModelId.values()) {
                requesting.put(model, new AtomicBoolean());
            }

        }

        private @NotNull AtomicBoolean slot(final @NotNull ModelId model) {

            return requesting.get(model);

        }

    }

    /**
     * A model request slot tied to the player generation that claimed it.
     */
    public static final class Claim {

        private final SlotState slots;
        private final ModelId model;
        private final long generation;

        private Claim(
                final @NotNull SlotState slots,
                final @NotNull ModelId model,
                final long generation
        ) {

            this.slots = slots;
            this.model = model;
            this.generation = generation;

        }

    }

}
