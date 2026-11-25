package com.g4vrk.react.inference.command;

import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.api.extension.command.CommandRegistrar;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.api.platform.sender.PlatformSender;
import com.g4vrk.react.inference.config.ActionsConfig;
import com.g4vrk.react.inference.server.AccountClient;
import com.g4vrk.react.inference.server.InferenceServer;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import net.kyori.adventure.audience.Audience;
import org.incendo.cloud.Command;
import org.incendo.cloud.key.CloudKey;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

/**
 * {@code /react link}: links this server to the owner's account on the site.
 * <ul>
 *     <li>{@code /react link} prints a link the owner opens and approves</li>
 *     <li>{@code /react link <code>} takes a code from the server's page on the site</li>
 *     <li>{@code /react link check} asks the site about the current link</li>
 * </ul>
 *
 * <p>Registered directly as a Cloud subcommand of React, with permission
 * {@code react.command.link}.</p>
 */
public final class LinkCommand {

    public static final String LABEL = "react";
    public static final String PERMISSION = "react.command.link";
    private static final CloudKey<String> CODE = CloudKey.of("code", String.class);

    /**
     * Stores a credential the site handed out and switches the RCP to it.
     */
    @FunctionalInterface
    public interface CredentialHandler {

        void accept(
                final @NotNull InferenceServer server,
                final @NotNull AccountClient.ConnectResult result
        ) throws Exception;

    }

    private final Supplier<InferenceServer> server;
    private final CredentialHandler onLinked;
    private final TaskScheduler<PlatformPlayer> scheduler;
    private final Logger logger;

    private volatile ActionSet actions;

    public LinkCommand(
            final @NotNull Supplier<InferenceServer> server,
            final @NotNull CredentialHandler onLinked,
            final @NotNull TaskScheduler<PlatformPlayer> scheduler,
            final @NotNull Logger logger
    ) {

        this.server = server;
        this.onLinked = onLinked;
        this.scheduler = scheduler;
        this.logger = logger;

    }

    public void reload(
            final @NotNull ActionParser<Audience> parser,
            final @NotNull ActionsConfig config
    ) {

        final ActionsConfig.Link link = config.link();
        this.actions = new ActionSet(
                parser.parseAll(link.checking()),
                parser.parseAll(link.requesting()),
                parser.parseAll(link.open()),
                parser.parseAll(link.linked()),
                parser.parseAll(link.failed()),
                parser.parseAll(link.status()),
                config.texts()
        );

    }

    /**
     * Registers {@code /react link [code|check]}.
     *
     * @param commands the command registrar of the RCP
     */
    public void register(final @NotNull CommandRegistrar commands) {

        final Command.Builder<PlatformSender> link = commands.command(LABEL).literal("link").permission(PERMISSION);
        commands.register(LABEL, link.handler(context -> execute(context.sender(), "")));
        commands.register(LABEL, link.literal("check").handler(context -> check(context.sender(), server.get())));
        commands.register(LABEL, link.required(CODE, StringParser.stringParser()).handler(context -> execute(context.sender(), context.get(CODE))));

    }

    private void execute(
            final @NotNull PlatformSender sender,
            final @NotNull String code
    ) {

        final ActionSet snapshot = this.actions;
        final InferenceServer current = server.get();
        final CompletableFuture<AccountClient.ConnectResult> connection;
        if (code.isEmpty()) {
            run(sender, snapshot.requesting(), Map.of());
            connection = current.account().connectWithLink(link -> run(sender, snapshot.open(), Map.of(
                    "{url}",
                    link.url(),
                    "{code}",
                    link.code(),
                    "{minutes}",
                    Long.toString(Math.max(1L, link.expiresInSeconds() / 60L))
            )));
        } else {
            run(sender, snapshot.checking(), Map.of());
            connection = current.account().connect(code);
        }
        connection.whenComplete((result, failure) -> {
            if (failure != null) {
                fail(sender, failureMessage(failure));
            } else if (result.credential() == null) {
                fail(sender, result.detail());
            } else {
                finish(sender, current, result);
            }
        });

    }

    private void check(
            final @NotNull PlatformSender sender,
            final @NotNull InferenceServer current
    ) {

        current.account().checkConnection().whenComplete((result, failure) -> {
            if (failure != null) {
                fail(sender, failureMessage(failure));
            } else if (result.problem() != null) {
                fail(sender, result.problem());
            } else {
                final ActionSet snapshot = this.actions;
                run(sender, snapshot.status(), Map.of("{server}", result.server(), "{models}", snapshot.texts().describe(result.models())));
            }
        });

    }

    private void finish(
            final @NotNull PlatformSender sender,
            final @NotNull InferenceServer current,
            final @NotNull AccountClient.ConnectResult result
    ) {

        final ActionSet snapshot = this.actions;
        try {
            onLinked.accept(current, result);
        } catch (final Exception exception) {
            logger.error("Could not save the link", exception);
            fail(sender, failureMessage(exception));
            return;
        }
        run(sender, snapshot.linked(), Map.of("{server}", result.server(), "{models}", snapshot.texts().describe(result.models())));

    }

    private void fail(
            final @NotNull PlatformSender sender,
            final @NotNull String reason
    ) {

        run(sender, this.actions.failed(), Map.of("{reason}", reason));

    }

    private void run(
            final @NotNull PlatformSender sender,
            final @NotNull ExecutableActionList<? super Audience> actions,
            final @NotNull Map<String, String> placeholders
    ) {

        final Runnable response = () -> actions.run(sender, text -> replacePlaceholders(text, placeholders));
        final PlatformPlayer player = sender.asPlayer();
        if (player != null) {
            scheduler.scheduleEntity(player, response, TickSchedule.instant());
        } else {
            scheduler.schedule(response, TickSchedule.instant());
        }

    }

    private static @NotNull String failureMessage(final @NotNull Throwable failure) {

        Throwable root = failure;
        while (root instanceof CompletionException && root.getCause() != null) {
            root = root.getCause();
        }
        final String message = root.getMessage();
        return message != null && !message.isBlank() ? message : root.getClass().getSimpleName();

    }

    private static @NotNull String replacePlaceholders(
            final @NotNull String text,
            final @NotNull Map<String, String> placeholders
    ) {

        final int firstOpen = text.indexOf('{');
        if (firstOpen < 0 || placeholders.isEmpty()) {
            return text;
        }
        final StringBuilder result = new StringBuilder(text.length() + 16);
        int cursor = 0;
        while (cursor < text.length()) {
            final int open = text.indexOf('{', cursor);
            if (open < 0) {
                result.append(text, cursor, text.length());
                break;
            }
            final int close = text.indexOf('}', open + 1);
            if (close < 0) {
                result.append(text, cursor, text.length());
                break;
            }
            final String replacement = placeholders.get(text.substring(open, close + 1));
            if (replacement != null) {
                result.append(text, cursor, open);
                result.append(replacement);
                cursor = close + 1;
            } else {
                result.append(text, cursor, open + 1);
                cursor = open + 1;
            }
        }
        return result.toString();

    }

    private record ActionSet(
            @NotNull ExecutableActionList<? super Audience> checking,
            @NotNull ExecutableActionList<? super Audience> requesting,
            @NotNull ExecutableActionList<? super Audience> open,
            @NotNull ExecutableActionList<? super Audience> linked,
            @NotNull ExecutableActionList<? super Audience> failed,
            @NotNull ExecutableActionList<? super Audience> status,
            @NotNull ActionsConfig.TextSet texts
    ) {

    }

}
