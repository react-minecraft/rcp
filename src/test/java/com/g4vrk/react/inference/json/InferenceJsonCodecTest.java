package com.g4vrk.react.inference.json;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InferenceJsonCodecTest {

    @Test
    void escapesRequestStrings() {

        final String json = InferenceJsonCodec.writeObject(
                Map.of("name", "Steve \"The Brave\"\\\n")
        );

        assertTrue(json.contains("Steve \\\"The Brave\\\"\\\\\\n"));

    }

    @Test
    void readsNestedObjectsArraysAndLiterals() {

        final Map<String, Object> response = InferenceJsonCodec.readObject(
                "{\"server\":\"Lobby\",\"models\":[{\"id\":\"flower/baseline\",\"enabled\":true},null],"
                        + "\"cheat_probability\":0.875,\"detail\":\"a\\\"b\"}");

        assertEquals("Lobby", response.get("server"));
        assertEquals(0.875D, response.get("cheat_probability"));
        assertEquals("a\"b", response.get("detail"));
        assertEquals(List.of(Map.of("id", "flower/baseline", "enabled", true)), List.of(((List<?>) response.get("models")).get(0)));
        assertNull(((List<?>) response.get("models")).get(1));

    }

    @Test
    void rejectsMalformedJsonAndTrailingContent() {

        assertThrows(IllegalArgumentException.class, () -> InferenceJsonCodec.readObject("{\"probability\":}"));
        assertThrows(IllegalArgumentException.class, () -> InferenceJsonCodec.readObject("[1,2,3]"));
        assertThrows(IllegalArgumentException.class, () -> InferenceJsonCodec.readObject("{\"probability\":0.5} trailing"));

    }

    @Test
    void rejectsNonFiniteNumbers() {

        assertThrows(
                IllegalArgumentException.class,
                () -> InferenceJsonCodec.writeObject(Map.of("value", Double.NaN))
        );

    }

    @Test
    void writesNestedMapsAndListsSymmetrically() {

        final Map<String, Object> source = new LinkedHashMap<>();
        source.put("server", "Lobby");
        source.put("models", List.of(
                Map.of("id", "flower/baseline", "enabled", true),
                Map.of("id", "vector/preview", "enabled", false)
        ));
        source.put("nullable", Arrays.asList("a", null, 3.5D));

        final String json = InferenceJsonCodec.writeObject(source);
        final Map<String, Object> parsed = InferenceJsonCodec.readObject(json);

        assertEquals("Lobby", parsed.get("server"));
        assertEquals(
                List.of(
                        Map.of("id", "flower/baseline", "enabled", true),
                        Map.of("id", "vector/preview", "enabled", false)
                ),
                parsed.get("models")
        );
        assertEquals("a", ((List<?>) parsed.get("nullable")).get(0));
        assertNull(((List<?>) parsed.get("nullable")).get(1));
        assertEquals(3.5D, ((List<?>) parsed.get("nullable")).get(2));

    }

}
