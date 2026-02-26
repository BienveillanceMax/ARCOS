package org.arcos.UnitTests.LLM;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.arcos.LLM.Client.ResponseObject.DesireResponse;
import org.arcos.Memory.LongTermMemory.Models.DesireEntry;
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
                  "intensity": 0.85,
                  "reasoning": "Curiosité intellectuelle intense",
                  "status": "PENDING"
                }
                """;

        DesireResponse response = objectMapper.readValue(json, DesireResponse.class);

        assertEquals("Explorer le monde", response.getLabel());
        assertEquals("Comprendre la diversité culturelle", response.getDescription());
        assertEquals(0.85, response.getIntensity(), 0.001);
        assertEquals("Curiosité intellectuelle intense", response.getReasoning());
        assertEquals(DesireEntry.Status.PENDING, response.getStatus());
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
        assertNull(response.getReasoning());
        assertNull(response.getStatus());
    }

    @Test
    void desireResponse_WithSatisfiedStatus_ShouldDeserializeStatus() throws Exception {
        String json = """
                {
                  "label": "Objectif atteint",
                  "description": "C'est fait",
                  "intensity": 0.0,
                  "reasoning": "Terminé",
                  "status": "SATISFIED"
                }
                """;

        DesireResponse response = objectMapper.readValue(json, DesireResponse.class);

        assertEquals(DesireEntry.Status.SATISFIED, response.getStatus());
        assertEquals(0.0, response.getIntensity(), 0.001);
    }
}
