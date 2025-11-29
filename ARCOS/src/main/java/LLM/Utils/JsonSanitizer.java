package LLM.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonSanitizer {

    private static final Logger logger = LoggerFactory.getLogger(JsonSanitizer.class);

    /**
     * Attempts to extract a valid JSON string from a potentially dirty response.
     * It looks for the first occurrence of '{' or '[' and the last occurrence of '}' or ']'.
     * It also strips Markdown code blocks like ```json ... ```.
     *
     * @param input The raw string from the LLM.
     * @return The sanitized JSON string, or the original input if no JSON structure is found.
     */
    public static String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        String trimmed = input.trim();

        // Remove Markdown code blocks if present
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > -1) {
                // Remove the first line (e.g., ```json)
                trimmed = trimmed.substring(firstNewline + 1);
            }
            // Remove the trailing ```
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
            trimmed = trimmed.trim();
        }

        // Find start and end of JSON content
        int jsonStart = -1;
        int jsonEnd = -1;

        int firstBrace = trimmed.indexOf('{');
        int firstBracket = trimmed.indexOf('[');

        if (firstBrace > -1 && firstBracket > -1) {
            jsonStart = Math.min(firstBrace, firstBracket);
        } else if (firstBrace > -1) {
            jsonStart = firstBrace;
        } else {
            jsonStart = firstBracket;
        }

        int lastBrace = trimmed.lastIndexOf('}');
        int lastBracket = trimmed.lastIndexOf(']');

        if (lastBrace > -1 && lastBracket > -1) {
            jsonEnd = Math.max(lastBrace, lastBracket);
        } else if (lastBrace > -1) {
            jsonEnd = lastBrace;
        } else {
            jsonEnd = lastBracket;
        }

        if (jsonStart > -1 && jsonEnd > -1 && jsonEnd > jsonStart) {
            String candidate = trimmed.substring(jsonStart, jsonEnd + 1);
            // Basic validation: brackets/braces must match types
            char startChar = trimmed.charAt(jsonStart);
            char endChar = trimmed.charAt(jsonEnd);

            boolean validObject = (startChar == '{' && endChar == '}');
            boolean validArray = (startChar == '[' && endChar == ']');

            if (validObject || validArray) {
                return candidate;
            } else {
                 logger.warn("Found JSON start/end but types mismatch or nesting is weird. Start: {}, End: {}", startChar, endChar);
                 // We try to return the substring anyway, letting Jackson decide if it's parsable,
                 // because sometimes we might have captured a nested object by mistake,
                 // but usually finding the first and last of any type is risky if they are mixed.
                 // Let's be safer: if we found { ... ], that's bad.
                 // But wait, if input is "Text { [ ... ] } Text", start='{', end='}' -> OK.
                 // If input is "Text [ { ... } ] Text", start='[', end=']' -> OK.

                 // If we have mixed content like "Here is an object: { ... } and a list: [ ... ]",
                 // we might grab from the first { to the last ]. That would be invalid JSON.
                 // For now, let's assume the LLM returns ONE main entity.
                 // If start is {, we look for last }.
                 if (startChar == '{') {
                     int realEnd = trimmed.lastIndexOf('}');
                     if (realEnd > jsonStart) return trimmed.substring(jsonStart, realEnd + 1);
                 } else if (startChar == '[') {
                     int realEnd = trimmed.lastIndexOf(']');
                     if (realEnd > jsonStart) return trimmed.substring(jsonStart, realEnd + 1);
                 }
            }
        }

        return trimmed;
    }
}
