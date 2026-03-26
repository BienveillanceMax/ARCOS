package org.arcos.UnitTests.Orchestrator;

import org.arcos.Configuration.AudioProperties;
import org.arcos.EventBus.EventQueue;
import org.arcos.Memory.ConversationSummaryService;
import org.arcos.EventBus.Events.Event;
import org.arcos.EventBus.Events.EventType;
import org.arcos.IO.OuputHandling.PiperEmbeddedTTSModule;
import org.arcos.Producers.WakeWordProducer;
import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.LLM.Client.ChatOrchestrator;
import org.arcos.LLM.Client.LLMClient;
import org.arcos.LLM.Prompts.PromptBuilder;
import org.arcos.Memory.ConversationContext;
import org.arcos.Memory.LongTermMemory.Models.DesireEntry;
import org.arcos.Memory.LongTermMemory.service.MemoryService;
import org.arcos.Personality.Initiative.InitiativeService;
import org.arcos.Orchestrator.Orchestrator;
import org.arcos.Personality.Desires.DesireService;
import org.arcos.Personality.Mood.MoodService;
import org.arcos.Personality.Mood.MoodStateHolder;
import org.arcos.Personality.Mood.MoodUpdate;
import org.arcos.Personality.Mood.MoodVoiceMapper;
import org.arcos.Personality.Mood.PadState;
import org.arcos.Personality.PersonalityOrchestrator;
import org.arcos.PlannedAction.ExecutionHistoryService;
import org.arcos.PlannedAction.Models.PlannedActionEntry;
import org.arcos.PlannedAction.Models.ActionType;
import org.arcos.PlannedAction.PlannedActionExecutor;
import org.arcos.PlannedAction.PlannedActionService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OrchestratorTest {

    @InjectMocks
    private Orchestrator orchestrator;

    @Mock
    private EventQueue eventQueue;

    @Mock
    private LLMClient llmClient;

    @Mock
    private ChatOrchestrator chatOrchestrator;

    @Mock
    private PromptBuilder promptBuilder;

    @Mock
    private ConversationContext conversationContext;

    @Mock
    private MemoryService memoryService;

    @Mock
    private InitiativeService initiativeService;

    @Mock
    private PersonalityOrchestrator personalityOrchestrator;

    @Mock
    private PiperEmbeddedTTSModule piperEmbeddedTTSModule;

    @Mock
    private MoodService moodService;

    @Mock
    private MoodStateHolder moodStateHolder;

    @Mock
    private MoodVoiceMapper moodVoiceMapper;

    @Mock
    private CentralFeedBackHandler centralFeedBackHandler;

    @Mock
    private DesireService desireService;

    @Mock
    private PlannedActionExecutor plannedActionExecutor;

    @Mock
    private PlannedActionService plannedActionService;

    @Mock
    private ExecutionHistoryService executionHistoryService;

    @Mock
    private WakeWordProducer wakeWordProducer;

    @Mock
    private AudioProperties audioProperties;

    @Mock
    private ConversationSummaryService conversationSummaryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orchestrator = new Orchestrator(centralFeedBackHandler,
                personalityOrchestrator,
                eventQueue,
                llmClient,
                chatOrchestrator,
                promptBuilder,
                conversationContext,
                memoryService,
                initiativeService,
                desireService,
                moodService,
                moodStateHolder,
                moodVoiceMapper,
                plannedActionExecutor,
                plannedActionService,
                executionHistoryService,
                wakeWordProducer,
                audioProperties,
                conversationSummaryService,
                null, null, null
        );
        ReflectionTestUtils.setField(orchestrator, "ttsHandler", piperEmbeddedTTSModule);
    }

    @Test
    void dispatch_WakeWordEvent_ShouldProcessQueryAndStreamResponse() {
        // Given
        String userQuery = "test query";
        Event<String> wakeWordEvent = new Event<>(EventType.WAKEWORD, userQuery, "test");
        String responseChunk1 = "Première phrase.";
        String responseChunk2 = " Deuxième phrase.";
        String fullResponse = responseChunk1 + responseChunk2;
        Flux<String> responseStream = Flux.just(responseChunk1, responseChunk2);
        MoodUpdate moodUpdate = new MoodUpdate();

        when(promptBuilder.buildConversationnalPrompt(any(ConversationContext.class), any(String.class))).thenReturn(new Prompt(""));
        when(chatOrchestrator.generateStreamingChatResponse(any(Prompt.class))).thenReturn(responseStream);

        when(promptBuilder.buildMoodUpdatePrompt(any(), any(), any())).thenReturn(new Prompt(""));
        when(llmClient.generateMoodUpdateResponse(any(Prompt.class))).thenReturn(moodUpdate);

        when(moodStateHolder.getPadState()).thenReturn(new PadState());
        when(moodVoiceMapper.mapToVoice(any(PadState.class))).thenReturn(new MoodVoiceMapper.VoiceParams(1.0f, 0.6f, 0.8f));

        when(piperEmbeddedTTSModule.speakAsync(any(String.class), anyFloat(), anyFloat(), anyFloat())).thenReturn(null);

        // When
        orchestrator.dispatch(wakeWordEvent);

        // Then
        verify(promptBuilder).buildConversationnalPrompt(conversationContext, userQuery);
        verify(chatOrchestrator).generateStreamingChatResponse(any(Prompt.class));
        verify(piperEmbeddedTTSModule, times(2)).speakAsync(any(String.class), anyFloat(), anyFloat(), anyFloat());

        verify(conversationContext).addUserMessage(userQuery);
        verify(conversationContext).addAssistantMessage(fullResponse);
        // No per-turn summary call anymore
        verifyNoInteractions(conversationSummaryService);

        verify(moodService, timeout(1000)).applyMoodUpdate(any(MoodUpdate.class));
        verify(llmClient, timeout(1000)).generateMoodUpdateResponse(any(Prompt.class));
        verify(promptBuilder, timeout(1000)).buildMoodUpdatePrompt(any(), eq(userQuery), eq(fullResponse));
    }

    @Test
    void dispatch_InitiativeEvent_ShouldProcessInitiative() {
        // Given
        DesireEntry desire = new DesireEntry();
        desire.setLabel("test desire");
        desire.setIntensity(10);
        Event<DesireEntry> initiativeEvent = new Event<>(EventType.INITIATIVE, desire, "test");
        when(initiativeService.processInitiative(any(DesireEntry.class))).thenReturn(true);

        // When
        orchestrator.dispatch(initiativeEvent);

        // Then
        verify(initiativeService).processInitiative(desire);
    }

    @Test
    void dispatch_PlannedActionEvent_ShouldExecuteAndSpeak() {
        // Given
        PlannedActionEntry action = new PlannedActionEntry();
        action.setLabel("Appeler le dentiste");
        action.setActionType(ActionType.TODO);
        Event<PlannedActionEntry> plannedActionEvent = new Event<>(EventType.PLANNED_ACTION, action, "test");

        when(plannedActionExecutor.execute(action)).thenReturn("Rappel : Appeler le dentiste");
        when(piperEmbeddedTTSModule.speakAsync(any(String.class))).thenReturn(null);

        // When
        orchestrator.dispatch(plannedActionEvent);

        // Then
        verify(plannedActionExecutor).execute(action);
        verify(piperEmbeddedTTSModule).speakAsync("Rappel : Appeler le dentiste");
        verify(executionHistoryService).recordExecution(action, "Rappel : Appeler le dentiste", true);
        verify(plannedActionService).markCompleted(action);
    }

    @Test
    void dispatch_PlannedActionHabitEvent_ShouldNotMarkCompleted() {
        // Given
        PlannedActionEntry habit = new PlannedActionEntry();
        habit.setLabel("Briefing matinal");
        habit.setActionType(ActionType.HABIT);
        Event<PlannedActionEntry> plannedActionEvent = new Event<>(EventType.PLANNED_ACTION, habit, "test");

        when(plannedActionExecutor.execute(habit)).thenReturn("Voici votre briefing...");
        when(piperEmbeddedTTSModule.speakAsync(any(String.class))).thenReturn(null);

        // When
        orchestrator.dispatch(plannedActionEvent);

        // Then
        verify(plannedActionExecutor).execute(habit);
        verify(piperEmbeddedTTSModule).speakAsync("Voici votre briefing...");
        verify(plannedActionService, never()).markCompleted(any());
    }

    @Test
    void dispatch_PlannedActionReminderEvent_ShouldSpeakReminderWithoutExecuting() {
        // Given
        PlannedActionEntry action = new PlannedActionEntry();
        action.setLabel("Rendre le rapport");
        action.setActionType(ActionType.DEADLINE);
        action.setDeadlineDatetime(java.time.LocalDateTime.now().plusHours(3));
        action.setReminderTrigger(true);
        Event<PlannedActionEntry> plannedActionEvent = new Event<>(EventType.PLANNED_ACTION, action, "test");

        when(piperEmbeddedTTSModule.speakAsync(any(String.class))).thenReturn(null);

        // When
        orchestrator.dispatch(plannedActionEvent);

        // Then
        verify(piperEmbeddedTTSModule).speakAsync(argThat(msg ->
                msg.contains("Rappel : Rendre le rapport") && msg.contains("heure")));
        verify(plannedActionExecutor, never()).execute(any());
        verify(plannedActionService, never()).markCompleted(any());
        assertFalse(action.isReminderTrigger());
    }

    @Test
    void dispatch_ListeningWindowTimeoutEvent_ShouldResetConversationMode() {
        // Given
        Event<Void> timeoutEvent = new Event<>(EventType.LISTENING_WINDOW_TIMEOUT, null, "WakeWordProducer");
        ReflectionTestUtils.setField(orchestrator, "inConversationMode", true);

        // When
        orchestrator.dispatch(timeoutEvent);

        // Then: inConversationMode reset to false
        boolean mode = (boolean) ReflectionTestUtils.getField(orchestrator, "inConversationMode");
        assertFalse(mode, "LISTENING_WINDOW_TIMEOUT doit remettre inConversationMode à false");
    }

    @Test
    void dispatch_SessionEndEvent_ShouldCallEndSession() {
        // Given
        Event<Void> sessionEndEvent = new Event<>(EventType.SESSION_END, null, "InactivityProducer");
        when(conversationContext.getFullConversation()).thenReturn("USER: bonjour\nASSISTANT: Salut!");
        when(conversationContext.getMessageHistory()).thenReturn(java.util.List.of());
        when(conversationContext.getMessageCount()).thenReturn(2);

        // When
        orchestrator.dispatch(sessionEndEvent);

        // Then: startNewSession called synchronously, no summary for < 6 messages
        verify(conversationContext).startNewSession();
        verifyNoInteractions(conversationSummaryService);
    }

    @Test
    void dispatch_CalendarEvent_ShouldGenerateAndSpeakResponse() {
        // Given
        String calendarEventPayload = "test calendar event";
        Event<String> calendarEvent = new Event<>(EventType.CALENDAR_EVENT_SCHEDULER, calendarEventPayload, "test");
        when(promptBuilder.buildSchedulerAlertPrompt(calendarEventPayload)).thenReturn(new Prompt("test prompt"));
        when(llmClient.generateToollessResponse(any(Prompt.class))).thenReturn("test response");
        when(piperEmbeddedTTSModule.speakAsync(any(String.class))).thenReturn(null);

        // When
        orchestrator.dispatch(calendarEvent);

        // Then
        verify(llmClient).generateToollessResponse(any(Prompt.class));
        verify(piperEmbeddedTTSModule).speakAsync("test response");
    }

    // ── Dégradation gracieuse quand circuit breaker OPEN ────────

    @Test
    void dispatch_WakeWord_WhenCircuitBreakerOpen_ShouldSpeakDegradationMessage() {
        // Given
        Event<String> wakeWordEvent = new Event<>(EventType.WAKEWORD, "bonjour", "test");
        when(moodStateHolder.getPadState()).thenReturn(new PadState());
        when(moodVoiceMapper.mapToVoice(any(PadState.class))).thenReturn(new MoodVoiceMapper.VoiceParams(1.0f, 0.6f, 0.8f));
        when(promptBuilder.buildConversationnalPrompt(any(), any())).thenReturn(new Prompt(""));
        when(chatOrchestrator.generateStreamingChatResponse(any(Prompt.class)))
                .thenThrow(mock(CallNotPermittedException.class));

        // When
        orchestrator.dispatch(wakeWordEvent);

        // Then
        verify(piperEmbeddedTTSModule).speakAsync("Désolé, le service de langage est temporairement indisponible. Réessaie dans quelques instants.");
        verify(wakeWordProducer).resumeDetection();
    }

    @Test
    void dispatch_Initiative_WhenCircuitBreakerOpen_ShouldSpeakDegradationMessage() {
        // Given
        DesireEntry desire = new DesireEntry();
        desire.setLabel("test desire");
        Event<DesireEntry> initiativeEvent = new Event<>(EventType.INITIATIVE, desire, "test");
        when(initiativeService.processInitiative(any(DesireEntry.class)))
                .thenThrow(mock(CallNotPermittedException.class));

        // When
        orchestrator.dispatch(initiativeEvent);

        // Then
        verify(piperEmbeddedTTSModule).speakAsync("Désolé, le service de langage est temporairement indisponible. Réessaie dans quelques instants.");
        verify(wakeWordProducer).resumeDetection();
    }

    @Test
    void dispatch_CalendarEvent_WhenCircuitBreakerOpen_ShouldFallbackToSimpleMessage() {
        // Given
        String calendarPayload = "Réunion équipe 14h";
        Event<String> calendarEvent = new Event<>(EventType.CALENDAR_EVENT_SCHEDULER, calendarPayload, "test");
        when(promptBuilder.buildSchedulerAlertPrompt(calendarPayload)).thenReturn(new Prompt(""));
        when(llmClient.generateToollessResponse(any(Prompt.class)))
                .thenThrow(mock(CallNotPermittedException.class));

        // When
        orchestrator.dispatch(calendarEvent);

        // Then — fallback message without LLM
        verify(piperEmbeddedTTSModule).speakAsync("Rappel d'événement : " + calendarPayload);
    }

    @Test
    void dispatch_PlannedActionReminder_WhenCircuitBreakerOpen_ShouldStillWork() {
        // Given — reminder path doesn't use LLM
        PlannedActionEntry action = new PlannedActionEntry();
        action.setLabel("Appeler le médecin");
        action.setActionType(ActionType.TODO);
        action.setReminderTrigger(true);

        Event<PlannedActionEntry> event = new Event<>(EventType.PLANNED_ACTION, action, "test");

        // When
        orchestrator.dispatch(event);

        // Then — reminder processed without any LLM call
        verify(piperEmbeddedTTSModule).speakAsync(argThat(msg -> msg.contains("Rappel : Appeler le médecin")));
        verify(llmClient, never()).generateToollessResponse(any());
        verify(plannedActionExecutor, never()).execute(any());
    }
}
