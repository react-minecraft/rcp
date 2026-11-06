package com.g4vrk.react.inference.config;

import java.time.Duration;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

/**
 * {@code connection.yml}: how long calls to the React site may take.
 */
@ConfigSerializable
public record ConnectionConfig(Timeouts timeouts) {

    @ConfigSerializable
    public record Timeouts(Duration connect, Duration call) {
    }

}
