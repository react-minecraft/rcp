package com.g4vrk.react.inference.model;

import org.jetbrains.annotations.Nullable;

/**
 * What a model answered for one window, before the dispatcher names the model.
 *
 * @param probability the cheat probability in [0, 1]
 * @param threshold   the model's own decision threshold, or NaN to use the plugin's
 * @param enforcement false when the answer is only observed
 * @param runId       backend model run identifier, when supplied
 */
public record ModelAnswer(double probability, double threshold, boolean enforcement, @Nullable String runId) {

    public ModelAnswer(final double probability, final double threshold, final boolean enforcement) {

        this(probability, threshold, enforcement, null);

    }

}
