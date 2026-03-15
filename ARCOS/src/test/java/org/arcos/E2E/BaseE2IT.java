package org.arcos.E2E;

import io.qdrant.client.grpc.Points;
import org.arcos.Memory.LongTermMemory.Qdrant.QdrantClientProvider;
import org.arcos.Orchestrator.Orchestrator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

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
 */
@SpringBootTest
@ActiveProfiles("test-e2e")
@Import(E2ETestConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseE2IT {

    @Autowired protected Orchestrator orchestrator;
    @Autowired protected QdrantClientProvider qdrantClientProvider;

    @Value("${arcos.qdrant.memories-collection}") protected String memoriesCollection;
    @Value("${arcos.qdrant.opinions-collection}")  protected String opinionsCollection;
    @Value("${arcos.qdrant.desires-collection}")   protected String desiresCollection;

    protected final MockTTSCapture mockTTS = new MockTTSCapture();

    @BeforeAll
    void clearCollections() throws Exception {
        var client = qdrantClientProvider.getClient();
        // Empty filter matches all points — clears data without dropping collection schema
        Points.Filter matchAll = Points.Filter.newBuilder().build();
        for (String col : List.of(memoriesCollection, opinionsCollection, desiresCollection)) {
            try {
                client.deleteAsync(col, matchAll).get();
            } catch (Exception ignored) {
                // Collection may not exist yet on first run — that's fine
            }
        }
    }

    @BeforeEach
    void injectMockTTSAndClear() {
        ReflectionTestUtils.setField(orchestrator, "ttsHandler", mockTTS);
        mockTTS.clear();
    }
}
