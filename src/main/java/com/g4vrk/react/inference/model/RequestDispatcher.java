package com.g4vrk.react.inference.model;

import com.g4vrk.react.api.rcp.environment.RCPEnvironment;
import com.g4vrk.react.api.rcp.player.RCPPlayer;
import com.g4vrk.react.api.rcp.verdict.InferenceResult;
import com.g4vrk.react.inference.http.LimitedBodyHandler;
import com.g4vrk.react.inference.http.model.HttpRequest;
import com.g4vrk.react.inference.server.InferenceServer;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
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

    private final RCPEnvironment environment;
    private volatile InferenceServer server;
    private final Logger logger;
    private final Map<UUID, Slots> players = new ConcurrentHashMap<>();
    private final PlayerInputGuards inputGuards = new PlayerInputGuards();

    public RequestDispatcher(final @NotNull RCPEnvironment environment, final @NotNull InferenceServer server) {
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
     * @return the claim, or null when the player is not checked, the model is off or a request is in flight
     */
    public @Nullable Claim tryStart(final @NotNull RCPPlayer player, final @NotNull ModelId model) {
        if (!player.checked() || !server.isModelEnabled(model)) {
            return null;
        }
        final Slots slots = slots(player.uniqueId());
        synchronized (slots) {
            if (players.get(player.uniqueId()) != slots || !slots.slot(model).compareAndSet(false, true)) {
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
     * @param encoder builds the request from the key sent in the body
     */
    public void send(
            final @NotNull RCPPlayer player,
            final @NotNull AimModel model,
            final @NotNull Claim claim,
            final @NotNull Function<String, HttpRequest> encoder
    ) {
        final InferenceServer server = this.server;
        final Slots slots = claim.slots;
        final long generation = claim.generation;
        final long epoch = server.requestEpoch();
        final String name = player.name();
        try {
            server.callAsync(encoder.apply(server.accessToken()), model.id()).whenComplete((response, failure) -> {
                try {
                    final InferenceResult result = failure == null ? read(name, model, response) : null;
                    if (failure != null) {
                        logFailure(name, model, failure);
                    }
                    if (result != null) {
                        server.publishIfCurrent(epoch, () -> {
                            synchronized (slots) {
                                if (slots.generation.get() == generation) {
                                    environment.fire(player, result);
                                }
                            }
                        });
                    }
                } catch (final RuntimeException ex) {
                    logger.warn("Could not process the {} answer for {}", model.id().displayName(), name, ex);
                } finally {
                    synchronized (slots) {
                        if (slots.generation.get() == generation) {
                            slots.slot(model.id()).set(false);
                        }
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
        final Slots slots = players.get(player);
        if (slots != null) {
            synchronized (slots) {
                slots.generation.incrementAndGet();
                slots.requesting.values().forEach(flag -> flag.set(false));
            }
        }
    }

    public void remove(final @NotNull UUID player) {
        final Slots slots = players.get(player);
        if (slots != null) {
            synchronized (slots) {
                slots.generation.incrementAndGet();
                slots.requesting.values().forEach(flag -> flag.set(false));
                players.remove(player, slots);
            }
        }
    }

    private @Nullable InferenceResult read(
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
                    new String(body, StandardCharsets.UTF_8)
            );
            return null;
        }
        final ModelAnswer answer = model.readResponse(body);
        if (answer.probability() < 0.0D) {
            return null;
        }
        final ModelId answered = ModelId.fromIdentity(response.headers().firstValue("X-React-Model-Family").orElse("")
                + "/" + response.headers().firstValue("X-React-Model-Name").orElse(""));
        final ModelId id = answered != null ? answered : model.id();
        return new InferenceResult(
                id.identity(),
                id.displayName(),
                Math.min(1.0D, answer.probability()),
                answer.threshold(),
                answer.enforcement()
        );
    }

    private void logFailure(final @NotNull String name, final @NotNull AimModel model, final @NotNull Throwable failure) {
        if (LimitedBodyHandler.isTooLarge(failure)) {
            logger.warn("The {} answer for {} is too large", model.id().displayName(), name);
        } else if (!(failure instanceof CancellationException)) {
            logger.warn("Could not send the {} request for {}", model.id().displayName(), name, failure);
        }
    }

    private @NotNull Slots slots(final @NotNull UUID player) {
        return players.computeIfAbsent(player, ignored -> new Slots());
    }

    private static final class Slots {

        private final Map<ModelId, AtomicBoolean> requesting = new EnumMap<>(ModelId.class);
        private final AtomicLong generation = new AtomicLong();

        private Slots() {
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

        private final Slots slots;
        private final ModelId model;
        private final long generation;

        private Claim(final Slots slots, final ModelId model, final long generation) {
            this.slots = slots;
            this.model = model;
            this.generation = generation;
        }

    }

}
