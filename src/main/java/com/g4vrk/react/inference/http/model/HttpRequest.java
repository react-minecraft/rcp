package com.g4vrk.react.inference.http.model;

import java.util.Map;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * A FlatBuffers window for a model; InferenceServer posts it with the FlatBuffers media type.
 */
@Value
public class HttpRequest {

    public static final String FLATBUFFERS = "application/x-flatbuffers";

    byte @NotNull [] body;
    @NotNull Map<String, String> headers;

    public HttpRequest(final byte @NotNull [] body) {
        this(body, Map.of());
    }

    public HttpRequest(final byte @NotNull [] body, final @NotNull Map<String, String> headers) {
        this.body = body;
        this.headers = Map.copyOf(headers);
    }
}
