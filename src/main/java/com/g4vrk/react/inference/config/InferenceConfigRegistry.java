package com.g4vrk.react.inference.config;

import com.g4vrk.react.api.config.ConfigFile;
import com.g4vrk.react.api.config.migration.ConfigMigrationChain;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Every file of the RCP, loaded together so a broken file keeps the previous set.
 */
public record InferenceConfigRegistry(
        @NotNull ConnectionConfig connection,
        @NotNull FlowerConfig flower,
        @NotNull VectorConfig vector,
        @NotNull ActionsConfig actions
) {

    private static final Pattern PERCENT_PLACEHOLDER = Pattern.compile("%([a-z_]+)%");

    private static final ConfigMigrationChain ACTIONS_MIGRATIONS = ConfigMigrationChain.builder()
            .step(root -> { })
            .step(root -> bracePlaceholders(root.node("link")))
            .build();

    public static @NotNull InferenceConfigRegistry load(final @NotNull Path directory, final @NotNull Logger logger)
            throws ConfigurateException {

        final String language = language();
        final ConnectionConfig connection = file(
                ConnectionConfig.class,
                directory,
                language,
                "connection.yml",
                logger,
                ConfigMigrationChain.none()
        );
        final FlowerConfig flower = file(
                FlowerConfig.class,
                directory,
                language,
                "models/flower.yml",
                logger,
                ConfigMigrationChain.none()
        );
        final VectorConfig vector = file(
                VectorConfig.class,
                directory,
                language,
                "models/vector.yml",
                logger,
                ConfigMigrationChain.none()
        );
        validate(connection, flower, vector);
        return new InferenceConfigRegistry(
                connection,
                flower,
                vector,
                file(ActionsConfig.class, directory, language, "actions.yml", logger, ACTIONS_MIGRATIONS)
        );

    }

    public static void validate(
            final @NotNull ConnectionConfig connection,
            final @NotNull FlowerConfig flower,
            final @NotNull VectorConfig vector
    ) {

        if (connection.timeouts() == null
                || connection.timeouts().connect() == null
                || connection.timeouts().connect().isNegative()
                || connection.timeouts().connect().isZero()
                || connection.timeouts().call() == null
                || connection.timeouts().call().isNegative()
                || connection.timeouts().call().isZero()) {
            throw new IllegalArgumentException("connection.yml timeouts must be positive durations");
        }
        if (flower.step() < 1) {
            throw new IllegalArgumentException("models/flower.yml step must be at least 1");
        }
        if (vector.combatTicks() < 1L) {
            throw new IllegalArgumentException("models/vector.yml combat-ticks must be at least 1");
        }

    }

    private static <T> T file(
            final Class<T> type,
            final Path directory,
            final String language,
            final String name,
            final Logger logger,
            final ConfigMigrationChain migrations
    ) throws ConfigurateException {

        return ConfigFile.builder(type)
                .path(directory.resolve(name))
                .defaults(InferenceConfigRegistry.class.getClassLoader(), language + "/" + name)
                .migrations(migrations)
                .logger(logger)
                .build()
                .load();

    }

    /**
     * Rewrites every {@code %name%} below the node as {@code {name}}, the placeholder form since version 2.
     */
    private static void bracePlaceholders(final ConfigurationNode node) throws SerializationException {

        for (final ConfigurationNode child : node.isList() ? node.childrenList() : node.childrenMap().values()) {
            bracePlaceholders(child);
        }
        if (node.rawScalar() instanceof String text) {
            node.set(PERCENT_PLACEHOLDER.matcher(text).replaceAll("{$1}"));
        }

    }

    private static String language() {

        final String language = System.getProperty("user.language", "ru").toLowerCase(Locale.ROOT);
        return language.equals("en") ? "en" : "ru";

    }

}
