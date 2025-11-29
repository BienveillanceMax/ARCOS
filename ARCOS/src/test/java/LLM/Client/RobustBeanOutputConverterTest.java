package LLM.Client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RobustBeanOutputConverterTest {

    @Test
    void testConvertWithMarkdown() {
        ObjectMapper mapper = new ObjectMapper();
        RobustBeanOutputConverter<TestRecord> converter = new RobustBeanOutputConverter<>(TestRecord.class, mapper);

        String dirtyJson = "```json\n{\"value\":\"test\"}\n```";

        TestRecord result = converter.convert(dirtyJson);
        assertEquals("test", result.value());
    }

    @Test
    void testConvertWithChatter() {
        ObjectMapper mapper = new ObjectMapper();
        RobustBeanOutputConverter<TestRecord> converter = new RobustBeanOutputConverter<>(TestRecord.class, mapper);

        String dirtyJson = "Here is the result: {\"value\":\"test\"} hope it works";

        TestRecord result = converter.convert(dirtyJson);
        assertEquals("test", result.value());
    }

    // Simple record for testing
    record TestRecord(String value) {}
}
