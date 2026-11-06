package com.g4vrk.react.inference.config;

import java.util.List;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

/**
 * {@code actions.yml}: what {@code /react link} shows.
 */
@ConfigSerializable
public record ActionsConfig(Link link) {

    /**
     * @param checking   while a code is checked
     * @param requesting while the site is asked for a link
     * @param open       the link to open, with {@code %url%}, {@code %code%} and {@code %minutes%}
     * @param linked     once linked, with {@code %server%} and {@code %models%}
     * @param failed     when linking fails, with {@code %reason%}
     * @param status     the answer of {@code /react link check}, with {@code %server%} and {@code %models%}
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

}
