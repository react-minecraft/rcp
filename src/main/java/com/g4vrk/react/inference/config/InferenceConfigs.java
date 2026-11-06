package com.g4vrk.react.inference.config;

import com.g4vrk.react.api.config.ConfigFile;
import java.nio.file.Path;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurateException;

/**
 * Every file of the RCP, loaded together so a broken file keeps the previous set.
 */
public record InferenceConfigs(
        @NotNull ConnectionConfig connection,
        @NotNull FlowerConfig flower,
        @NotNull VectorConfig vector,
        @NotNull ActionsConfig actions
) {

    public static @NotNull InferenceConfigs load(final @NotNull Path directory, final @NotNull Logger logger)
            throws ConfigurateException {
        final String language = language();
        return new InferenceConfigs(
                file(ConnectionConfig.class, directory, language, "connection.yml", logger),
                file(FlowerConfig.class, directory, language, "models/flower.yml", logger),
                file(VectorConfig.class, directory, language, "models/vector.yml", logger),
                file(ActionsConfig.class, directory, language, "actions.yml", logger)
        );
    }

    private static <T> T file(
            final Class<T> type,
            final Path directory,
            final String language,
            final String name,
            final Logger logger
    ) throws ConfigurateException {
        return ConfigFile.builder(type)
                .path(directory.resolve(name))
                .defaults(InferenceConfigs.class.getClassLoader(), language + "/" + name)
                .logger(logger)
                .build()
                .load();
    }

    private static String language() {
        final String language = System.getProperty("user.language", "ru").toLowerCase(Locale.ROOT);
        return language.equals("en") ? "en" : "ru";
    }

}
