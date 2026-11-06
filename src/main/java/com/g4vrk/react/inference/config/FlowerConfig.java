package com.g4vrk.react.inference.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

/**
 * {@code models/flower.yml}.
 *
 * @param step how many new rotations a player makes between two Flower requests
 */
@ConfigSerializable
public record FlowerConfig(int step) {
}
