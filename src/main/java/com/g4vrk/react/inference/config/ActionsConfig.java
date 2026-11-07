package com.g4vrk.react.inference.config;

import com.g4vrk.react.inference.model.ModelId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

/**
 * {@code actions.yml}: what {@code /react link} shows and the texts put into it and into {@code %react_model%}.
 */
@ConfigSerializable
public record ActionsConfig(Link link, Texts texts) {

    /**
     * @param checking   while a code is checked
     * @param requesting while the site is asked for a link
     * @param open       the link to open, with {@code {url}}, {@code {code}} and {@code {minutes}}
     * @param linked     once linked, with {@code {server}} and {@code {models}}
     * @param failed     when linking fails, with {@code {reason}}
     * @param status     the answer of {@code /react link check}, with {@code {server}} and {@code {models}}
     */
    @ConfigSerializable
    public record Link(
            List<String> checking,
            List<String> requesting,
            List<String> open,
            List<String> linked,
            List<String> failed,
            List<String> status
    ) {
    }

    /**
     * @param notLinked     the status while the server is not linked
     * @param unavailable   the status while no model is enabled
     * @param modelEnabled  one enabled model in {@code {models}}, with {@code {model}}
     * @param modelDisabled one disabled model in {@code {models}}, with {@code {model}}
     * @param separator     between the models
     * @param none          {@code {models}} when the account has none
     * @param reasons       what {@code {reason}} says
     */
    @ConfigSerializable
    public record Texts(
            String notLinked,
            String unavailable,
            String modelEnabled,
            String modelDisabled,
            String separator,
            String none,
            Reasons reasons
    ) {

        /**
         * {@code {models}}: every model of the account, marked enabled or disabled.
         */
        public @NotNull String describe(final @NotNull Map<ModelId, Boolean> models) {
            if (models.isEmpty()) {
                return none;
            }
            return models.entrySet().stream()
                    .map(entry -> (entry.getValue() ? modelEnabled : modelDisabled)
                            .replace("{model}", entry.getKey().displayName()))
                    .collect(Collectors.joining(separator));
        }

        /**
         * The status behind {@code %react_model%}: the enabled models, or why there are none.
         */
        public @NotNull String status(final boolean linked, final @NotNull Set<ModelId> enabled) {
            if (!linked) {
                return notLinked;
            }
            if (enabled.isEmpty()) {
                return unavailable;
            }
            return enabled.stream().map(ModelId::displayName).collect(Collectors.joining(separator));
        }

    }

    /**
     * @param notLinked the server is not linked
     * @param replaced  a newer {@code /react link} took over
     * @param expired   the owner did not approve in time
     * @param http      the site answered with an error, with {@code {status}}
     */
    @ConfigSerializable
    public record Reasons(String notLinked, String replaced, String expired, String http) {
    }

}
