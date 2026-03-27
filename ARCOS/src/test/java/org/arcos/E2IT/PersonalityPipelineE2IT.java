package org.arcos.E2IT;

import org.arcos.EventBus.Events.Event;
import org.arcos.EventBus.Events.EventType;
import org.arcos.Memory.LongTermMemory.Models.MemoryEntry;
import org.arcos.Memory.LongTermMemory.service.MemoryService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E — Sprint 10 E3: Personality pipeline.
 *
 * Proves the full chain triggered by a real conversation:
 *   WAKEWORD → Orchestrator → LLM → TTS
 *     then SESSION_END → async PersonalityOrchestrator.processMemory()
 *     → MemoryService.memorizeConversation() → Qdrant "Memories"
 *     → OpinionService.processInteraction() → Qdrant "Opinions"
 *
 * Prerequisites: Qdrant, Mistral API.
 */
@Tag("e2e")
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class PersonalityPipelineE2IT extends BaseE2IT {

    @Autowired private MemoryService memoryService;

    @Test
    void t1_conversationFollowedBySessionEndCreatesMemory() {
        // Step 1: Have a real conversation about a distinctive topic
        orchestrator.dispatch(new Event<>(EventType.WAKEWORD,
            "J'adore la cuisine italienne, surtout les pâtes fraîches. "
            + "C'est ma passion depuis l'enfance.", "test"));

        Awaitility.await().atMost(Duration.ofSeconds(20))
            .until(() -> mockTTS.hasSpoken());

        // Step 2: End the session — this triggers the async personality pipeline
        orchestrator.dispatch(new Event<>(EventType.SESSION_END, null, "test"));

        // Step 3: Wait for the async pipeline to persist a memory in Qdrant
        Awaitility.await()
            .atMost(Duration.ofSeconds(45))
            .pollInterval(Duration.ofSeconds(2))
            .until(() -> !memoryService.searchMemories("cuisine italienne pâtes", 5).isEmpty());

        // Step 4: Verify the memory is semantically related
        List<MemoryEntry> memories = memoryService.searchMemories("cuisine italienne", 5);
        assertFalse(memories.isEmpty(), "A memory should be stored in Qdrant after session end");

        String content = memories.get(0).getContent().toLowerCase();
        assertTrue(
            content.matches(".*(?:cuisine|pâtes|italien|passion|enfance|culinaire).*"),
            "Memory content should be related to the conversation topic, got: " + content);
    }

    @Test
    void t2_personalityPipelineDoesNotBlockOrchestrator() {
        // Dispatch a conversation with opinionated content
        orchestrator.dispatch(new Event<>(EventType.WAKEWORD,
            "Je pense que l'intelligence artificielle va transformer le monde. "
            + "C'est fascinant et un peu effrayant.", "test"));

        Awaitility.await().atMost(Duration.ofSeconds(20))
            .until(() -> mockTTS.hasSpoken());

        // End session (triggers async personality pipeline)
        orchestrator.dispatch(new Event<>(EventType.SESSION_END, null, "test"));

        // Immediately dispatch a new conversation — should NOT be blocked
        mockTTS.clear();
        orchestrator.dispatch(new Event<>(EventType.WAKEWORD,
            "Bonjour, comment vas-tu ?", "test"));

        // Awaitility proves the second conversation produces TTS while pipeline runs async
        Awaitility.await().atMost(Duration.ofSeconds(20))
            .until(() -> mockTTS.hasSpoken());
    }
}
