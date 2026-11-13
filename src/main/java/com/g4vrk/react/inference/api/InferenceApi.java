package com.g4vrk.react.inference.api;

import java.util.List;

/**
 * Read-only information about the models in the running inference RCP.
 */
public interface InferenceApi {

    /** Returns an immutable snapshot of the configured model IDs. */
    List<String> modelIds();

}
