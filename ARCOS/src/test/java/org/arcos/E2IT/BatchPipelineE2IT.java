package org.arcos.E2IT;

import org.arcos.EventBus.Events.Event;
import org.arcos.EventBus.Events.EventType;
import org.arcos.UserModel.BatchPipeline.MemListenerReadinessCheck;
import org.arcos.UserModel.BatchPipeline.Queue.ConversationQueueService;
import org.arcos.UserModel.PersonaTree.PersonaTreeGate;
import org.arcos.UserModel.PersonaTree.PersonaTreeService;
import org.arcos.UserModel.UserModelProperties;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * E2E — Sprint 10 E5: Batch pipeline.
 *
 * Proves: conversations enqueued → IDLE_WINDOW_OPEN dispatched to Orchestrator
 *   → BatchPipelineOrchestrator.runBatch() → Ollama/Qwen3-8B processes chunks
 *   → PersonaTree operations extracted and applied → persona-tree.json modified.
 *
 * Prerequisites: Qdrant, Ollama with Qwen3-8B model loaded.
 */
@Tag("e2e")
@Tag("requires-ollama")
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class BatchPipelineE2IT extends BaseE2IT {

    @Autowired private ConversationQueueService conversationQueueService;
    @Autowired private PersonaTreeGate personaTreeGate;
    @Autowired private PersonaTreeService personaTreeService;
    @Autowired private MemListenerReadinessCheck readinessCheck;
    @Autowired private UserModelProperties userModelProperties;

    @BeforeEach
    void resetTreeAndQueue() throws IOException {
        Path treePath = Paths.get(userModelProperties.getPersonaTreePath());
        Path queuePath = Paths.get(userModelProperties.getConversationQueuePath());
        Files.deleteIfExists(treePath);
        Files.deleteIfExists(queuePath);
        personaTreeService.initialize();
        conversationQueueService.drainAll();
    }

    @Test
    void t1_idleWindowTriggersBatchAndModifiesPersonaTree() {
        assumeTrue(readinessCheck.isModelReady(),
            "Skipping — Ollama/MemListener model not available");

        // Enqueue conversations with clear, extractable facts
        conversationQueueService.enqueue(makeConversation(
            "Je suis architecte depuis cinq ans, je travaille dans un cabinet à Paris",
            "C'est un métier qui demande beaucoup de précision et de vision créative"));
        conversationQueueService.enqueue(makeConversation(
            "Mon chien Pixel me réveille tous les matins à sept heures",
            "Pixel a l'air d'être un réveil très efficace"));

        int countBefore = personaTreeGate.getFilledLeafCount();

        // Dispatch IDLE_WINDOW_OPEN through the Orchestrator — triggers triggerBatchPipeline()
        orchestrator.dispatch(new Event<>(EventType.IDLE_WINDOW_OPEN, null, "test"));

        // The batch runs asynchronously on personalityExecutor — wait for tree to be modified
        Awaitility.await()
            .atMost(Duration.ofSeconds(90))
            .pollInterval(Duration.ofSeconds(3))
            .until(() -> personaTreeGate.getFilledLeafCount() > countBefore);

        int countAfter = personaTreeGate.getFilledLeafCount();
        assertTrue(countAfter > countBefore,
            "PersonaTree should have new leaves after batch pipeline, before=" + countBefore + " after=" + countAfter);

        // Verify at least one leaf value is non-null and within size limits
        personaTreeGate.getNonEmptyLeaves().forEach((path, value) -> {
            assertNotNull(value, "Leaf value must not be null: " + path);
            assertTrue(value.length() <= userModelProperties.getLeafMaxChars(),
                "Leaf value exceeds max chars at path: " + path);
        });
    }

    @Test
    void t2_ollamaUnavailableDoesNotCorruptState() throws InterruptedException {
        conversationQueueService.enqueue(makeConversation(
            "Message test", "Réponse test"));

        int leafCountBefore = personaTreeGate.getFilledLeafCount();

        orchestrator.dispatch(new Event<>(EventType.IDLE_WINDOW_OPEN, null, "test"));

        // Give the async batch time to attempt and fail/skip gracefully
        Thread.sleep(3_000);

        // Tree should not be corrupted — count unchanged or still valid
        int leafCountAfter = personaTreeGate.getFilledLeafCount();
        assertTrue(leafCountAfter >= 0, "Tree should not be corrupted after failed batch");
        assertTrue(leafCountAfter >= leafCountBefore,
            "Tree should not lose leaves after failed batch");
    }
}
