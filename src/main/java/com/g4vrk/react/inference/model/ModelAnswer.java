package com.g4vrk.react.inference.model;

/**
 * What a model answered for one window, before the dispatcher names the model.
 *
 * @param probability the cheat probability in [0, 1]
 * @param threshold   the model's own decision threshold, or NaN to use the plugin's
 * @param enforcement false when the answer is only observed
 */
public record ModelAnswer(double probability, double threshold, boolean enforcement) {
}
