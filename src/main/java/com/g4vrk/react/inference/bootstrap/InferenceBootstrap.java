package com.g4vrk.react.inference.bootstrap;

import com.g4vrk.react.api.extension.ExtensionId;
import com.g4vrk.react.api.extension.bootstrap.ExtensionBootstrap;
import com.g4vrk.react.api.extension.bootstrap.ExtensionBootstrapContext;
import org.jetbrains.annotations.NotNull;

import java.net.URI;

/**
 * Declares the inference RCP's public package and private FlatBuffers runtime.
 */
public final class InferenceBootstrap implements ExtensionBootstrap {

    @Override
    public void bootstrap(final @NotNull ExtensionBootstrapContext context) {

        context.identify(ExtensionId.rcp("react-inference"), 1);
        context.exportPackage("com.g4vrk.react.inference.api");
        context.library(
                URI.create("https://repo.maven.apache.org/maven2/com/google/flatbuffers/flatbuffers-java/25.2.10/flatbuffers-java-25.2.10.jar"),
                "587bd6c31cda747587493a113bec8602d3a0b0ca579b2b1b838ef71b19e6525d"
        );

    }

}
