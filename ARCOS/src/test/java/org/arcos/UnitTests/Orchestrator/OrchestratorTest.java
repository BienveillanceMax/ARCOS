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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.prompt.Prompt;

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
                moodVoiceMapper
        );
    }

    @Test
    void dispatch_WakeWordEvent_ShouldProcessQueryAndStreamResponse() {
        // Given
        String userQuery = "test query";
        Event<String> wakeWordEvent = new Event<>(EventType.WAKEWORD, userQuery, "test");
        String responseChunk1 = "Test ";
        String responseChunk2 = "answer.";
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

        doNothing().when(piperEmbeddedTTSModule).speak(any(String.class), anyFloat(), anyFloat(), anyFloat());

        // When
        orchestrator.dispatch(wakeWordEvent);

        // Then
        // Verify streaming part
        verify(promptBuilder).buildConversationnalPrompt(conversationContext, userQuery);
        verify(llmClient).generateStreamingChatResponse(any(Prompt.class));
        verify(piperEmbeddedTTSModule, times(1)).speak(eq(responseChunk1), anyFloat(), anyFloat(), anyFloat());
        verify(piperEmbeddedTTSModule, times(1)).speak(eq(responseChunk2), anyFloat(), anyFloat(), anyFloat());

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
    void dispatch_CalendarEvent_ShouldGenerateAndSpeakResponse() {
        // Given
        String calendarEventPayload = "test calendar event";
        Event<String> calendarEvent = new Event<>(EventType.CALENDAR_EVENT_SCHEDULER, calendarEventPayload, "test");
        when(promptBuilder.buildSchedulerAlertPrompt(calendarEventPayload)).thenReturn(new Prompt("test prompt"));
        when(llmClient.generateToollessResponse(any(Prompt.class))).thenReturn("test response");
        doNothing().when(piperEmbeddedTTSModule).speak(any(String.class));

        // When
        orchestrator.dispatch(calendarEvent);

        // Then
        verify(llmClient).generateToollessResponse(any(Prompt.class));
        verify(piperEmbeddedTTSModule).speak("test response");
    }
}

