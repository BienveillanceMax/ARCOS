package org.arcos.IntegrationTests.Services;

import io.qdrant.client.grpc.Points;
import org.arcos.E2E.E2ETestConfig;
import org.arcos.E2E.MockTTSCapture;
import org.arcos.EventBus.Events.Event;
import org.arcos.EventBus.Events.EventType;
import org.arcos.EventBus.Events.WakeWordEvent;
import org.arcos.LLM.Client.ChatOrchestrator;
import org.arcos.LLM.Client.LLMClient;
import org.arcos.Memory.ConversationContext;
import org.arcos.Memory.ConversationSummaryService;
import org.arcos.Memory.LongTermMemory.Models.DesireEntry;
import org.arcos.Memory.LongTermMemory.Qdrant.QdrantClientProvider;
import org.arcos.Orchestrator.Orchestrator;
import org.arcos.Personality.Initiative.InitiativeService;
import org.arcos.Personality.Mood.MoodService;
import org.arcos.Personality.PersonalityOrchestrator;
import org.arcos.PlannedAction.ExecutionHistoryService;
import org.arcos.PlannedAction.Models.ActionType;
import org.arcos.PlannedAction.Models.PlannedActionEntry;
import org.arcos.PlannedAction.PlannedActionExecutor;
import org.arcos.PlannedAction.PlannedActionService;
import org.arcos.Tools.CalendarTool.model.CalendarEvent;
import org.arcos.UserModel.BatchPipeline.Queue.ConversationQueueService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests d'intégration pour le dispatch de l'Orchestrator.
 * Vérifie que chaque type d'événement est routé vers le bon handler.
 *
 * Utilise le profil test-e2e (TransformersEmbeddingModel 384-dim)
 * et mocke les appels LLM pour ne pas dépendre de l'API Mistral.
 *
 * Pré-requis : docker compose up qdrant
 */
// Matches MIN_MESSAGES_FOR_SUMMARY (package-private, cannot access from here)
@SpringBootTest(properties = {
        "arcos.user-model.idle-threshold-minutes=60",
        "arcos.user-model.session-end-threshold-minutes=5"
})
@ActiveProfiles("test-e2e")
@Import(E2ETestConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrchestratorDispatchIT {

    @Autowired private Orchestrator orchestrator;
    @Autowired private QdrantClientProvider qdrantClientProvider;
    @Autowired private ConversationContext context;
    @Autowired private PlannedActionService plannedActionService;
    @Autowired private ExecutionHistoryService executionHistoryService;
    @Autowired private ConversationQueueService conversationQueueService;

    /** Mirrors MIN_MESSAGES_FOR_SUMMARY (package-private) */
    private static final int MIN_MESSAGES_FOR_SUMMARY = 6;

    private final MockTTSCapture mockTTS = new MockTTSCapture();

    // Original dependencies saved for restore
    private ChatOrchestrator realChatOrchestrator;
    private LLMClient realLLMClient;
    private InitiativeService realInitiativeService;
    private PlannedActionExecutor realPlannedActionExecutor;
    private PersonalityOrchestrator realPersonalityOrchestrator;
    private MoodService realMoodService;
    private ConversationSummaryService realConversationSummaryService;

    // Mocks
    private ChatOrchestrator mockChatOrchestrator;
    private LLMClient mockLLMClient;
    private InitiativeService mockInitiativeService;
    private PersonalityOrchestrator mockPersonalityOrchestrator;
    private MoodService mockMoodService;
    private ConversationSummaryService mockConversationSummaryService;

    @BeforeAll
    void clearCollections() throws Exception {
        var client = qdrantClientProvider.getClient();
        Points.Filter matchAll = Points.Filter.newBuilder().build();
        for (String col : List.of("Memories", "Opinions", "Desires")) {
            try {
                client.deleteAsync(col, matchAll).get();
            } catch (Exception ignored) {
            }
        }
    }

    @BeforeEach
    void setUp() {
        // Inject MockTTS (PiperEmbeddedTTSModule is new'd, not a Spring bean)
        ReflectionTestUtils.setField(orchestrator, "ttsHandler", mockTTS);
        mockTTS.clear();

        // Save real dependencies
        realChatOrchestrator = (ChatOrchestrator) ReflectionTestUtils.getField(orchestrator, "chatOrchestrator");
        realLLMClient = (LLMClient) ReflectionTestUtils.getField(orchestrator, "llmClient");
        realInitiativeService = (InitiativeService) ReflectionTestUtils.getField(orchestrator, "initiativeService");
        realPlannedActionExecutor = (PlannedActionExecutor) ReflectionTestUtils.getField(orchestrator, "plannedActionExecutor");
        realPersonalityOrchestrator = (PersonalityOrchestrator) ReflectionTestUtils.getField(orchestrator, "personalityOrchestrator");
        realMoodService = (MoodService) ReflectionTestUtils.getField(orchestrator, "moodService");
        realConversationSummaryService = (ConversationSummaryService) ReflectionTestUtils.getField(orchestrator, "conversationSummaryService");

        // Create mocks
        mockChatOrchestrator = Mockito.mock(ChatOrchestrator.class);
        mockLLMClient = Mockito.mock(LLMClient.class);
        mockInitiativeService = Mockito.mock(InitiativeService.class);
        mockPersonalityOrchestrator = Mockito.mock(PersonalityOrchestrator.class);
        mockMoodService = Mockito.mock(MoodService.class);
        mockConversationSummaryService = Mockito.mock(ConversationSummaryService.class);

        // Inject mocks
        ReflectionTestUtils.setField(orchestrator, "chatOrchestrator", mockChatOrchestrator);
        ReflectionTestUtils.setField(orchestrator, "llmClient", mockLLMClient);
        ReflectionTestUtils.setField(orchestrator, "initiativeService", mockInitiativeService);
        ReflectionTestUtils.setField(orchestrator, "personalityOrchestrator", mockPersonalityOrchestrator);
        ReflectionTestUtils.setField(orchestrator, "moodService", mockMoodService);
        ReflectionTestUtils.setField(orchestrator, "conversationSummaryService", mockConversationSummaryService);

        // Reset conversation context
        context.startNewSession();
    }

    @AfterEach
    void restoreReal() {
        ReflectionTestUtils.setField(orchestrator, "chatOrchestrator", realChatOrchestrator);
        ReflectionTestUtils.setField(orchestrator, "llmClient", realLLMClient);
        ReflectionTestUtils.setField(orchestrator, "initiativeService", realInitiativeService);
        ReflectionTestUtils.setField(orchestrator, "plannedActionExecutor", realPlannedActionExecutor);
        ReflectionTestUtils.setField(orchestrator, "personalityOrchestrator", realPersonalityOrchestrator);
        ReflectionTestUtils.setField(orchestrator, "moodService", realMoodService);
        ReflectionTestUtils.setField(orchestrator, "conversationSummaryService", realConversationSummaryService);
    }

    // ========================================================================
    // AC1: WAKEWORD dispatch -> processAndSpeak()
    // ========================================================================

    @Test
    @Order(1)
    void dispatch_wakewordEvent_invokesProcessAndSpeak() {
        // Given: mock streaming response
        when(mockChatOrchestrator.generateStreamingChatResponse(any(Prompt.class)))
                .thenReturn(Flux.just("Bonjour, ", "je suis ARCOS."));

        // When
        orchestrator.dispatch(new Event<>(EventType.WAKEWORD, "Salut, comment vas-tu ?", "test"));

        // Then: TTS should have spoken the streamed response
        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> mockTTS.hasSpoken());
        assertTrue(mockTTS.hasSpoken(), "TTS devrait avoir ete appele pour un WAKEWORD event");

        // Context should contain user and assistant messages
        assertTrue(context.getRecentMessages(10).stream()
                .anyMatch(m -> m.getContent().contains("Salut")),
                "Le contexte devrait contenir le message utilisateur");
        assertTrue(context.getRecentMessages(10).stream()
                .anyMatch(m -> m.getType().name().equals("ASSISTANT")),
                "Le contexte devrait contenir la reponse assistant");
    }

    @Test
    @Order(2)
    void dispatch_wakewordEvent_multiTurn_invokesProcessAndSpeak() {
        // Given
        when(mockChatOrchestrator.generateStreamingChatResponse(any(Prompt.class)))
                .thenReturn(Flux.just("Reponse multi-tour."));

        // When: WakeWordEvent with multiTurn=true
        orchestrator.dispatch(new WakeWordEvent("Deuxieme question", "test", true));

        // Then
        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> mockTTS.hasSpoken());
        assertTrue(mockTTS.hasSpoken(), "TTS devrait avoir ete appele pour un WakeWordEvent multi-turn");
    }

    // ========================================================================
    // AC2: INITIATIVE dispatch -> InitiativeService.processInitiative()
    // ========================================================================

    @Test
    @Order(3)
    void dispatch_initiativeEvent_invokesInitiativeService() {
        // Given
        DesireEntry desire = new DesireEntry();
        desire.setId(UUID.randomUUID().toString());
        desire.setLabel("Apprendre le piano");
        desire.setDescription("Je veux apprendre a jouer du piano");
        desire.setIntensity(0.9);
        desire.setCreatedAt(LocalDateTime.now());

        when(mockInitiativeService.processInitiative(desire)).thenReturn(true);

        // When
        orchestrator.dispatch(new Event<>(EventType.INITIATIVE, desire, "test"));

        // Then
        verify(mockInitiativeService, times(1)).processInitiative(desire);
    }

    @Test
    @Order(4)
    void dispatch_initiativeEvent_failureHandledGracefully() {
        // Given: initiative service throws
        DesireEntry desire = new DesireEntry();
        desire.setId(UUID.randomUUID().toString());
        desire.setLabel("Initiative echouee");
        desire.setDescription("Test failure");
        desire.setCreatedAt(LocalDateTime.now());

        when(mockInitiativeService.processInitiative(desire))
                .thenThrow(new RuntimeException("Simulated failure"));

        // When: should not throw
        assertDoesNotThrow(() ->
                orchestrator.dispatch(new Event<>(EventType.INITIATIVE, desire, "test")));

        // Then
        verify(mockInitiativeService, times(1)).processInitiative(desire);
    }

    // ========================================================================
    // AC3: PLANNED_ACTION dispatch -> PlannedActionExecutor (execution path)
    // ========================================================================

    @Test
    @Order(5)
    void dispatch_plannedActionEvent_executesAndSpeaks() {
        // Given: simple reminder action (no execution plan)
        PlannedActionEntry action = makeAction(ActionType.TODO, "Appeler le medecin");
        action.setReminderTrigger(false);
        plannedActionService.createAction(action);

        // When
        orchestrator.dispatch(new Event<>(EventType.PLANNED_ACTION, action, "test"));

        // Then: TTS should speak the result from PlannedActionExecutor
        assertTrue(mockTTS.hasSpoken(), "TTS devrait parler apres execution d'une action planifiee");
        assertTrue(mockTTS.getLastSpokenText().toLowerCase().contains("medecin"),
                "Le texte TTS devrait mentionner le label de l'action");
    }

    // ========================================================================
    // AC3b: PLANNED_ACTION dispatch -> reminder path
    // ========================================================================

    @Test
    @Order(6)
    void dispatch_plannedActionEvent_reminderTrigger_speaksReminderMessage() {
        // Given: action with reminderTrigger=true
        PlannedActionEntry action = makeAction(ActionType.DEADLINE, "Rendre le dossier");
        action.setReminderTrigger(true);
        action.setDeadlineDatetime(LocalDateTime.now().plusHours(2));
        plannedActionService.createAction(action);

        // When
        orchestrator.dispatch(new Event<>(EventType.PLANNED_ACTION, action, "test"));

        // Then
        assertTrue(mockTTS.hasSpoken(), "TTS devrait avoir parle un rappel");
        String spoken = mockTTS.getLastSpokenText();
        assertTrue(spoken.contains("Rappel"), "Le message devrait commencer par 'Rappel'");
        assertTrue(spoken.contains("Rendre le dossier"), "Le message devrait contenir le label");
        assertTrue(spoken.contains("heure"), "Le message devrait indiquer le temps restant");
        assertFalse(action.isReminderTrigger(), "reminderTrigger devrait etre remis a false");
    }

    @Test
    @Order(7)
    void dispatch_plannedActionEvent_reminderTrigger_overdueDeadline() {
        // Given: overdue deadline
        PlannedActionEntry action = makeAction(ActionType.DEADLINE, "Soumettre le rapport");
        action.setReminderTrigger(true);
        action.setDeadlineDatetime(LocalDateTime.now().minusHours(1));
        plannedActionService.createAction(action);

        // When
        orchestrator.dispatch(new Event<>(EventType.PLANNED_ACTION, action, "test"));

        // Then
        assertTrue(mockTTS.hasSpoken());
        assertTrue(mockTTS.getLastSpokenText().contains("echeance depassee")
                || mockTTS.getLastSpokenText().contains("échéance dépassée"),
                "Un rappel en retard devrait dire 'echeance depassee'");
    }

    // ========================================================================
    // AC4: CALENDAR_EVENT_SCHEDULER dispatch -> LLM toolless + TTS
    // ========================================================================

    @Test
    @Order(8)
    void dispatch_calendarEvent_generatesAlertAndSpeaks() {
        // Given: mock LLM toolless response
        CalendarEvent calEvent = CalendarEvent.builder()
                .title("Reunion d'equipe")
                .description("Daily standup")
                .startDateTime(LocalDateTime.now().plusMinutes(15))
                .build();

        when(mockLLMClient.generateToollessResponse(any(Prompt.class)))
                .thenReturn("Attention, vous avez une reunion d'equipe dans 15 minutes.");

        // When
        orchestrator.dispatch(new Event<>(EventType.CALENDAR_EVENT_SCHEDULER, calEvent, "test"));

        // Then
        assertTrue(mockTTS.hasSpoken(), "TTS devrait avoir prononce l'alerte calendrier");
        verify(mockLLMClient, times(1)).generateToollessResponse(any(Prompt.class));
        assertTrue(mockTTS.getLastSpokenText().contains("reunion"),
                "L'alerte devrait mentionner la reunion");
    }

    // ========================================================================
    // AC5: SESSION_END dispatch -> personality pipeline + mood + enqueue
    // ========================================================================

    @Test
    @Order(9)
    void dispatch_sessionEnd_triggersPersonalityPipelineAsync() {
        // Given: populate context with enough messages for processing
        context.addUserMessage("Bonjour, je suis passionne par l'astronomie");
        context.addAssistantMessage("L'astronomie est fascinante ! Quels aspects t'interessent le plus ?");
        context.addUserMessage("Les trous noirs et la cosmologie");
        context.addAssistantMessage("Les trous noirs sont un sujet captivant.");

        int messageCountBefore = context.getMessageCount();
        assertTrue(messageCountBefore >= 4, "Le contexte devrait avoir au moins 4 messages");

        // When
        orchestrator.dispatch(new Event<>(EventType.SESSION_END, "timeout", "test"));

        // Then: personality pipeline should be called async
        Awaitility.await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() ->
                        verify(mockPersonalityOrchestrator, atLeastOnce()).processMemory(any(String.class)));

        // Context should be reset (startNewSession called)
        assertEquals(0, context.getMessageCount(),
                "Le contexte devrait etre vide apres SESSION_END");
    }

    @Test
    @Order(10)
    void dispatch_sessionEnd_withEnoughMessages_triggersSummary() {
        // Given: populate context with >= MIN_MESSAGES_FOR_SUMMARY (6) messages
        for (int i = 0; i < 4; i++) {
            context.addUserMessage("Message utilisateur " + i);
            context.addAssistantMessage("Reponse assistant " + i);
        }
        assertTrue(context.getMessageCount() >= MIN_MESSAGES_FOR_SUMMARY,
                "Devrait avoir >= 6 messages pour declencher le resume");

        when(mockConversationSummaryService.summarizeAsync(any(), any(String.class)))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture("Resume de la session"));

        // When
        orchestrator.dispatch(new Event<>(EventType.SESSION_END, "timeout", "test"));

        // Then: summary should be triggered
        Awaitility.await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() ->
                        verify(mockConversationSummaryService, times(1))
                                .summarizeAsync(any(), any(String.class)));
    }

    @Test
    @Order(11)
    void dispatch_sessionEnd_withFewMessages_noSummary() {
        // Given: only 2 messages (below MIN_MESSAGES_FOR_SUMMARY threshold)
        context.addUserMessage("Bonjour");
        context.addAssistantMessage("Bonjour !");
        assertTrue(context.getMessageCount() < MIN_MESSAGES_FOR_SUMMARY);

        // When
        orchestrator.dispatch(new Event<>(EventType.SESSION_END, "timeout", "test"));

        // Then: summary should NOT be triggered
        Awaitility.await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() ->
                        verify(mockPersonalityOrchestrator, atLeastOnce()).processMemory(any(String.class)));
        verify(mockConversationSummaryService, never()).summarizeAsync(any(), any(String.class));
    }

    @Test
    @Order(12)
    void dispatch_sessionEnd_emptyContext_doesNothing() {
        // Given: empty context
        assertEquals(0, context.getMessageCount());

        // When
        orchestrator.dispatch(new Event<>(EventType.SESSION_END, "timeout", "test"));

        // Then: nothing should be triggered
        verify(mockPersonalityOrchestrator, never()).processMemory(any(String.class));
        verify(mockConversationSummaryService, never()).summarizeAsync(any(), any(String.class));
    }

    // ========================================================================
    // LISTENING_WINDOW_TIMEOUT dispatch -> sets inConversationMode to false
    // ========================================================================

    @Test
    @Order(13)
    void dispatch_listeningWindowTimeout_resetsConversationMode() {
        // Given: set inConversationMode to true
        ReflectionTestUtils.setField(orchestrator, "inConversationMode", true);

        // When
        orchestrator.dispatch(new Event<>(EventType.LISTENING_WINDOW_TIMEOUT, "timeout", "test"));

        // Then
        boolean inConversation = (boolean) ReflectionTestUtils.getField(orchestrator, "inConversationMode");
        assertFalse(inConversation, "inConversationMode devrait etre false apres LISTENING_WINDOW_TIMEOUT");
    }

    // ========================================================================
    // IDLE_WINDOW_OPEN dispatch -> triggers batch pipeline
    // ========================================================================

    @Test
    @Order(14)
    void dispatch_idleWindowOpen_triggersBatchPipeline() {
        // Given/When: dispatch idle window open event
        // The batch pipeline orchestrator is @Nullable, and triggerBatchPipeline()
        // submits to personalityExecutor if batchPipelineOrchestrator != null.
        // We just verify dispatch doesn't throw.
        assertDoesNotThrow(() ->
                orchestrator.dispatch(new Event<>(EventType.IDLE_WINDOW_OPEN, "idle", "test")));
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private PlannedActionEntry makeAction(ActionType type, String label) {
        PlannedActionEntry action = new PlannedActionEntry();
        action.setId(UUID.randomUUID().toString());
        action.setLabel(label);
        action.setActionType(type);
        action.setReminderTrigger(false);
        action.setCreatedAt(LocalDateTime.now());
        return action;
    }
}
