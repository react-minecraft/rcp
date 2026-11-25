package com.g4vrk.react.inference.http.model;

import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * A FlatBuffers window payload and extra headers for a model inference request.
 *
 * @param body    encoded FlatBuffers payload bytes
 * @param headers extra HTTP headers attached to the request
 */
public record InferencePayload(
        byte @NotNull [] body,
        @NotNull Map<String, String> headers
) {

    public static final String FLATBUFFERS = "application/x-flatbuffers";

    public InferencePayload {

        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(headers, "headers");

    }

    public InferencePayload(final byte @NotNull [] body) {

        this(body, Map.of());

    }

}
