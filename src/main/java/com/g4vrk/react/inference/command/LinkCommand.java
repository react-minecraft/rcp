package com.g4vrk.react.inference.command;

import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.api.extension.command.CommandRegistrar;
import com.g4vrk.react.inference.config.ActionsConfig;
import com.g4vrk.react.inference.link.Credential;
import com.g4vrk.react.inference.server.InferenceServer;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.kyori.adventure.audience.Audience;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

/**
 * {@code /reactlink}: links this server to the owner's account on the site.
 * <ul>
 *     <li>{@code /reactlink} prints a link the owner opens and approves</li>
 *     <li>{@code /reactlink <code>} takes a code from the server's page on the site</li>
 *     <li>{@code /reactlink check} asks the site about the current link</li>
 * </ul>
 *
 * <p>It is a Cloud command of its own because an RCP cannot add subcommands to {@code /react}. The
 * permission stays {@code react.command.link}, the one the old subcommand used.</p>
 */
public final class LinkCommand {

    public static final String LABEL = "reactlink";
    public static final String PERMISSION = "react.command.link";

    /**
     * Stores a credential the site handed out and switches the RCP to it.
     */
    @FunctionalInterface
    public interface CredentialHandler {

        void accept(@NotNull Credential credential) throws Exception;

    }

    private final Supplier<InferenceServer> server;
    private final CredentialHandler onLinked;
    private final Scheduler scheduler;
    private final Logger logger;

    private ExecutableActionList<? super Audience> checking;
    private ExecutableActionList<? super Audience> requesting;
    private ExecutableActionList<? super Audience> open;
    private ExecutableActionList<? super Audience> linked;
    private ExecutableActionList<? super Audience> failed;
    private ExecutableActionList<? super Audience> status;
    private ActionsConfig.Texts texts;

    public LinkCommand(
            final @NotNull Supplier<InferenceServer> server,
            final @NotNull CredentialHandler onLinked,
            final @NotNull Scheduler scheduler,
            final @NotNull Logger logger
    ) {
        this.server = server;
        this.onLinked = onLinked;
        this.scheduler = scheduler;
        this.logger = logger;
    }

    public void reload(final @NotNull ActionParser<Audience> parser, final @NotNull ActionsConfig config) {
        final ActionsConfig.Link link = config.link();
        this.checking = parser.parseAll(link.checking());
        this.requesting = parser.parseAll(link.requesting());
        this.open = parser.parseAll(link.open());
        this.linked = parser.parseAll(link.linked());
        this.failed = parser.parseAll(link.failed());
        this.status = parser.parseAll(link.status());
        this.texts = config.texts();
    }

    /**
     * Registers {@code /reactlink [code|check]}.
     *
     * @param commands the command registrar of the RCP
     */
    public void register(final @NotNull CommandRegistrar commands) {
        commands.register(LABEL, commands.command(LABEL)
                .permission(PERMISSION)
                .optional("code", StringParser.stringParser(), SuggestionProvider.suggestingStrings("check"))
                .handler(context -> execute(context.sender(), context.<String>optional("code").orElse(""))));
    }

    private void execute(final @NotNull CommandSender sender, final @NotNull String code) {
        final InferenceServer current = server.get();
        if (code.equalsIgnoreCase("check")) {
            check(sender, current);
            return;
        }
        final CompletableFuture<InferenceServer.ConnectResult> connection;
        if (code.isEmpty()) {
            requesting.run(sender);
            connection = current.connectWithLink(link -> run(sender, open, Map.of(
                    "{url}", link.url(),
                    "{code}", link.code(),
                    "{minutes}", Long.toString(Math.max(1L, link.expiresInSeconds() / 60L)))));
        } else {
            checking.run(sender);
            connection = current.connect(code);
        }
        connection.whenComplete((result, failure) -> {
            if (failure != null) {
                fail(sender, String.valueOf(failure.getMessage()));
            } else if (result.credential() == null) {
                fail(sender, result.detail());
            } else {
                finish(sender, result.credential(), result);
            }
        });
    }

    private void check(final CommandSender sender, final InferenceServer current) {
        current.checkConnection().whenComplete((result, failure) -> {
            if (failure != null) {
                fail(sender, String.valueOf(failure.getMessage()));
            } else if (result.problem() != null) {
                fail(sender, result.problem());
            } else {
                run(sender, status, Map.of("{server}", result.server(), "{models}", texts.describe(result.models())));
            }
        });
    }

    private void finish(
            final CommandSender sender,
            final Credential credential,
            final InferenceServer.ConnectResult result
    ) {
        try {
            onLinked.accept(credential);
        } catch (final Exception exception) {
            logger.error("Could not save the link", exception);
            fail(sender, String.valueOf(exception.getMessage()));
            return;
        }
        run(sender, linked, Map.of("{server}", result.server(), "{models}", texts.describe(result.models())));
    }

    private void fail(final CommandSender sender, final String reason) {
        run(sender, failed, Map.of("{reason}", reason));
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

}
