package org.arcos.UnitTests.Orchestrator;

import org.arcos.EventBus.EventQueue;
import org.arcos.EventBus.Events.Event;
import org.arcos.EventBus.Events.EventType;
import org.arcos.IO.OuputHandling.PiperEmbeddedTTSModule;
import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.LLM.Client.LLMClient;
import org.arcos.LLM.Prompts.PromptBuilder;
import org.arcos.Memory.ConversationContext;
import org.arcos.Memory.LongTermMemory.Models.DesireEntry;
import org.arcos.Memory.LongTermMemory.service.MemoryService;
import org.arcos.Orchestrator.InitiativeService;
import org.arcos.Orchestrator.Orchestrator;
import org.arcos.Personality.Desires.DesireService;
import org.arcos.Personality.Mood.MoodService;
import org.arcos.Personality.Mood.MoodUpdate;
import org.arcos.Personality.Mood.MoodVoiceMapper;
import org.arcos.Personality.Mood.PadState;
import org.arcos.Personality.PersonalityOrchestrator;
import org.arcos.PlannedAction.Models.PlannedActionEntry;
import org.arcos.PlannedAction.Models.ActionType;
import org.arcos.PlannedAction.PlannedActionExecutor;
import org.arcos.PlannedAction.PlannedActionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

import reactor.core.publisher.Flux;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
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
    private MoodVoiceMapper moodVoiceMapper;

    @Mock
    private CentralFeedBackHandler centralFeedBackHandler;

    @Mock
    private DesireService desireService;

    @Mock
    private PlannedActionExecutor plannedActionExecutor;

    @Mock
    private PlannedActionService plannedActionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orchestrator = new Orchestrator(centralFeedBackHandler,
                personalityOrchestrator,
                eventQueue,
                llmClient,
                promptBuilder,
                conversationContext,
                memoryService,
                initiativeService,
                desireService,
                moodService,
                moodVoiceMapper,
                plannedActionExecutor,
                plannedActionService
        );
        ReflectionTestUtils.setField(orchestrator, "ttsHandler", piperEmbeddedTTSModule);
    }

    @Test
    void dispatch_WakeWordEvent_ShouldProcessQueryAndStreamResponse() {
        // Given
        String userQuery = "test query";
        Event<String> wakeWordEvent = new Event<>(EventType.WAKEWORD, userQuery, "test");
        // Sentence splitting triggers on punctuation (.?!), so "Première phrase." and " Deuxième phrase."
        // arrive as separate chunks that each contain a sentence boundary
        String responseChunk1 = "Première phrase.";
        String responseChunk2 = " Deuxième phrase.";
        String fullResponse = responseChunk1 + responseChunk2;
        Flux<String> responseStream = Flux.just(responseChunk1, responseChunk2);
        MoodUpdate moodUpdate = new MoodUpdate();

        when(promptBuilder.buildConversationnalPrompt(any(ConversationContext.class), any(String.class))).thenReturn(new Prompt(""));
        when(llmClient.generateStreamingChatResponse(any(Prompt.class))).thenReturn(responseStream);

        // For the async part
        when(promptBuilder.buildMoodUpdatePrompt(any(), any(), any())).thenReturn(new Prompt(""));
        when(llmClient.generateMoodUpdateResponse(any(Prompt.class))).thenReturn(moodUpdate);

        // Mock MoodVoiceMapper
        when(conversationContext.getPadState()).thenReturn(new PadState());
        when(moodVoiceMapper.mapToVoice(any(PadState.class))).thenReturn(new MoodVoiceMapper.VoiceParams(1.0f, 0.6f, 0.8f));

        when(piperEmbeddedTTSModule.speakAsync(any(String.class), anyFloat(), anyFloat(), anyFloat())).thenReturn(null);

        // When
        orchestrator.dispatch(wakeWordEvent);

        // Then
        // Verify streaming part — each chunk has a sentence boundary so each produces a speakAsync call
        verify(promptBuilder).buildConversationnalPrompt(conversationContext, userQuery);
        verify(llmClient).generateStreamingChatResponse(any(Prompt.class));
        verify(piperEmbeddedTTSModule, times(2)).speakAsync(any(String.class), anyFloat(), anyFloat(), anyFloat());

        // Verify completion part (synchronous because of Flux.just)
        verify(conversationContext).addUserMessage(userQuery);
        verify(conversationContext).addAssistantMessage(fullResponse);

        // Verify async part with timeout
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
        doNothing().when(initiativeService).processInitiative(any(DesireEntry.class));

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
}
