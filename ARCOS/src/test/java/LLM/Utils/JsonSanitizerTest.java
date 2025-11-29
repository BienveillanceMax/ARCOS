package LLM.Utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class JsonSanitizerTest {

    @Test
    void testCleanJsonRemainsUnchanged() {
        String input = "{\"key\": \"value\"}";
        String result = JsonSanitizer.sanitize(input);
        assertEquals(input, result);
    }

    @Test
    void testMarkdownBlockRemoved() {
        String input = "```json\n{\"key\": \"value\"}\n```";
        String result = JsonSanitizer.sanitize(input);
        assertEquals("{\"key\": \"value\"}", result);
    }

    @Test
    void testMarkdownBlockWithTextAround() {
        String input = "Here is the json:\n```json\n{\"key\": \"value\"}\n```\nHope it helps.";
        String result = JsonSanitizer.sanitize(input);
        assertEquals("{\"key\": \"value\"}", result);
    }

    @Test
    void testNoMarkdownButTextAround() {
        String input = "Here is the json: {\"key\": \"value\"} Thanks.";
        String result = JsonSanitizer.sanitize(input);
        assertEquals("{\"key\": \"value\"}", result);
    }

    @Test
    void testArraySupport() {
        String input = "Some list: [1, 2, 3]";
        String result = JsonSanitizer.sanitize(input);
        assertEquals("[1, 2, 3]", result);
    }

    @Test
    void testNestedObjects() {
        String input = "Response: {\"data\": {\"id\": 1}} End.";
        String result = JsonSanitizer.sanitize(input);
        assertEquals("{\"data\": {\"id\": 1}}", result);
    }

    @Test
    void testArrayOfObjects() {
         String input = "List:\n[{\"id\":1}, {\"id\":2}]\nDone.";
         String result = JsonSanitizer.sanitize(input);
         assertEquals("[{\"id\":1}, {\"id\":2}]", result);
    }

    @Test
    void testEmptyOrNull() {
        assertNull(JsonSanitizer.sanitize(null));
        assertEquals("", JsonSanitizer.sanitize(""));
        assertEquals("   ", JsonSanitizer.sanitize("   ")); // Should probably remain as is if no JSON found
    }

    @Test
    void testNoJsonStructure() {
        String input = "Just some text without brackets.";
        String result = JsonSanitizer.sanitize(input);
        assertEquals("Just some text without brackets.", result);
    }

    @Test
    void testMarkdownWithoutJsonIdentifier() {
        String input = "```\n{\"a\":1}\n```";
        String result = JsonSanitizer.sanitize(input);
        assertEquals("{\"a\":1}", result);
    }
}
