package com.g4vrk.react.inference.server;

import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that plain HTTP does not attempt an unsupported HTTP/2 upgrade.
 */
class InferenceServerHttpVersionTest {

    @Test
    void selectsVersionForBackendScheme() {

        assertEquals(HttpClient.Version.HTTP_1_1, InferenceServer.httpVersionFor("http://127.0.0.1:8000"));
        assertEquals(HttpClient.Version.HTTP_1_1, InferenceServer.httpVersionFor("HTTP://127.0.0.1"));
        assertEquals(HttpClient.Version.HTTP_2, InferenceServer.httpVersionFor("https://example.invalid"));

    }

}
