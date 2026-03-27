package org.arcos.E2IT;

import io.qdrant.client.grpc.Points;
import org.arcos.Memory.ConversationContext;
import org.arcos.Memory.LongTermMemory.Qdrant.QdrantClientProvider;
import org.arcos.Orchestrator.Orchestrator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import org.arcos.UserModel.BatchPipeline.Queue.ConversationPair;
import org.arcos.UserModel.BatchPipeline.Queue.QueuedConversation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Base class for all E2E integration tests.
 *
 * Prerequisites: docker compose up qdrant ollama memlistener-init
 *
 * Key design decisions:
 * - Orchestrator.start() (the event loop) is NOT triggered in @SpringBootTest —
 *   it is only called from ArcosApplication.main(). Tests use dispatch() directly.
 * - MockTTSCapture is set via ReflectionTestUtils because PiperEmbeddedTTSModule
 *   is new'd inside Orchestrator's constructor, not injected by Spring.
 * - @BeforeAll clears Qdrant collections (empty filter = matches all points).
 *   This preserves collection schema while wiping data.
 *
 * Tagging convention:
 * - All E2E test classes MUST use @Tag("e2e") at class level.
 * - Individual @Tag("requires-llm") on methods is deprecated for E2E —
 *   migrate to class-level @Tag("e2e") when touching each class.
 * - Both tags are excluded from standard `mvn test` via surefire excludedGroups.
 *
 * Boundaries (NOT "zero mock"):
 * - TTS: intercepted by MockTTSCapture (captures text, no audio playback).
 * - Embeddings: local all-MiniLM-L6-v2 (384 dims) via E2ETestConfig.
 * - Everything else is real: LLM Mistral, Qdrant, personality pipeline.
 */
@SpringBootTest
@ActiveProfiles("test-e2e")
@Import(E2ETestConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 30, unit = TimeUnit.SECONDS)
public abstract class BaseE2IT {

    @Autowired protected Orchestrator orchestrator;
    @Autowired protected QdrantClientProvider qdrantClientProvider;
    @Autowired protected ConversationContext conversationContext;

    protected final MockTTSCapture mockTTS = new MockTTSCapture();

    @BeforeAll
    void clearCollections() throws Exception {
        var client = qdrantClientProvider.getClient();
        // Empty filter matches all points — clears data without dropping collection schema
        Points.Filter matchAll = Points.Filter.newBuilder().build();
        for (String col : List.of("Memories", "Opinions", "Desires")) {
            try {
                client.deleteAsync(col, matchAll).get();
            } catch (Exception ignored) {
                // Collection may not exist yet on first run — that's fine
            }
        }
    }

    protected static QueuedConversation makeConversation(String... pairs) {
        List<ConversationPair> pairList = new ArrayList<>();
        for (int i = 0; i < pairs.length - 1; i += 2) {
            pairList.add(new ConversationPair(pairs[i], pairs[i + 1]));
        }
        return new QueuedConversation(UUID.randomUUID().toString(), pairList, LocalDateTime.now(), false);
    }

    @BeforeEach
    void injectMockTTSAndClear() {
        ReflectionTestUtils.setField(orchestrator, "ttsHandler", mockTTS);
        mockTTS.clear();
        conversationContext.startNewSession();
    }
}
