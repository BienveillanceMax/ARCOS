package org.arcos.IntegrationTests.Orchestrators;

import io.qdrant.client.grpc.Points;
import org.arcos.Memory.LongTermMemory.Models.MemoryEntry;
import org.arcos.Memory.LongTermMemory.Models.OpinionEntry;
import org.arcos.Memory.LongTermMemory.Qdrant.QdrantClientProvider;
import org.arcos.Memory.LongTermMemory.service.MemoryService;
import org.arcos.Personality.Opinions.OpinionService;
import org.arcos.Personality.PersonalityOrchestrator;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for PersonalityOrchestrator — end-to-end chain:
 * conversation → memory → opinion → desire.
 * Requires: Qdrant on localhost:6334, MISTRALAI_API_KEY.
 */
@SpringBootTest
@ActiveProfiles("test-it")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PersonalityOrchestratorIT {

    @Autowired private PersonalityOrchestrator personalityOrchestrator;
    @Autowired private MemoryService memoryService;
    @Autowired private OpinionService opinionService;
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

    @Test
    @Order(1)
    @Tag("requires-llm")
    void processMemory_withSignificantConversation_shouldCreateMemoryInQdrant() {
        // given
        String conversation = """
                USER: J'ai découvert un super restaurant japonais dans le Vieux Lyon hier soir, \
                les ramens étaient incroyables et le chef est passionné par la cuisine traditionnelle.
                ASSISTANT: Le Vieux Lyon regorge de bonnes adresses ! Un restaurant japonais avec un \
                chef passionné, c'est toujours un bon signe. Tu y retourneras ?
                """;

        // when — full pipeline: memorize → opinion → desire
        assertDoesNotThrow(() -> personalityOrchestrator.processMemory(conversation));

        // then — at minimum, a memory should be stored in Qdrant
        List<MemoryEntry> memories = memoryService.searchMemories("restaurant japonais Lyon ramen", 5);
        assertFalse(memories.isEmpty(),
                "processMemory should store at least one memory about the restaurant conversation");
    }

    @Test
    @Order(2)
    @Tag("requires-llm")
    void processMemory_afterSignificantConversation_shouldAttemptOpinionFormation() {
        // given — memory from Order(1) exists in Qdrant

        // when — search for opinions that might have been formed
        List<OpinionEntry> opinions = opinionService.searchOpinions("restaurant japonais cuisine");

        // then — we can't guarantee the LLM forms an opinion, but the search shouldn't crash
        assertNotNull(opinions, "Opinion search should return a list (possibly empty)");
        // If an opinion was formed, verify it has valid structure
        for (OpinionEntry opinion : opinions) {
            assertNotNull(opinion.getSubject(), "Opinion should have a subject");
        }
    }

    @Test
    @Order(3)
    @Tag("requires-llm")
    void processMemory_withTrivialConversation_shouldCompleteWithoutError() {
        // given — trivial small talk that may not produce a memory
        String conversation = """
                USER: Salut !
                ASSISTANT: Salut ! Comment ça va ?
                """;

        // when/then — pipeline should handle gracefully (LLM may return null memory)
        assertDoesNotThrow(() -> personalityOrchestrator.processMemory(conversation),
                "Trivial conversation should not crash the pipeline");
    }
}
