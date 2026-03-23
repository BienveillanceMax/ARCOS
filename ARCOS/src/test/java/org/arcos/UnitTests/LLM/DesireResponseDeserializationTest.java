package org.arcos.UnitTests.LLM;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.arcos.LLM.Client.ResponseObject.DesireResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that @JsonProperty annotations on DesireResponse fields
 * correctly map JSON keys to Java fields during deserialization.
 */
class DesireResponseDeserializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
    }

    @Test
    void desireResponse_WithAllFields_ShouldDeserializeCorrectly() throws Exception {
        String json = """
                {
                  "label": "Explorer le monde",
                  "description": "Comprendre la diversité culturelle",
                  "intensity": 0.85
                }
                """;

        DesireResponse response = objectMapper.readValue(json, DesireResponse.class);

        assertEquals("Explorer le monde", response.getLabel());
        assertEquals("Comprendre la diversité culturelle", response.getDescription());
        assertEquals(0.85, response.getIntensity(), 0.001);
    }

    @Test
    void desireResponse_WithOnlyRequiredFields_ShouldDeserializeGracefully() throws Exception {
        String json = """
                {
                  "label": "Apprendre",
                  "intensity": 0.6
                }
                """;

        DesireResponse response = objectMapper.readValue(json, DesireResponse.class);

        assertEquals("Apprendre", response.getLabel());
        assertEquals(0.6, response.getIntensity(), 0.001);
        assertNull(response.getDescription());
    }
}
