package org.arcos.E2E;

import org.arcos.Memory.LongTermMemory.Models.MemoryEntry;
import org.arcos.Memory.LongTermMemory.Models.OpinionEntry;
import org.arcos.Memory.LongTermMemory.Models.Subject;
import org.arcos.Memory.LongTermMemory.Models.DesireEntry;
import org.arcos.Memory.LongTermMemory.service.MemoryService;
import org.arcos.Personality.Desires.DesireService;
import org.arcos.Personality.Opinions.OpinionService;
import org.arcos.Personality.Values.Entities.DimensionSchwartz;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PersonalityE2IT extends BaseE2IT {

    @Autowired private OpinionService opinionService;
    @Autowired private DesireService desireService;
    @Autowired private MemoryService memoryService;

    // Helper: build a MemoryEntry directly (bypasses LLM extraction); uses a fresh UUID as ID
    private MemoryEntry storedMemory(String content) {
        MemoryEntry mem = new MemoryEntry(UUID.randomUUID().toString(), content, Subject.SELF, 0.7, LocalDateTime.now(), null);
        memoryService.storeMemory(mem);
        return mem;
    }

    // Helper: build an OpinionEntry with high confidence (for desire creation tests)
    private OpinionEntry highConfidenceOpinion(String canonicalText) {
        OpinionEntry op = new OpinionEntry();
        op.setCanonicalText(canonicalText);
        op.setSubject("technologie");
        op.setSummary(canonicalText);
        op.setPolarity(0.9);
        op.setConfidence(0.85);
        op.setStability(0.7);
        op.setMainDimension(DimensionSchwartz.OPENNESS_TO_CHANGE);
        op.setCreatedAt(LocalDateTime.now());
        op.setUpdatedAt(LocalDateTime.now());
        return op;
    }

    @Test
    @Tag("requires-llm")
    void t1_opinionFormationFromMemory() {
        MemoryEntry mem = storedMemory(
            "Pierre trouve l'évolution des IA très stimulante, il suit l'actualité tech avec enthousiasme");

        List<OpinionEntry> result = opinionService.processInteraction(mem);

        assertNotNull(result, "processInteraction returned null — LLM may have failed");
        assertFalse(result.isEmpty(), "Should have formed at least one opinion");
        OpinionEntry formed = result.get(0);
        assertNotNull(formed.getId());
        assertTrue(formed.getConfidence() >= 0.0 && formed.getConfidence() <= 1.0);
        assertTrue(formed.getPolarity() >= -1.0 && formed.getPolarity() <= 1.0);
        assertTrue(formed.getStability() >= 0.0 && formed.getStability() <= 1.0);
    }

    @Test
    @Tag("requires-llm")
    void t2_opinionReinforcementNoDuplicate() {
        // Store a known opinion directly
        OpinionEntry seed = highConfidenceOpinion(
            "L'intelligence artificielle est une avancée stimulante");
        seed.setId(UUID.randomUUID().toString());
        MemoryEntry seedMem = storedMemory("seed memory");
        opinionService.addOpinion(seed, seedMem);  // stores in Qdrant

        double confidenceBefore = seed.getConfidence();
        double polarityBefore   = seed.getPolarity();

        // Process a similar memory — should reinforce, not create duplicate
        MemoryEntry similar = storedMemory(
            "Les derniers modèles de langage sont impressionnants, les progrès sont rapides");
        List<OpinionEntry> result = opinionService.processInteraction(similar);

        assertNotNull(result, "processInteraction returned null — LLM may have failed");

        // Exactly one opinion in Qdrant (reinforcement, not addition)
        List<OpinionEntry> allOpinions = opinionService.searchOpinions("intelligence artificielle");
        assertEquals(1, allOpinions.size(), "Should still be exactly 1 opinion (reinforced, not duplicated)");

        // At least one numeric field changed
        OpinionEntry updated = allOpinions.get(0);
        boolean fieldChanged = updated.getConfidence() != confidenceBefore
            || updated.getPolarity() != polarityBefore;
        assertTrue(fieldChanged, "Reinforcement should change at least one numeric field");
    }

    @Test
    void t3_opinionRetrievalByUserMessage() {
        // Store a jazz opinion directly
        OpinionEntry jazzOp = new OpinionEntry();
        jazzOp.setCanonicalText("La musique jazz est une source de sérénité et d'équilibre");
        jazzOp.setSubject("musique jazz");
        jazzOp.setSummary("Le jazz apporte de la sérénité");
        jazzOp.setPolarity(0.8);
        jazzOp.setConfidence(0.6);
        jazzOp.setStability(0.6);
        jazzOp.setMainDimension(DimensionSchwartz.SELF_TRANSCENDENCE);
        jazzOp.setCreatedAt(LocalDateTime.now());
        jazzOp.setUpdatedAt(LocalDateTime.now());
        MemoryEntry mem = storedMemory("jazz memory");
        OpinionEntry stored = opinionService.addOpinion(jazzOp, mem);
        String jazzOpId = stored.getId(); // addOpinion always assigns a new UUID

        // Simulate: user message triggers opinion retrieval
        List<OpinionEntry> retrieved = opinionService.searchOpinions(
            "J'ai passé la soirée à écouter du jazz");

        assertFalse(retrieved.isEmpty(), "Should find opinions matching user's jazz message");
        assertTrue(retrieved.stream().anyMatch(o -> jazzOpId.equals(o.getId())),
            "Should retrieve the stored jazz opinion by ID");
    }

    @Test
    @Tag("requires-llm")
    void t4_desireCreationFromHighConfidenceOpinion() {
        OpinionEntry strongOp = highConfidenceOpinion(
            "Les nouvelles avancées en IA sont passionnantes et méritent d'être explorées");
        MemoryEntry mem = storedMemory("desire creation memory");
        opinionService.addOpinion(strongOp, mem);  // stores in Qdrant, returns stored entry with ID

        DesireEntry desire = desireService.processOpinion(strongOp);

        // May be null if intensity below threshold — check PersonalityProperties.desireCreateThreshold
        assertNotNull(desire, "Desire should be created for high-confidence opinion " +
            "(if null, check arcos.personality.desire-create-threshold — default is ~0.3)");
        assertNotNull(desire.getId());
        assertTrue(desire.getIntensity() > 0, "Desire intensity should be positive");
    }

    @Test
    @Tag("requires-llm")
    void t5_desireReinforcementNoDuplicate() {
        // Create initial desire via LLM
        OpinionEntry op = highConfidenceOpinion("Explorer les avancées en intelligence artificielle");
        MemoryEntry mem = storedMemory("desire reinforcement memory");
        opinionService.addOpinion(op, mem);
        DesireEntry initial = desireService.processOpinion(op);
        assumeNotNull(initial, "Initial desire must be created for this test to run");

        double intensityBefore = initial.getIntensity();

        // Set associatedDesire on opinion and update to trigger reinforcement path
        op.setAssociatedDesire(initial.getId());
        DesireEntry updated = desireService.processOpinion(op);

        assertNotNull(updated, "Updated desire should not be null");
        assertEquals(initial.getId(), updated.getId(), "Same desire should be updated (not duplicated)");
        assertNotEquals(intensityBefore, updated.getIntensity(),
            "Desire intensity should change on reinforcement");
    }

    // JUnit 5 helper — skip test cleanly rather than fail if precondition not met
    private void assumeNotNull(Object value, String message) {
        org.junit.jupiter.api.Assumptions.assumeTrue(value != null, message);
    }
}
