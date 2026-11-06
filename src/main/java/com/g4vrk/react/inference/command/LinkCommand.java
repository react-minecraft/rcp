package com.g4vrk.react.inference.command;

import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.api.rcp.command.RCPCommand;
import com.g4vrk.react.inference.config.ActionsConfig;
import com.g4vrk.react.inference.model.ModelId;
import com.g4vrk.react.inference.server.InferenceServer;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.kyori.adventure.audience.Audience;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

/**
 * {@code /react link}: links this server to the owner's account on the site.
 * <ul>
 *     <li>{@code /react link} prints a link the owner opens and approves</li>
 *     <li>{@code /react link <code>} takes a code from the server's page on the site</li>
 *     <li>{@code /react link check} asks the site about the current link</li>
 * </ul>
 */
public final class LinkCommand implements RCPCommand {

    /**
     * Stores a key the site handed out and switches the RCP to it.
     */
    @FunctionalInterface
    public interface KeyHandler {

        void accept(@NotNull String apiKey) throws Exception;

    }

    private final Supplier<InferenceServer> server;
    private final KeyHandler onLinked;
    private final Scheduler scheduler;
    private final Logger logger;

    private ExecutableActionList<? super Audience> checking;
    private ExecutableActionList<? super Audience> requesting;
    private ExecutableActionList<? super Audience> open;
    private ExecutableActionList<? super Audience> linked;
    private ExecutableActionList<? super Audience> failed;
    private ExecutableActionList<? super Audience> status;

    public LinkCommand(
            final @NotNull Supplier<InferenceServer> server,
            final @NotNull KeyHandler onLinked,
            final @NotNull Scheduler scheduler,
            final @NotNull Logger logger
    ) {
        this.server = server;
        this.onLinked = onLinked;
        this.scheduler = scheduler;
        this.logger = logger;
    }

    public void reload(final @NotNull ActionParser<Audience> parser, final @NotNull ActionsConfig.Link config) {
        this.checking = parser.parseAll(config.checking());
        this.requesting = parser.parseAll(config.requesting());
        this.open = parser.parseAll(config.open());
        this.linked = parser.parseAll(config.linked());
        this.failed = parser.parseAll(config.failed());
        this.status = parser.parseAll(config.status());
    }

    @Override
    public @NotNull String name() {
        return "link";
    }

    @Override
    public void execute(final @NotNull CommandSender sender, final @NotNull List<String> args) {
        final InferenceServer current = server.get();
        if (!args.isEmpty() && args.get(0).equalsIgnoreCase("check")) {
            check(sender, current);
            return;
        }
        final CompletableFuture<InferenceServer.ConnectResult> connection;
        if (args.isEmpty()) {
            requesting.run(sender);
            connection = current.connectWithLink(link -> run(sender, open, Map.of(
                    "%url%", link.url(),
                    "%code%", link.code(),
                    "%minutes%", Long.toString(Math.max(1L, link.expiresInSeconds() / 60L)))));
        } else {
            checking.run(sender);
            connection = current.connect(args.get(0));
        }
        connection.whenComplete((result, failure) -> {
            if (failure != null) {
                fail(sender, String.valueOf(failure.getMessage()));
            } else if (result.apiKey() == null) {
                fail(sender, result.detail());
            } else {
                finish(sender, result);
            }
        });
    }

    @Override
    public @NotNull List<String> suggest(final @NotNull CommandSender sender, final @NotNull List<String> args) {
        return args.size() <= 1 ? List.of("check") : List.of();
    }

    private void check(final CommandSender sender, final InferenceServer current) {
        current.checkConnection().whenComplete((result, failure) -> {
            if (failure != null) {
                fail(sender, String.valueOf(failure.getMessage()));
            } else if (result.problem() != null) {
                fail(sender, result.problem());
            } else {
                run(sender, status, Map.of("%server%", result.server(), "%models%", describe(result.models())));
            }
        });
    }

    private void finish(final CommandSender sender, final InferenceServer.ConnectResult result) {
        try {
            onLinked.accept(result.apiKey());
        } catch (final Exception exception) {
            logger.error("Could not save the link", exception);
            fail(sender, String.valueOf(exception.getMessage()));
            return;
        }
        run(sender, linked, Map.of("%server%", result.server(), "%models%", result.modelNames()));
    }

    private void fail(final CommandSender sender, final String reason) {
        run(sender, failed, Map.of("%reason%", reason));
    }

    private void run(
            final CommandSender sender,
            final ExecutableActionList<? super Audience> actions,
            final Map<String, String> placeholders
    ) {
        scheduler.schedule(() -> actions.run(sender, text -> {
            String replaced = text;
            for (final Map.Entry<String, String> placeholder : placeholders.entrySet()) {
                replaced = replaced.replace(placeholder.getKey(), placeholder.getValue());
            }
            return replaced;
        }), TickSchedule.instant());
    }

    private static String describe(final Map<ModelId, Boolean> models) {
        if (models.isEmpty()) {
            return "-";
        }
        return models.entrySet().stream()
                .map(entry -> entry.getKey().displayName() + (entry.getValue() ? " ✔" : " ✘"))
                .collect(Collectors.joining(", "));
    }

}
