package org.arcos.IntegrationTests.Services;

import io.qdrant.client.grpc.Points;
import org.arcos.Memory.LongTermMemory.Models.MemoryEntry;
import org.arcos.Memory.LongTermMemory.Models.OpinionEntry;
import org.arcos.Memory.LongTermMemory.Models.Subject;
import org.arcos.Memory.LongTermMemory.Qdrant.QdrantClientProvider;
import org.arcos.Memory.LongTermMemory.service.MemoryService;
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
 * Integration tests for OpinionService.
 * Requires: Qdrant on localhost:6334, MISTRALAI_API_KEY (for 1024-dim embeddings).
 * LLM-dependent tests tagged @Tag("requires-llm").
 */
@SpringBootTest
@ActiveProfiles("test-it")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OpinionServiceIT {

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
            } catch (Exception e) {
                System.err.println("Warning: failed to clear collection " + col + ": " + e.getMessage());
            }
        }
    }

    private MemoryEntry storedMemory(String content) {
        MemoryEntry mem = new MemoryEntry(UUID.randomUUID().toString(), content, null,
                Subject.SELF, 0.7, LocalDateTime.now(), null);
        memoryService.storeMemory(mem);
        return mem;
    }

    private OpinionEntry buildOpinion(String canonicalText, String subject, double polarity, double confidence) {
        OpinionEntry op = new OpinionEntry();
        op.setCanonicalText(canonicalText);
        op.setSubject(subject);
        op.setSummary(canonicalText);
        op.setPolarity(polarity);
        op.setConfidence(confidence);
        op.setStability(0.6);
        op.setMainDimension(DimensionSchwartz.OPENNESS_TO_CHANGE);
        op.setCreatedAt(LocalDateTime.now());
        op.setUpdatedAt(LocalDateTime.now());
        return op;
    }

    // --- addOpinion + searchOpinions ---

    @Test
    @Order(1)
    void addOpinion_shouldPersistInQdrant_andBeSearchable() {
        // given
        MemoryEntry mem = storedMemory("discussion sur l'open source");
        OpinionEntry opinion = buildOpinion(
                "Les logiciels open source sont essentiels pour l'innovation technologique",
                "open source", 0.85, 0.7);

        // when
        OpinionEntry stored = opinionService.addOpinion(opinion, mem);

        // then
        assertNotNull(stored.getId(), "addOpinion should assign an ID");
        assertTrue(stored.getStability() > 0, "Stability should be calculated");

        List<OpinionEntry> results = opinionService.searchOpinions("logiciel libre open source");
        assertFalse(results.isEmpty(), "Should find the stored opinion by semantic search");
        assertTrue(results.stream().anyMatch(o -> stored.getId().equals(o.getId())));
    }

    @Test
    @Order(2)
    void addOpinion_differentTopic_shouldCreateSeparateEntry() {
        // given
        MemoryEntry mem = storedMemory("discussion cuisine");
        OpinionEntry cuisineOpinion = buildOpinion(
                "La cuisine française est la meilleure gastronomie du monde",
                "gastronomie", 0.9, 0.8);

        // when
        OpinionEntry stored = opinionService.addOpinion(cuisineOpinion, mem);

        // then
        List<OpinionEntry> foodResults = opinionService.searchOpinions("cuisine gastronomie française");
        assertFalse(foodResults.isEmpty(), "Should find cuisine opinion");
        assertTrue(foodResults.stream().anyMatch(o -> stored.getId().equals(o.getId())));
    }

    @Test
    @Order(3)
    void searchOpinions_withUnrelatedQuery_shouldNotCrash() {
        // when
        List<OpinionEntry> results = opinionService.searchOpinions("météo à Tokyo demain");

        // then
        assertNotNull(results);
    }

    @Test
    @Order(4)
    void addOpinion_setsStability_basedOnValueProfile() {
        // given
        MemoryEntry mem = storedMemory("test stability calculation");
        OpinionEntry opinion = buildOpinion(
                "Test de la stabilité initiale d'une opinion",
                "stabilité", 0.5, 0.5);
        opinion.setMainDimension(DimensionSchwartz.CONSERVATION);

        // when
        OpinionEntry stored = opinionService.addOpinion(opinion, mem);

        // then
        assertNotNull(stored);
        assertTrue(stored.getStability() >= 0.0 && stored.getStability() <= 1.0,
                "Stability should be within [0, 1]");
    }

    // --- processInteraction (requires LLM) ---

    @Test
    @Order(10)
    @Tag("requires-llm")
    void processInteraction_shouldFormOpinionFromMemory() {
        // given
        MemoryEntry mem = storedMemory(
                "Pierre trouve que l'IA générative est une révolution pour la productivité des développeurs");

        // when
        List<OpinionEntry> result = opinionService.processInteraction(mem);

        // then
        assertNotNull(result, "processInteraction should not return null");
        assertFalse(result.isEmpty(), "Should form at least one opinion");
        OpinionEntry formed = result.get(0);
        assertNotNull(formed.getId());
        assertTrue(formed.getConfidence() >= 0.0 && formed.getConfidence() <= 1.0);
        assertTrue(formed.getPolarity() >= -1.0 && formed.getPolarity() <= 1.0);
        assertNotNull(formed.getCanonicalText(), "Opinion should have a canonicalText");
    }

    @Test
    @Order(11)
    @Tag("requires-llm")
    void processInteraction_shouldProduceDistinctCanonicalText() {
        // given
        MemoryEntry mem = storedMemory(
                "Pierre pense que le vélo électrique est un excellent moyen de transport en ville");

        // when
        List<OpinionEntry> result = opinionService.processInteraction(mem);

        // then
        Assumptions.assumeTrue(result != null && !result.isEmpty(),
                "LLM must produce an opinion for canonicalization test");
        OpinionEntry formed = result.get(0);
        assertNotNull(formed.getCanonicalText(), "Opinion should have a canonicalText");
        assertFalse(formed.getCanonicalText().isBlank(), "canonicalText should not be blank");
        assertNotEquals(formed.getCanonicalText(), formed.getSummary(),
                "canonicalText should be distinct from summary (actual canonicalization, not copy)");
    }

    @Test
    @Order(12)
    @Tag("requires-llm")
    void processInteraction_similarTopic_shouldUpdateNotDuplicate() {
        // given — seed an opinion on AI
        MemoryEntry seedMem = storedMemory("seed memory for deduplication test");
        OpinionEntry seed = buildOpinion(
                "L'intelligence artificielle est passionnante", "IA", 0.8, 0.7);
        OpinionEntry storedSeed = opinionService.addOpinion(seed, seedMem);

        // when — process a similar memory
        MemoryEntry similar = storedMemory(
                "Les avancées en intelligence artificielle sont vraiment impressionnantes");
        List<OpinionEntry> result = opinionService.processInteraction(similar);

        // then
        assertNotNull(result);
        List<OpinionEntry> allAiOpinions = opinionService.searchOpinions("intelligence artificielle");
        assertTrue(allAiOpinions.size() <= 3,
                "Should not create many duplicates for semantically similar topics");
        assertTrue(allAiOpinions.stream().anyMatch(o -> storedSeed.getId().equals(o.getId())),
                "Original seed opinion should still exist (updated, not replaced)");
    }
}
