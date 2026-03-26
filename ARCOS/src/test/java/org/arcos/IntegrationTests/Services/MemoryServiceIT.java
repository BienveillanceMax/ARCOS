package org.arcos.IntegrationTests.Services;

import io.qdrant.client.grpc.Points;
import org.arcos.Memory.LongTermMemory.Models.MemoryEntry;
import org.arcos.Memory.LongTermMemory.Models.Subject;
import org.arcos.Memory.LongTermMemory.Qdrant.QdrantClientProvider;
import org.arcos.Memory.LongTermMemory.service.MemoryService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for MemoryService.
 * Requires: Qdrant on localhost:6334, MISTRALAI_API_KEY (for 1024-dim embeddings).
 */
@SpringBootTest
@ActiveProfiles("test-it")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MemoryServiceIT {

    @Autowired private MemoryService memoryService;
    @Autowired private QdrantClientProvider qdrantClientProvider;

    @BeforeAll
    void clearMemories() throws Exception {
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

    @Test
    @Order(1)
    void storeMemory_thenSearchBySimilarity_shouldReturnStoredEntry() {
        // given
        String id = UUID.randomUUID().toString();
        MemoryEntry entry = new MemoryEntry(id,
                "Pierre adore la musique jazz, surtout Miles Davis et John Coltrane",
                null, Subject.SELF, 0.8, LocalDateTime.now(), null);

        // when
        memoryService.storeMemory(entry);
        List<MemoryEntry> results = memoryService.searchMemories("musique jazz", 5);

        // then
        assertFalse(results.isEmpty(), "Search by similarity should return at least one result");
        assertTrue(results.stream().anyMatch(m -> id.equals(m.getId())),
                "Results should contain the stored jazz memory");
    }

    @Test
    @Order(2)
    void searchMemories_withUnrelatedQuery_shouldNotReturnStoredEntry() {
        // given — jazz memory stored in Order(1)

        // when
        List<MemoryEntry> results = memoryService.searchMemories("recette de gâteau au chocolat", 5);

        // then
        assertNotNull(results);
        assertTrue(results.stream().noneMatch(m -> m.getContent().toLowerCase().contains("jazz")),
                "Unrelated query should not return jazz memories in top results");
    }

    @Test
    @Order(3)
    void storeMultipleMemories_thenSearch_shouldRankRelevantFirst() {
        // given
        String jazzId = UUID.randomUUID().toString();
        String cookingId = UUID.randomUUID().toString();
        MemoryEntry jazz = new MemoryEntry(jazzId,
                "Le concert de jazz d'hier soir était magnifique, ambiance incroyable",
                null, Subject.SELF, 0.9, LocalDateTime.now(), null);
        MemoryEntry cooking = new MemoryEntry(cookingId,
                "Pierre a appris à faire des pâtes fraîches maison ce week-end",
                null, Subject.SELF, 0.6, LocalDateTime.now(), null);
        memoryService.storeMemory(jazz);
        memoryService.storeMemory(cooking);

        // when
        List<MemoryEntry> results = memoryService.searchMemories("concert de musique live", 5);

        // then
        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(m -> jazzId.equals(m.getId())),
                "Concert/music query should find the jazz concert memory");
    }

    @Test
    @Order(4)
    void getMemory_byId_shouldReturnCorrectEntry() {
        // given
        String id = UUID.randomUUID().toString();
        MemoryEntry entry = new MemoryEntry(id,
                "Pierre habite à Lyon depuis 3 ans",
                null, Subject.SELF, 0.5, LocalDateTime.now(), null);
        memoryService.storeMemory(entry);

        // when
        MemoryEntry retrieved = memoryService.getMemory(id);

        // then
        assertNotNull(retrieved, "Should retrieve memory by its ID");
        assertEquals(id, retrieved.getId());
        assertEquals(Subject.SELF, retrieved.getSubject());
    }

    @Test
    @Order(5)
    void deleteMemory_thenGetById_shouldReturnNull() {
        // given
        String id = UUID.randomUUID().toString();
        MemoryEntry entry = new MemoryEntry(id,
                "Souvenir temporaire à supprimer",
                null, Subject.SELF, 0.3, LocalDateTime.now(), null);
        memoryService.storeMemory(entry);
        assertNotNull(memoryService.getMemory(id), "Memory should exist before deletion");

        // when
        memoryService.deleteMemory(id);

        // then
        assertNull(memoryService.getMemory(id), "Memory should be null after deletion");
    }

    @Test
    @Order(6)
    void storeMemory_withCanonicalText_shouldUseCanonicalForEmbedding() {
        // given
        String id = UUID.randomUUID().toString();
        MemoryEntry entry = new MemoryEntry(id,
                "Il a dit qu'il aimait bien le vélo, surtout en montagne",
                "Pierre aime le cyclisme en montagne",
                Subject.SELF, 0.7, LocalDateTime.now(), null);
        memoryService.storeMemory(entry);

        // when
        List<MemoryEntry> results = memoryService.searchMemories("cyclisme montagne vélo", 5);

        // then
        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(m -> id.equals(m.getId())),
                "Should find the memory when searching by canonical text terms");
    }

    @Test
    @Order(7)
    @Tag("requires-llm")
    void memorizeConversation_shouldExtractAndStore() {
        // given
        int countBefore = memoryService.searchMemories("piano classique", 100).size();
        String conversation = """
                USER: Tu sais, j'ai commencé à apprendre le piano classique il y a deux mois, \
                je travaille sur une sonate de Mozart en ce moment.
                ASSISTANT: Mozart au piano, c'est un excellent choix pour débuter ! \
                La structure de ses sonates est idéale pour progresser.
                """;

        // when
        MemoryEntry result = memoryService.memorizeConversation(conversation);

        // then
        assertNotNull(result, "memorizeConversation should return a MemoryEntry");
        assertNotNull(result.getId());
        assertFalse(result.getContent().isBlank(), "Extracted content should not be blank");
        assertNotNull(result.getSubject());

        List<MemoryEntry> stored = memoryService.searchMemories("piano classique Mozart", 100);
        assertTrue(stored.size() > countBefore, "A new memory should be stored in Qdrant");
    }
}
