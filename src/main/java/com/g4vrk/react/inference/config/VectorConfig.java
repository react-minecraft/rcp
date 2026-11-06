package com.g4vrk.react.inference.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

/**
 * {@code models/vector.yml}.
 *
 * @param combatTicks how many ticks after an attack the player still counts as fighting
 */
@ConfigSerializable
public record VectorConfig(long combatTicks) {
}
