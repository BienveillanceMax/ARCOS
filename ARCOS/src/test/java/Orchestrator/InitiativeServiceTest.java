package Orchestrator;

import LLM.LLMClient;
import LLM.LLMResponseParser;
import LLM.Prompts.PromptBuilder;
import Memory.Actions.Entities.ActionResult;
import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.service.MemoryService;
import Orchestrator.Entities.ExecutionPlan;
import Personality.Opinions.OpinionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class InitiativeServiceTest {

    @Mock
    private MemoryService memoryService;

    @Mock
    private OpinionService opinionService;

    @Mock
    private ActionRegistry actionRegistry;

    @Mock
    private LLMClient llmClient;

    @Mock
    private LLMResponseParser responseParser;

    @Mock
    private ActionExecutor actionExecutor;

    @Mock
    private PromptBuilder promptBuilder;

    @InjectMocks
    private InitiativeService initiativeService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testProcessInitiative_Success() throws Exception {
        // Arrange
        DesireEntry desire = new DesireEntry();
        desire.setId("desire-1");
        desire.setDescription("A test desire");

        ExecutionPlan mockPlan = new ExecutionPlan();
        mockPlan.setReasoning("A good plan");

        ActionResult successResult = ActionResult.successWithMessage("Action succeeded");

        when(memoryService.searchMemories(anyString(), anyInt())).thenReturn(Collections.emptyList());
        when(memoryService.searchOpinions(anyString(), anyInt())).thenReturn(Collections.emptyList());
        when(promptBuilder.buildInitiativePlanningPrompt(any(DesireEntry.class), any(List.class), any(List.class))).thenReturn("prompt");
        when(llmClient.generatePlanningResponse(anyString())).thenReturn("llm-response");
        when(responseParser.parseWithMistralRetry(anyString(), anyInt())).thenReturn(mockPlan);
        when(actionExecutor.executeActions(any(ExecutionPlan.class))).thenReturn(Collections.singletonMap("action1", successResult));

        // Act
        initiativeService.processInitiative(desire);

        // Assert
        ArgumentCaptor<DesireEntry> desireCaptor = ArgumentCaptor.forClass(DesireEntry.class);
        verify(memoryService, times(1)).storeDesire(desireCaptor.capture());

        DesireEntry storedDesire = desireCaptor.getValue();
        assertEquals(DesireEntry.Status.SATISFIED, storedDesire.getStatus());
    }

    @Test
    void testProcessInitiative_PlanExecutionFails() throws Exception {
        // Arrange
        DesireEntry desire = new DesireEntry();
        desire.setId("desire-2");
        desire.setDescription("Another test desire");

        ExecutionPlan mockPlan = new ExecutionPlan();
        mockPlan.setReasoning("A plan that will fail");

        ActionResult failureResult = ActionResult.failure("Action failed");

        when(memoryService.searchMemories(anyString(), anyInt())).thenReturn(Collections.emptyList());
        when(memoryService.searchOpinions(anyString(), anyInt())).thenReturn(Collections.emptyList());
        when(promptBuilder.buildInitiativePlanningPrompt(any(DesireEntry.class), any(List.class), any(List.class))).thenReturn("prompt");
        when(llmClient.generatePlanningResponse(anyString())).thenReturn("llm-response");
        when(responseParser.parseWithMistralRetry(anyString(), anyInt())).thenReturn(mockPlan);
        when(actionExecutor.executeActions(any(ExecutionPlan.class))).thenReturn(Collections.singletonMap("action1", failureResult));

        // Act
        initiativeService.processInitiative(desire);

        // Assert
        ArgumentCaptor<DesireEntry> desireCaptor = ArgumentCaptor.forClass(DesireEntry.class);
        verify(memoryService, times(1)).storeDesire(desireCaptor.capture());

        DesireEntry storedDesire = desireCaptor.getValue();
        assertEquals(DesireEntry.Status.PENDING, storedDesire.getStatus());
    }
}
