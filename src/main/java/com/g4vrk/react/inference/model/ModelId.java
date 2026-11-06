package com.g4vrk.react.inference.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The models a server can run; each one is enabled on the server's page on the site.
 */
public enum ModelId {

    FLOWER_BASELINE("flower", "baseline", "Flower Baseline", 40, 10),
    FLOWER_ULTRA("flower", "ultra", "Flower Ultra", 40, 10),
    VECTOR_PREVIEW("vector", "preview", "Vector Preview", 64, 32);

    private final String family;
    private final String modelName;
    private final String displayName;
    private final int window;
    private final int step;

    ModelId(String family, String modelName, String displayName, int window, int step) {
        this.family = family;
        this.modelName = modelName;
        this.displayName = displayName;
        this.window = window;
        this.step = step;
    }

    public @NotNull String family() {
        return family;
    }

    public @NotNull String modelName() {
        return modelName;
    }

    public @NotNull String displayName() {
        return displayName;
    }

    public int window() {
        return window;
    }

    public int step() {
        return step;
    }

    /**
     * {@code family/name}, the form the site and the plugin name a model by.
     */
    public @NotNull String identity() {
        return family + "/" + modelName;
    }

    /**
     * A newer backend may list models this build does not know about.
     */
    public static @Nullable ModelId fromIdentity(final @Nullable String identity) {
        for (final ModelId model : values()) {
            if (model.identity().equals(identity)) {
                return model;
            }
        }
        return null;
    }

}
