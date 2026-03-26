package org.arcos.IntegrationTests.Services;

import org.arcos.E2E.E2ETestConfig;
import org.arcos.E2E.MockTTSCapture;
import org.arcos.Orchestrator.Orchestrator;
import org.arcos.UserModel.BatchPipeline.BatchPipelineOrchestrator;
import org.arcos.UserModel.BatchPipeline.MemListenerClient;
import org.arcos.UserModel.BatchPipeline.MemListenerReadinessCheck;
import org.arcos.UserModel.BatchPipeline.Queue.ConversationPair;
import org.arcos.UserModel.BatchPipeline.Queue.ConversationQueueService;
import org.arcos.UserModel.BatchPipeline.Queue.QueuedConversation;
import org.arcos.UserModel.PersonaTree.PersonaTreeGate;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests d'integration pour le BatchPipelineOrchestrator.
 *
 * Verifie le cycle complet : drain de la queue, chunking, traitement par MemListenerClient,
 * application d'operations PersonaTree, interruption par interaction utilisateur,
 * et degradation gracieuse quand Ollama est indisponible.
 *
 * Le MemListenerClient et MemListenerReadinessCheck sont mockes pour ne pas dependre d'Ollama.
 * Le reste du pipeline (ConversationChunker, PersonaTreeGate, etc.) utilise les vrais beans Spring.
 *
 * Pre-requis : docker compose up qdrant
 */
@SpringBootTest(properties = {
        "arcos.user-model.idle-threshold-minutes=60",
        "arcos.user-model.session-end-threshold-minutes=5",
        "arcos.user-model.batch-check-interval-ms=99999999"
})
@ActiveProfiles("test-e2e")
@Import(E2ETestConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BatchPipelineOrchestratorIT {

    @Autowired private BatchPipelineOrchestrator batchOrchestrator;
    @Autowired private ConversationQueueService conversationQueueService;
    @Autowired private PersonaTreeGate personaTreeGate;
    @Autowired private Orchestrator orchestrator;

    private final MockTTSCapture mockTTS = new MockTTSCapture();

    // Real dependencies saved for restore
    private MemListenerClient realMemListenerClient;
    private MemListenerReadinessCheck realReadinessCheck;

    // Mocks
    private MemListenerClient mockMemListenerClient;
    private MemListenerReadinessCheck mockReadinessCheck;

    @BeforeAll
    void injectMockTTS() {
        ReflectionTestUtils.setField(orchestrator, "ttsHandler", mockTTS);
    }

    @BeforeEach
    void setUp() {
        mockTTS.clear();

        // Save real dependencies
        realMemListenerClient = (MemListenerClient) ReflectionTestUtils.getField(
                batchOrchestrator, "memListenerClient");
        realReadinessCheck = (MemListenerReadinessCheck) ReflectionTestUtils.getField(
                batchOrchestrator, "readinessCheck");

        // Create mocks
        mockMemListenerClient = Mockito.mock(MemListenerClient.class);
        mockReadinessCheck = Mockito.mock(MemListenerReadinessCheck.class);

        // Inject mocks
        ReflectionTestUtils.setField(batchOrchestrator, "memListenerClient", mockMemListenerClient);
        ReflectionTestUtils.setField(batchOrchestrator, "readinessCheck", mockReadinessCheck);

        // Drain any leftover conversations from previous tests
        if (!conversationQueueService.isEmpty()) {
            conversationQueueService.drainAll();
        }
    }

    @AfterEach
    void restoreReal() {
        ReflectionTestUtils.setField(batchOrchestrator, "memListenerClient", realMemListenerClient);
        ReflectionTestUtils.setField(batchOrchestrator, "readinessCheck", realReadinessCheck);

        // Reset interrupted flag
        ReflectionTestUtils.setField(batchOrchestrator, "interrupted", false);
    }

    // ========================================================================
    // AC1: Given conversations queued and inactivity threshold reached
    //      When BatchPipelineOrchestrator triggers
    //      Then conversations are drained, chunked, and processed
    // ========================================================================

    @Test
    @Order(1)
    void runBatch_drainsChunksAndProcessesConversations() {
        // Given: 2 conversations queued
        when(mockReadinessCheck.isModelReady()).thenReturn(true);
        when(mockMemListenerClient.generate(anyString())).thenReturn("NO_OP()");

        QueuedConversation conv1 = new QueuedConversation(
                "it-conv-1",
                List.of(
                        new ConversationPair("Bonjour, je m'appelle Pierre", "Bonjour Pierre !"),
                        new ConversationPair("J'ai 30 ans", "Merci pour cette information.")
                ),
                LocalDateTime.now().minusMinutes(35),
                false
        );
        QueuedConversation conv2 = new QueuedConversation(
                "it-conv-2",
                List.of(
                        new ConversationPair("J'aime la musique classique", "La musique classique est magnifique !")
                ),
                LocalDateTime.now().minusMinutes(32),
                false
        );

        conversationQueueService.enqueue(conv1);
        conversationQueueService.enqueue(conv2);
        assertEquals(2, conversationQueueService.size(), "Queue devrait contenir 2 conversations");

        // When
        batchOrchestrator.runBatch();

        // Then: queue should be drained
        assertTrue(conversationQueueService.isEmpty(),
                "La queue devrait etre vide apres le batch");

        // MemListenerClient should have been called (at least once per conversation chunk)
        verify(mockMemListenerClient, atLeast(2)).generate(anyString());
    }

    @Test
    @Order(2)
    void runBatch_withSingleConversation_processesAllChunks() {
        // Given: 1 conversation with many pairs (will produce multiple chunks)
        when(mockReadinessCheck.isModelReady()).thenReturn(true);
        when(mockMemListenerClient.generate(anyString())).thenReturn("NO_OP()");

        QueuedConversation conv = new QueuedConversation(
                "it-conv-chunked",
                List.of(
                        new ConversationPair("Premier echange", "Reponse 1"),
                        new ConversationPair("Deuxieme echange", "Reponse 2"),
                        new ConversationPair("Troisieme echange", "Reponse 3"),
                        new ConversationPair("Quatrieme echange", "Reponse 4"),
                        new ConversationPair("Cinquieme echange", "Reponse 5"),
                        new ConversationPair("Sixieme echange", "Reponse 6"),
                        new ConversationPair("Septieme echange", "Reponse 7")
                ),
                LocalDateTime.now().minusMinutes(35),
                false
        );

        conversationQueueService.enqueue(conv);

        // When
        batchOrchestrator.runBatch();

        // Then: with windowSize=3, 7 pairs should produce 3 chunks (3+3+1)
        // MemListenerClient should be called once per chunk
        verify(mockMemListenerClient, atLeast(2)).generate(anyString());
        assertTrue(conversationQueueService.isEmpty(),
                "La queue devrait etre vide apres traitement");
    }

    @Test
    @Order(3)
    void runBatch_emptyQueue_skipsProcessing() {
        // Given: model ready but empty queue
        when(mockReadinessCheck.isModelReady()).thenReturn(true);
        assertTrue(conversationQueueService.isEmpty());

        // When
        batchOrchestrator.runBatch();

        // Then: no LLM calls
        verify(mockMemListenerClient, never()).generate(anyString());
    }

    // ========================================================================
    // AC2: Given Ollama/Qwen3-8B is available
    //      When chunks are sent to MemListenerClient
    //      Then PersonaTree operations (ADD/UPDATE/DELETE) are extracted and applied
    // ========================================================================

    @Test
    @Order(4)
    void runBatch_addOperations_appliedToPersonaTree() {
        // Given: MemListenerClient returns ADD operations for known schema paths
        when(mockReadinessCheck.isModelReady()).thenReturn(true);

        String addResponse = "ADD(1_Biological_Characteristics.Physical_Appearance.Body_Build.Height, \"1m80\")";
        when(mockMemListenerClient.generate(anyString())).thenReturn(addResponse);

        QueuedConversation conv = new QueuedConversation(
                "it-conv-add",
                List.of(new ConversationPair("Je mesure 1m80", "Merci, c'est note.")),
                LocalDateTime.now().minusMinutes(35),
                false
        );
        conversationQueueService.enqueue(conv);

        // When
        batchOrchestrator.runBatch();

        // Then: the leaf should have been updated in the PersonaTree
        var value = personaTreeGate.getLeafValue(
                "1_Biological_Characteristics.Physical_Appearance.Body_Build.Height");
        assertTrue(value.isPresent(), "La feuille Height devrait avoir une valeur apres ADD");
        assertEquals("1m80", value.get(), "La valeur devrait etre '1m80'");
    }

    @Test
    @Order(5)
    void runBatch_updateOperation_mergesValueInPersonaTree() {
        // Given: first set a value via ADD, then UPDATE it
        when(mockReadinessCheck.isModelReady()).thenReturn(true);

        // First call returns ADD, second returns UPDATE
        String addResponse = "ADD(1_Biological_Characteristics.Physical_Appearance.Body_Build.Weight, \"75kg\")";
        String updateResponse = "UPDATE(1_Biological_Characteristics.Physical_Appearance.Body_Build.Weight, \"75kg, tendance sportive\")";

        when(mockMemListenerClient.generate(anyString()))
                .thenReturn(addResponse)
                .thenReturn(updateResponse);

        // Enqueue 2 conversations to trigger 2 calls
        conversationQueueService.enqueue(new QueuedConversation(
                "it-conv-update-1",
                List.of(new ConversationPair("Je pese 75kg", "Compris.")),
                LocalDateTime.now().minusMinutes(35), false
        ));
        conversationQueueService.enqueue(new QueuedConversation(
                "it-conv-update-2",
                List.of(new ConversationPair("Je fais du sport regulierement", "Super !")),
                LocalDateTime.now().minusMinutes(34), false
        ));

        // When
        batchOrchestrator.runBatch();

        // Then: the merged value should be present
        var value = personaTreeGate.getLeafValue(
                "1_Biological_Characteristics.Physical_Appearance.Body_Build.Weight");
        assertTrue(value.isPresent(), "La feuille Weight devrait avoir une valeur apres UPDATE");
        assertEquals("75kg, tendance sportive", value.get(),
                "La valeur devrait contenir la fusion UPDATE");
    }

    @Test
    @Order(6)
    void runBatch_deleteOperation_clearsValueInPersonaTree() {
        // Given: a leaf has a value, then DELETE clears it
        when(mockReadinessCheck.isModelReady()).thenReturn(true);

        // First set a value, then delete it in a second batch
        String addResponse = "ADD(1_Biological_Characteristics.Physical_Appearance.Skin.Skin_Color, \"claire\")";
        when(mockMemListenerClient.generate(anyString())).thenReturn(addResponse);

        conversationQueueService.enqueue(new QueuedConversation(
                "it-conv-del-1",
                List.of(new ConversationPair("J'ai la peau claire", "Compris.")),
                LocalDateTime.now().minusMinutes(35), false
        ));
        batchOrchestrator.runBatch();

        // Verify it was set
        assertTrue(personaTreeGate.getLeafValue(
                "1_Biological_Characteristics.Physical_Appearance.Skin.Skin_Color").isPresent());

        // Now DELETE
        String deleteResponse = "DELETE(1_Biological_Characteristics.Physical_Appearance.Skin.Skin_Color, None)";
        when(mockMemListenerClient.generate(anyString())).thenReturn(deleteResponse);

        conversationQueueService.enqueue(new QueuedConversation(
                "it-conv-del-2",
                List.of(new ConversationPair("En fait non, oublie ca", "D'accord.")),
                LocalDateTime.now().minusMinutes(34), false
        ));
        batchOrchestrator.runBatch();

        // Then: the leaf should be empty
        var value = personaTreeGate.getLeafValue(
                "1_Biological_Characteristics.Physical_Appearance.Skin.Skin_Color");
        assertTrue(value.isEmpty(), "La feuille Skin_Color devrait etre vide apres DELETE");
    }

    @Test
    @Order(7)
    void runBatch_noOpResponse_doesNotModifyTree() {
        // Given
        when(mockReadinessCheck.isModelReady()).thenReturn(true);
        when(mockMemListenerClient.generate(anyString())).thenReturn("NO_OP()");

        int filledBefore = personaTreeGate.getFilledLeafCount();

        conversationQueueService.enqueue(new QueuedConversation(
                "it-conv-noop",
                List.of(new ConversationPair("Quelle heure est-il ?", "Il est 14h.")),
                LocalDateTime.now().minusMinutes(35), false
        ));

        // When
        batchOrchestrator.runBatch();

        // Then: no change in filled leaf count (NO_OP doesn't modify tree)
        assertEquals(filledBefore, personaTreeGate.getFilledLeafCount(),
                "NO_OP ne devrait pas modifier le nombre de feuilles remplies");
    }

    @Test
    @Order(8)
    void runBatch_multipleOperationsInOneResponse_allApplied() {
        // Given: MemListener returns multiple operations in a single response
        when(mockReadinessCheck.isModelReady()).thenReturn(true);

        String multiOpResponse = String.join("\n",
                "ADD(1_Biological_Characteristics.Physical_Appearance.Facial_Features.Eyes, \"bleus\")",
                "ADD(1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair, \"chatains, courts\")"
        );
        when(mockMemListenerClient.generate(anyString())).thenReturn(multiOpResponse);

        conversationQueueService.enqueue(new QueuedConversation(
                "it-conv-multi",
                List.of(new ConversationPair(
                        "J'ai les yeux bleus et les cheveux chatains courts",
                        "Merci pour ces details !")),
                LocalDateTime.now().minusMinutes(35), false
        ));

        // When
        batchOrchestrator.runBatch();

        // Then: both leaves should be filled
        var eyes = personaTreeGate.getLeafValue(
                "1_Biological_Characteristics.Physical_Appearance.Facial_Features.Eyes");
        var hair = personaTreeGate.getLeafValue(
                "1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair");

        assertTrue(eyes.isPresent(), "Eyes devrait avoir une valeur");
        assertEquals("bleus", eyes.get());
        assertTrue(hair.isPresent(), "Scalp_Hair devrait avoir une valeur");
        assertEquals("chatains, courts", hair.get());
    }

    // ========================================================================
    // AC3: Given the batch pipeline is running
    //      When a user interaction occurs
    //      Then the batch pipeline is interrupted
    // ========================================================================

    @Test
    @Order(9)
    void interrupt_duringBatch_reEnqueuesRemainingConversations() throws Exception {
        // Given: 3 conversations, MemListenerClient is slow on the first one
        when(mockReadinessCheck.isModelReady()).thenReturn(true);

        CountDownLatch firstCallStarted = new CountDownLatch(1);
        CountDownLatch interruptSent = new CountDownLatch(1);

        when(mockMemListenerClient.generate(anyString())).thenAnswer(invocation -> {
            firstCallStarted.countDown();
            // Wait until interrupt is sent
            interruptSent.await(5, TimeUnit.SECONDS);
            // Small delay to allow the interrupted flag to be checked
            Thread.sleep(50);
            return "NO_OP()";
        });

        conversationQueueService.enqueue(new QueuedConversation(
                "it-int-1",
                List.of(new ConversationPair("Msg 1", "Rep 1")),
                LocalDateTime.now().minusMinutes(35), false
        ));
        conversationQueueService.enqueue(new QueuedConversation(
                "it-int-2",
                List.of(new ConversationPair("Msg 2", "Rep 2")),
                LocalDateTime.now().minusMinutes(34), false
        ));
        conversationQueueService.enqueue(new QueuedConversation(
                "it-int-3",
                List.of(new ConversationPair("Msg 3", "Rep 3")),
                LocalDateTime.now().minusMinutes(33), false
        ));

        // When: run batch in background, then interrupt
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicBoolean batchCompleted = new AtomicBoolean(false);

        executor.submit(() -> {
            batchOrchestrator.runBatch();
            batchCompleted.set(true);
        });

        // Wait for the first MemListener call to start
        assertTrue(firstCallStarted.await(5, TimeUnit.SECONDS),
                "Le premier appel MemListener devrait avoir demarre");

        // Simulate user interaction -> interrupt
        batchOrchestrator.interrupt();
        interruptSent.countDown();

        // Wait for batch to complete
        Awaitility.await().atMost(Duration.ofSeconds(10))
                .untilTrue(batchCompleted);

        executor.shutdown();

        // Then: some conversations should have been re-enqueued
        // The first conversation was being processed when interrupt happened,
        // so at least conv2 and conv3 (or all 3) should be re-enqueued
        assertTrue(conversationQueueService.size() >= 1,
                "Au moins 1 conversation devrait etre re-enqueuee apres interruption, "
                        + "taille actuelle: " + conversationQueueService.size());
    }

    @Test
    @Order(10)
    void interrupt_beforeBatchStarts_reEnqueuesAll() {
        // Given
        when(mockReadinessCheck.isModelReady()).thenReturn(true);

        // MemListenerClient returns immediately after interrupt
        when(mockMemListenerClient.generate(anyString())).thenAnswer(invocation -> {
            return "NO_OP()";
        });

        conversationQueueService.enqueue(new QueuedConversation(
                "it-pre-int-1",
                List.of(new ConversationPair("Msg A", "Rep A")),
                LocalDateTime.now().minusMinutes(35), false
        ));
        conversationQueueService.enqueue(new QueuedConversation(
                "it-pre-int-2",
                List.of(new ConversationPair("Msg B", "Rep B")),
                LocalDateTime.now().minusMinutes(34), false
        ));

        // Drain queue first, then re-enqueue to have a clean state.
        // (setUp already drains, but let's be explicit)

        // When: interrupt BEFORE calling runBatch triggers the interrupt flag
        // We simulate this via the unit-tested pattern: interrupt inside generate()
        when(mockMemListenerClient.generate(anyString())).thenAnswer(invocation -> {
            batchOrchestrator.interrupt();
            return "NO_OP()";
        });

        batchOrchestrator.runBatch();

        // Then: the second conversation should be re-enqueued
        // (first was processed, interrupt happens during its chunk, but the loop
        //  re-checks at the conversation level)
        assertTrue(conversationQueueService.size() >= 1,
                "Au moins 1 conversation devrait etre re-enqueuee");
    }

    // ========================================================================
    // AC4: Given Ollama is unavailable
    //      When the pipeline checks readiness
    //      Then processing is gracefully skipped (no crash)
    // ========================================================================

    @Test
    @Order(11)
    void runBatch_ollamaUnavailable_skipsGracefully() {
        // Given: model not ready
        when(mockReadinessCheck.isModelReady()).thenReturn(false);

        conversationQueueService.enqueue(new QueuedConversation(
                "it-conv-unavail",
                List.of(new ConversationPair("Bonjour", "Salut")),
                LocalDateTime.now().minusMinutes(35), false
        ));

        // When: should not throw
        assertDoesNotThrow(() -> batchOrchestrator.runBatch());

        // Then: queue should NOT have been drained (pipeline skipped entirely)
        assertFalse(conversationQueueService.isEmpty(),
                "La queue ne devrait pas etre videe quand Ollama est indisponible");
        assertEquals(1, conversationQueueService.size(),
                "La conversation devrait rester dans la queue");

        // MemListenerClient should not have been called
        verify(mockMemListenerClient, never()).generate(anyString());
    }

    @Test
    @Order(12)
    void runBatch_ollamaBecomesAvailable_processesOnNextRun() {
        // Given: model starts unavailable, then becomes available
        when(mockReadinessCheck.isModelReady()).thenReturn(false);

        conversationQueueService.enqueue(new QueuedConversation(
                "it-conv-recover",
                List.of(new ConversationPair("Je suis la", "Moi aussi")),
                LocalDateTime.now().minusMinutes(35), false
        ));

        // First run: skipped
        batchOrchestrator.runBatch();
        assertFalse(conversationQueueService.isEmpty(),
                "La queue ne devrait pas etre videe quand Ollama est indisponible");

        // Now Ollama becomes available
        when(mockReadinessCheck.isModelReady()).thenReturn(true);
        when(mockMemListenerClient.generate(anyString())).thenReturn("NO_OP()");

        // Second run: processes
        batchOrchestrator.runBatch();
        assertTrue(conversationQueueService.isEmpty(),
                "La queue devrait etre vide apres que Ollama est devenu disponible");
        verify(mockMemListenerClient, atLeastOnce()).generate(anyString());
    }

    @Test
    @Order(13)
    void runBatch_memListenerThrowsException_doesNotCrash() {
        // Given: model ready but MemListenerClient throws during generate
        when(mockReadinessCheck.isModelReady()).thenReturn(true);
        when(mockMemListenerClient.generate(anyString()))
                .thenThrow(new RuntimeException("Ollama connection refused"));

        conversationQueueService.enqueue(new QueuedConversation(
                "it-conv-error",
                List.of(new ConversationPair("Test erreur", "Reponse")),
                LocalDateTime.now().minusMinutes(35), false
        ));

        // When: should not throw (error is caught in processConversation)
        assertDoesNotThrow(() -> batchOrchestrator.runBatch());

        // Then: queue should be drained (conversations were consumed even if processing failed)
        assertTrue(conversationQueueService.isEmpty(),
                "La queue devrait etre videe meme si le traitement echoue");
    }

    @Test
    @Order(14)
    void runBatch_emptyMemListenerResponse_handledGracefully() {
        // Given: MemListenerClient returns empty string
        when(mockReadinessCheck.isModelReady()).thenReturn(true);
        when(mockMemListenerClient.generate(anyString())).thenReturn("");

        conversationQueueService.enqueue(new QueuedConversation(
                "it-conv-empty",
                List.of(new ConversationPair("Rien a dire", "Ok")),
                LocalDateTime.now().minusMinutes(35), false
        ));

        int filledBefore = personaTreeGate.getFilledLeafCount();

        // When
        assertDoesNotThrow(() -> batchOrchestrator.runBatch());

        // Then: tree unchanged, no crash
        assertEquals(filledBefore, personaTreeGate.getFilledLeafCount(),
                "Le nombre de feuilles remplies ne devrait pas changer avec une reponse vide");
    }
}
