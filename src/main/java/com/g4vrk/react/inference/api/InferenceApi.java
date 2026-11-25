package com.g4vrk.react.inference.api;

import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Read-only information about the models in the running inference RCP.
 *
 * <p>Implementations are provided by the inference RCP and registered through
 * {@code RCPEnvironment.services()}.</p>
 */
@ApiStatus.NonExtendable
public interface InferenceApi {

    /**
     * Returns an immutable snapshot of the configured model IDs.
     *
     * @return immutable list of model identities
     */
    @NotNull List<String> modelIds();

}
