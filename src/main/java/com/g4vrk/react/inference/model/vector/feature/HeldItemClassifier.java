package com.g4vrk.react.inference.model.vector.feature;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Maps platform-neutral item properties to the Vector model's item classes.
 */
public final class HeldItemClassifier {

    private HeldItemClassifier() {

        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");

    }

    public static int classify(
            final @NotNull String itemId,
            final boolean edible,
            final boolean block
    ) {

        final int separator = itemId.indexOf(':');
        final String name = itemId.substring(separator + 1).toUpperCase(Locale.ROOT);
        if (name.equals("AIR")) {
            return 1;
        }
        return switch (name) {
            case "END_CRYSTAL" -> 3;
            case "RESPAWN_ANCHOR" -> 5;
            case "GLOWSTONE" -> 6;
            case "TOTEM_OF_UNDYING" -> 7;
            case "SHIELD" -> 12;
            case "OBSIDIAN", "CRYING_OBSIDIAN", "BEDROCK" -> 4;
            case "BOW", "CROSSBOW", "TRIDENT" -> 9;
            case "ENDER_PEARL", "SNOWBALL", "EGG" -> 10;
            default -> classifyOther(name, edible, block);
        };

    }

    private static int classifyOther(final String name, final boolean edible, final boolean block) {

        if (name.endsWith("_SWORD") || name.endsWith("_AXE")) {
            return 2;
        }
        if (edible) {
            return 11;
        }
        return block ? 8 : 0;

    }

}
