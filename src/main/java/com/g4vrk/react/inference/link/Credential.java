package com.g4vrk.react.inference.link;

import java.security.KeyPair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What the site handed this server at link time: the access token and the Ed25519 key that signs account requests.
 * <p>
 * A token moved from an old {@code inference.yml} has no key; such a server stays bound to its IP until relinked.
 *
 * @param accessToken sent as the Bearer token and inside model requests
 * @param keys        the key the site knows the public half of, or null
 */
public record Credential(@NotNull String accessToken, @Nullable KeyPair keys) {

    public static @NotNull Credential unsigned(final @NotNull String accessToken) {
        return new Credential(accessToken, null);
    }

}
