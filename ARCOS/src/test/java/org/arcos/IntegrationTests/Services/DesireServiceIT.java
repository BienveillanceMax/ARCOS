package org.arcos.IntegrationTests.Services;

import io.qdrant.client.grpc.Points;
import org.arcos.Memory.LongTermMemory.Models.DesireEntry;
import org.arcos.Memory.LongTermMemory.Models.MemoryEntry;
import org.arcos.Memory.LongTermMemory.Models.OpinionEntry;
import org.arcos.Memory.LongTermMemory.Models.Subject;
import org.arcos.Memory.LongTermMemory.Qdrant.QdrantClientProvider;
import org.arcos.Memory.LongTermMemory.service.MemoryService;
import org.arcos.Personality.Desires.DesireService;
import org.arcos.Personality.Opinions.OpinionService;
import org.arcos.Personality.Values.Entities.DimensionSchwartz;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for DesireService.
 * Requires: Qdrant on localhost:6334, MISTRALAI_API_KEY (for 1024-dim embeddings).
 * LLM-dependent tests tagged @Tag("requires-llm").
 */
@SpringBootTest
@ActiveProfiles("test-it")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DesireServiceIT {

    @Autowired private DesireService desireService;
    @Autowired private OpinionService opinionService;
    @Autowired private MemoryService memoryService;
    @Autowired private QdrantClientProvider qdrantClientProvider;

    @BeforeAll
    void clearCollections() throws Exception {
        var client = qdrantClientProvider.getClient();
        Points.Filter matchAll = Points.Filter.newBuilder().build();
        for (String col : List.of("Memories", "Opinions", "Desires")) {
            try {
                client.deleteAsync(col, matchAll).get();
            } catch (Exception ignored) {}
        }
    }

    private MemoryEntry storedMemory(String content) {
        MemoryEntry mem = new MemoryEntry(UUID.randomUUID().toString(), content, null,
                Subject.SELF, 0.7, LocalDateTime.now(), null);
        memoryService.storeMemory(mem);
        return mem;
    }

    private OpinionEntry buildHighConfidenceOpinion(String canonicalText) {
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

    // --- storeDesire + getPendingDesires ---

    @Test
    @Order(1)
    void storeDesire_thenGetPending_shouldReturnIt() {
        // given
        DesireEntry desire = new DesireEntry();
        desire.setId(UUID.randomUUID().toString());
        desire.setLabel("Explorer les nouvelles technologies IA");
        desire.setCanonicalText("Je veux explorer les nouvelles technologies d'intelligence artificielle");
        desire.setDescription("Découvrir et expérimenter les derniers modèles de langage");
        desire.setReasoning("Passion pour l'IA et curiosité technologique");
        desire.setIntensity(0.9);
        desire.setStatus(DesireEntry.Status.PENDING);
        desire.setOpinionId(UUID.randomUUID().toString());
        desire.setCreatedAt(LocalDateTime.now());
        desire.setLastUpdated(LocalDateTime.now());

        // when
        desireService.storeDesire(desire);
        List<DesireEntry> pending = desireService.getPendingDesires();

        // then
        assertFalse(pending.isEmpty(), "Should have at least one PENDING desire");
        assertTrue(pending.stream().anyMatch(d -> desire.getId().equals(d.getId())),
                "Should find the stored desire by ID");
    }

    @Test
    @Order(2)
    void storeDesire_withSatisfiedStatus_shouldNotAppearInPending() {
        // given
        DesireEntry desire = new DesireEntry();
        desire.setId(UUID.randomUUID().toString());
        desire.setLabel("Un désir déjà satisfait");
        desire.setCanonicalText("Un désir déjà satisfait et accompli");
        desire.setDescription("Ce désir a été réalisé");
        desire.setReasoning("Test");
        desire.setIntensity(0.5);
        desire.setStatus(DesireEntry.Status.SATISFIED);
        desire.setOpinionId(UUID.randomUUID().toString());
        desire.setCreatedAt(LocalDateTime.now());
        desire.setLastUpdated(LocalDateTime.now());

        // when
        desireService.storeDesire(desire);
        List<DesireEntry> pending = desireService.getPendingDesires();

        // then
        assertFalse(pending.stream().anyMatch(d -> desire.getId().equals(d.getId())),
                "SATISFIED desire should NOT appear in pending desires");
    }

    // --- processOpinion: update path (no LLM needed) ---

    @Test
    @Order(3)
    void processOpinion_withExistingDesire_shouldUpdateIntensity() {
        // given — store a desire, then link an opinion to it
        String desireId = UUID.randomUUID().toString();
        DesireEntry existingDesire = new DesireEntry();
        existingDesire.setId(desireId);
        existingDesire.setLabel("Apprendre la guitare");
        existingDesire.setCanonicalText("Je veux apprendre à jouer de la guitare");
        existingDesire.setDescription("Désir d'apprentissage musical");
        existingDesire.setReasoning("Amour de la musique");
        existingDesire.setIntensity(0.7);
        existingDesire.setStatus(DesireEntry.Status.PENDING);
        existingDesire.setOpinionId(UUID.randomUUID().toString());
        existingDesire.setCreatedAt(LocalDateTime.now());
        existingDesire.setLastUpdated(LocalDateTime.now());
        desireService.storeDesire(existingDesire);

        // Opinion linked to this desire (associatedDesire set → update path, no LLM call)
        OpinionEntry opinion = buildHighConfidenceOpinion("La musique est essentielle pour le bien-être");
        opinion.setId(UUID.randomUUID().toString());
        opinion.setAssociatedDesire(desireId);
        MemoryEntry mem = storedMemory("discussion musique");
        opinionService.addOpinion(opinion, mem);

        // when
        DesireEntry updated = desireService.processOpinion(opinion);

        // then
        assertNotNull(updated, "Update path should return the updated desire");
        assertEquals(desireId, updated.getId(), "Should update the existing desire, not create a new one");
        assertNotEquals(0.7, updated.getIntensity(), 0.001,
                "Intensity should change after update");
    }

    @Test
    @Order(4)
    void processOpinion_lowIntensity_shouldNotCreateDesire() {
        // given — opinion with very low polarity → low intensity → below threshold
        OpinionEntry weakOpinion = new OpinionEntry();
        weakOpinion.setCanonicalText("Observation neutre sans importance");
        weakOpinion.setSubject("rien");
        weakOpinion.setSummary("Observation neutre");
        weakOpinion.setPolarity(0.05);
        weakOpinion.setConfidence(0.1);
        weakOpinion.setStability(0.1);
        weakOpinion.setMainDimension(DimensionSchwartz.CONSERVATION);
        weakOpinion.setCreatedAt(LocalDateTime.now());
        weakOpinion.setUpdatedAt(LocalDateTime.now());
        weakOpinion.setId(UUID.randomUUID().toString());

        MemoryEntry mem = storedMemory("test low intensity");
        opinionService.addOpinion(weakOpinion, mem);

        // when
        DesireEntry result = desireService.processOpinion(weakOpinion);

        // then
        assertNull(result, "Low-intensity opinion should not create a desire");
    }

    // --- processOpinion: creation path (requires LLM) ---

    @Test
    @Order(10)
    @Tag("requires-llm")
    void processOpinion_highIntensity_shouldCreateDesire() {
        // given
        OpinionEntry strongOpinion = buildHighConfidenceOpinion(
                "Les avancées en robotique méritent un investissement personnel important");
        MemoryEntry mem = storedMemory("desire creation from strong opinion");
        OpinionEntry stored = opinionService.addOpinion(strongOpinion, mem);

        // when
        DesireEntry desire = desireService.processOpinion(stored);

        // then
        assertNotNull(desire, "High-intensity opinion should create a desire " +
                "(if null, check arcos.personality.desire-create-threshold)");
        assertNotNull(desire.getId());
        assertTrue(desire.getIntensity() > 0, "Desire intensity should be positive");
        assertNotNull(desire.getLabel(), "Desire should have a label");
        assertEquals(stored.getId(), desire.getOpinionId(), "Desire should reference the source opinion");
    }

    @Test
    @Order(11)
    @Tag("requires-llm")
    void processOpinion_highIntensity_shouldPersistInQdrant() {
        // given
        OpinionEntry opinion = buildHighConfidenceOpinion(
                "L'exploration spatiale est fondamentale pour l'avenir de l'humanité");
        MemoryEntry mem = storedMemory("space exploration desire test");
        OpinionEntry stored = opinionService.addOpinion(opinion, mem);

        // when
        DesireEntry desire = desireService.processOpinion(stored);
        Assumptions.assumeTrue(desire != null, "Desire must be created for persistence test");

        // then
        List<DesireEntry> pending = desireService.getPendingDesires();
        assertTrue(pending.stream().anyMatch(d -> desire.getId().equals(d.getId())),
                "Created desire should be retrievable from Qdrant via getPendingDesires");
    }
}
