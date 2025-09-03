package Orchestrator;

import EventBus.EventQueue;
import EventBus.Events.Event;
import EventBus.Events.EventType;
import Exceptions.ResponseParsingException;
import IO.OuputHandling.TTSHandler;
import LLM.LLMClient;
import LLM.LLMResponseParser;
import LLM.Prompts.PromptBuilder;
import Memory.Actions.Entities.ActionResult;
import Memory.ConversationContext;
import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.service.MemoryService;
import Orchestrator.Entities.ExecutionPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class OrchestratorTests{

    @Mock
    private EventQueue eventQueue;

    @Mock
    private LLMClient llmClient;

    @Mock
    private PromptBuilder promptBuilder;

    @Mock
    private LLMResponseParser responseParser;

    @Mock
    private ActionExecutor actionExecutor;

    @Mock
    private ConversationContext context;

    @Mock
    private MemoryService memoryService;

    @Mock
    private InitiativeService initiativeService;

    @Mock
    private TTSHandler ttsHandler;

    @InjectMocks
    private Orchestrator orchestrator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private void runOrchestratorInThread(Runnable testLogic) {
        Thread orchestratorThread = new Thread(() -> {
            try {
                orchestrator.start();
            } catch (RuntimeException e) {
                // This is expected when the InterruptedException is thrown
            }
        });
        orchestratorThread.start();

        testLogic.run();

        // Give the thread a moment to process
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        orchestratorThread.interrupt(); // Stop the infinite loop
    }

    @Test
    void testDispatch_InitiativeEvent_DelegatesToService() throws InterruptedException {
        // Arrange
        DesireEntry desire = new DesireEntry();
        desire.setId("desire-1");
        Event<DesireEntry> initiativeEvent = new Event<>(EventType.INITIATIVE, desire, "Test");
        when(eventQueue.take()).thenReturn((Event) initiativeEvent).thenThrow(new InterruptedException());

        // Act
        runOrchestratorInThread(() -> {});

        // Assert
        verify(initiativeService, times(1)).processInitiative(desire);
    }

    @Test
    void testDispatch_InitiativeEvent_CatchesServiceFailure() throws InterruptedException {
        // Arrange
        DesireEntry desire = new DesireEntry();
        desire.setId("desire-1");
        desire.setStatus(DesireEntry.Status.ACTIVE);
        Event<DesireEntry> initiativeEvent = new Event<>(EventType.INITIATIVE, desire, "Test");

        when(eventQueue.take()).thenReturn((Event) initiativeEvent).thenThrow(new InterruptedException());
        doThrow(new RuntimeException("Critical failure in service")).when(initiativeService).processInitiative(any(DesireEntry.class));

        // Act
        runOrchestratorInThread(() -> {});

        // Assert
        ArgumentCaptor<DesireEntry> desireCaptor = ArgumentCaptor.forClass(DesireEntry.class);
        verify(memoryService, times(1)).storeDesire(desireCaptor.capture());
        assertEquals(DesireEntry.Status.PENDING, desireCaptor.getValue().getStatus());
    }
}

