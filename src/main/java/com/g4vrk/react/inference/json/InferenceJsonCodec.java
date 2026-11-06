package com.g4vrk.react.inference.json;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON for the site's account endpoints (/react connect, key check); models speak FlatBuffers.
 */
public final class InferenceJsonCodec {

    private static final int MAX_NESTING_DEPTH = 64;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private InferenceJsonCodec() {
    }

    public static @NotNull String writeObject(final @NotNull Map<String, ?> values) {
        final StringBuilder output = new StringBuilder(256);
        appendObject(output, values, 0);
        return output.toString();
    }

    /**
     * Reads a JSON object: nested objects become maps, arrays lists, numbers doubles.
     */
    @SuppressWarnings("unchecked")
    public static @NotNull Map<String, Object> readObject(final @NotNull String json) {
        if (!(new Parser(json).readDocument() instanceof Map<?, ?> object)) {
            throw new IllegalArgumentException("Expected a JSON object");
        }
        return (Map<String, Object>) object;
    }

    private static void appendObject(
            final @NotNull StringBuilder output,
            final @NotNull Map<String, ?> values,
            final int depth
    ) {
        checkDepth(depth);
        output.append('{');

        boolean first = true;

        for (final Map.Entry<String, ?> entry : values.entrySet()) {
            if (!first) {
                output.append(',');
            }

            first = false;
            appendString(output, entry.getKey());
            output.append(':');
            appendValue(output, entry.getValue(), depth + 1);
        }

        output.append('}');
    }

    private static void appendValue(
            final @NotNull StringBuilder output,
            final Object value,
            final int depth
    ) {
        checkDepth(depth);

        if (value == null) {
            output.append("null");
        } else if (value instanceof String string) {
            appendString(output, string);
        } else if (value instanceof Boolean) {
            output.append(value);
        } else if (value instanceof Number number) {
            appendNumber(output, number);
        } else {
            throw new IllegalArgumentException(
                    "Unsupported inference JSON value type: " + value.getClass().getName()
            );
        }
    }

    private static void appendNumber(
            final @NotNull StringBuilder output,
            final @NotNull Number number
    ) {
        if ((number instanceof Double doubleValue && !Double.isFinite(doubleValue))
                || (number instanceof Float floatValue && !Float.isFinite(floatValue))) {
            throw new IllegalArgumentException("JSON numbers must be finite");
        }

        output.append(number);
    }

    private static void appendString(
            final @NotNull StringBuilder output,
            final @NotNull String value
    ) {
        output.append('"');

        for (int i = 0, length = value.length(); i < length; i++) {
            final char character = value.charAt(i);

            switch (character) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (character < 0x20) {
                        output.append("\\u");
                        output.append(HEX[(character >>> 12) & 0xF]);
                        output.append(HEX[(character >>> 8) & 0xF]);
                        output.append(HEX[(character >>> 4) & 0xF]);
                        output.append(HEX[character & 0xF]);
                    } else {
                        output.append(character);
                    }
                }
            }
        }

        output.append('"');
    }

    private static void checkDepth(final int depth) {
        if (depth > MAX_NESTING_DEPTH) {
            throw new IllegalArgumentException("JSON nesting depth exceeds " + MAX_NESTING_DEPTH);
        }
    }

    private static final class Parser {

        private final String input;
        private int index;

        private Parser(final @NotNull String input) {
            this.input = input;
        }

        private Object readDocument() {
            final Object value = readValue(1);
            skipWhitespace();

            if (hasRemaining()) {
                throw error("Unexpected trailing content");
            }

            return value;
        }

        private Object readValue(final int depth) {
            checkDepth(depth);
            skipWhitespace();

            if (!hasRemaining()) {
                throw error("Expected a JSON value");
            }

            switch (input.charAt(index)) {
                case '"':
                    return readString();
                case '{':
                    return readObjectValue(depth + 1);
                case '[':
                    return readArray(depth + 1);
                case 't':
                    readLiteral("true");
                    return Boolean.TRUE;
                case 'f':
                    readLiteral("false");
                    return Boolean.FALSE;
                case 'n':
                    readLiteral("null");
                    return null;
                default:
                    return readNumber().doubleValue();
            }
        }

        private Map<String, Object> readObjectValue(final int depth) {
            expect('{');
            final Map<String, Object> values = new LinkedHashMap<>();
            skipWhitespace();

            if (consume('}')) {
                return values;
            }

            while (true) {
                skipWhitespace();

                if (!hasRemaining() || input.charAt(index) != '"') {
                    throw error("Expected a string key");
                }

                final String key = readString();
                skipWhitespace();
                expect(':');
                values.put(key, readValue(depth + 1));
                skipWhitespace();

                if (consume('}')) {
                    return values;
                }

                expect(',');
            }
        }

        private List<Object> readArray(final int depth) {
            expect('[');
            final List<Object> values = new ArrayList<>();
            skipWhitespace();

            if (consume(']')) {
                return values;
            }

            while (true) {
                values.add(readValue(depth + 1));
                skipWhitespace();

                if (consume(']')) {
                    return values;
                }

                expect(',');
            }
        }

        private String readString() {
            expect('"');
            final StringBuilder value = new StringBuilder();

            while (hasRemaining()) {
                final char character = input.charAt(index++);

                if (character == '"') {
                    return value.toString();
                }

                if (character < 0x20) {
                    throw error("Unescaped control character in string");
                }

                if (character != '\\') {
                    value.append(character);
                    continue;
                }

                if (!hasRemaining()) {
                    throw error("Incomplete escape sequence");
                }

                final char escaped = input.charAt(index++);

                switch (escaped) {
                    case '"' -> value.append('"');
                    case '\\' -> value.append('\\');
                    case '/' -> value.append('/');
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> value.append(readUnicodeCharacter());
                    default -> throw error("Invalid escape sequence");
                }
            }

            throw error("Unterminated string");
        }

        private char readUnicodeCharacter() {
            if (index + 4 > input.length()) {
                throw error("Incomplete Unicode escape");
            }

            int value = 0;

            for (int i = 0; i < 4; i++) {
                final int digit = Character.digit(input.charAt(index++), 16);

                if (digit == -1) {
                    throw error("Invalid Unicode escape");
                }

                value = (value << 4) | digit;
            }

            return (char) value;
        }

        private void readLiteral(final @NotNull String literal) {
            if (!input.startsWith(literal, index)) {
                throw error("Invalid JSON literal");
            }

            index += literal.length();
        }

        private BigDecimal readNumber() {
            final int start = index;
            consume('-');

            if (consume('0')) {
                if (hasDigit()) {
                    throw error("Leading zero in number");
                }
            } else {
                readDigits(true);
            }

            if (consume('.')) {
                readDigits(true);
            }

            if (consume('e') || consume('E')) {
                if (!consume('+')) {
                    consume('-');
                }

                readDigits(true);
            }

            try {
                return new BigDecimal(input.substring(start, index));
            } catch (final NumberFormatException ex) {
                throw error("Invalid JSON number");
            }
        }

        private void readDigits(final boolean required) {
            final int start = index;

            while (hasDigit()) {
                index++;
            }

            if (required && start == index) {
                throw error("Expected a digit");
            }
        }

        private boolean hasDigit() {
            if (!hasRemaining()) {
                return false;
            }

            final char character = input.charAt(index);
            return character >= '0' && character <= '9';
        }

        private void skipWhitespace() {
            while (hasRemaining()) {
                final char character = input.charAt(index);

                if (character != ' ' && character != '\n'
                        && character != '\r' && character != '\t') {
                    return;
                }

                index++;
            }
        }

        private void expect(final char expected) {
            if (!consume(expected)) {
                throw error("Expected '" + expected + "'");
            }
        }

        private boolean consume(final char expected) {
            if (!hasRemaining() || input.charAt(index) != expected) {
                return false;
            }

            index++;
            return true;
        }

        private boolean hasRemaining() {
            return index < input.length();
        }

        private @NotNull IllegalArgumentException error(final @NotNull String message) {
            return new IllegalArgumentException(message + " at index " + index);
        }
    }
}
