package com.g4vrk.react.inference.config;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies non-null validation of {@link ActionsConfig}, {@link ActionsConfig.TextSet},
 * and {@link ActionsConfig.ReasonSet}.
 */
final class InferenceConfigRegistryTest {

    @Test
    void validatesActionsTextSetAndReasonSetNonNull() {

        final ConnectionConfig connection = new ConnectionConfig(
                new ConnectionConfig.TimeoutConfig(Duration.ofSeconds(1), Duration.ofSeconds(5))
        );
        final FlowerConfig flower = new FlowerConfig(1);
        final VectorConfig vector = new VectorConfig(64L);
        final ActionsConfig.Link validLink = new ActionsConfig.Link(
                List.of("a"), List.of("b"), List.of("c"), List.of("d"), List.of("e"), List.of("f")
        );
        final ActionsConfig.ReasonSet validReasons = new ActionsConfig.ReasonSet("nl", "rep", "exp", "http");
        final ActionsConfig.TextSet validTexts = new ActionsConfig.TextSet("nl", "un", "en", "dis", ", ", "-", validReasons);

        assertDoesNotThrow(() -> InferenceConfigRegistry.validate(
                connection,
                flower,
                vector,
                new ActionsConfig(validLink, validTexts)
        ));
        assertThrows(
                IllegalArgumentException.class,
                () -> InferenceConfigRegistry.validate(connection, flower, vector, new ActionsConfig(null, validTexts))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> InferenceConfigRegistry.validate(connection, flower, vector, new ActionsConfig(validLink, null))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> InferenceConfigRegistry.validate(
                        connection,
                        flower,
                        vector,
                        new ActionsConfig(
                                validLink,
                                new ActionsConfig.TextSet("nl", "un", "en", "dis", ", ", "-", null)
                        )
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> InferenceConfigRegistry.validate(
                        connection,
                        flower,
                        vector,
                        new ActionsConfig(
                                validLink,
                                new ActionsConfig.TextSet(
                                        "nl",
                                        "un",
                                        "en",
                                        "dis",
                                        ", ",
                                        "-",
                                        new ActionsConfig.ReasonSet("nl", null, "exp", "http")
                                )
                        )
                )
        );

    }

}
