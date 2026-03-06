package org.arcos.UnitTests.UserModel;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.arcos.UserModel.Consolidation.Models.ConsolidationAction;
import org.arcos.UserModel.Consolidation.Models.ConsolidationDecision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConsolidationDecisionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeserializeFromQwenJsonResponse() throws Exception {
        // Given
        String json = """
                {
                    "decision": "MERGE",
                    "winner_id": "abc-123",
                    "merge_target_id": "def-456",
                    "new_text": "Mon créateur aime le café noir.",
                    "target_branch": "IDENTITE",
                    "confidence": 0.85,
                    "reasoning": "Les deux observations décrivent la même préférence."
                }
                """;

        // When
        ConsolidationDecision decision = objectMapper.readValue(json, ConsolidationDecision.class);

        // Then
        assertEquals(ConsolidationAction.MERGE, decision.decision());
        assertEquals("abc-123", decision.winnerId());
        assertEquals("def-456", decision.mergeTargetId());
        assertEquals("Mon créateur aime le café noir.", decision.newText());
        assertEquals("IDENTITE", decision.targetBranch());
        assertEquals(0.85, decision.confidence(), 0.001);
        assertEquals("Les deux observations décrivent la même préférence.", decision.reasoning());
    }

    @Test
    void shouldDeserializeMinimalResponse() throws Exception {
        // Given — simple merge response with only decision + confidence
        String json = """
                {
                    "decision": "KEEP_BOTH",
                    "confidence": 0.4
                }
                """;

        // When
        ConsolidationDecision decision = objectMapper.readValue(json, ConsolidationDecision.class);

        // Then
        assertEquals(ConsolidationAction.KEEP_BOTH, decision.decision());
        assertEquals(0.4, decision.confidence(), 0.001);
        assertNull(decision.winnerId());
        assertNull(decision.newText());
        assertNull(decision.reasoning());
    }

    @Test
    void shouldIgnoreUnknownFields() throws Exception {
        // Given — Qwen may include unexpected fields
        String json = """
                {
                    "decision": "ARCHIVE",
                    "confidence": 0.9,
                    "extra_field": "should be ignored"
                }
                """;

        // When
        ConsolidationDecision decision = objectMapper.readValue(json, ConsolidationDecision.class);

        // Then
        assertEquals(ConsolidationAction.ARCHIVE, decision.decision());
        assertEquals(0.9, decision.confidence(), 0.001);
    }
}
