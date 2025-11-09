package org.arcos.Orchestrator;

import EventBus.EventQueue;
import EventBus.Events.Event;
import EventBus.Events.EventType;
import IO.OuputHandling.PiperEmbeddedTTSModule;
import LLM.LLMClient;
import LLM.Prompts.PromptBuilder;
import Memory.ConversationContext;
import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.service.MemoryService;
import Orchestrator.InitiativeService;
import Orchestrator.Orchestrator;
import Personality.PersonalityOrchestrator;
import Producers.WakeWordProducer;
import Tools.SearchTool.BraveSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private WakeWordProducer wakeWordProducer;

    @Mock
    private BraveSearchService braveSearchService;

    @Mock
    private PiperEmbeddedTTSModule piperEmbeddedTTSModule;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orchestrator = new Orchestrator(
                personalityOrchestrator,
                eventQueue,
                llmClient,
                promptBuilder,
                conversationContext,
                memoryService,
                initiativeService,
                piperEmbeddedTTSModule
        );
    }

    @Test
    void dispatch_WakeWordEvent_ShouldProcessQuery() {
        // Given
        String userQuery = "test query";
        Event<String> wakeWordEvent = new Event<>(EventType.WAKEWORD, userQuery, "test");
        when(memoryService.searchMemories(userQuery)).thenReturn(Collections.emptyList());
        when(promptBuilder.buildConversationnalPrompt(any(ConversationContext.class), any(String.class))).thenReturn(new Prompt(""));
        when(llmClient.generateChatResponse(any(Prompt.class))).thenReturn("test answer");
        doNothing().when(piperEmbeddedTTSModule).speak(any(String.class));

        // When
        orchestrator.dispatch(wakeWordEvent);

        // Then
        verify(memoryService).searchMemories(userQuery);
        verify(promptBuilder).buildConversationnalPrompt(conversationContext, userQuery);
        verify(llmClient).generateChatResponse(any(Prompt.class));
        verify(piperEmbeddedTTSModule).speak("test answer");
        verify(conversationContext).addUserMessage(userQuery);
        verify(conversationContext).addAssistantMessage("test answer");
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

