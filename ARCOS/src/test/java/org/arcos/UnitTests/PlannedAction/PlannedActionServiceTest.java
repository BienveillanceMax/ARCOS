package org.arcos.UnitTests.PlannedAction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.arcos.EventBus.EventQueue;
import org.arcos.LLM.Client.LLMClient;
import org.arcos.LLM.Client.ResponseObject.PlannedActionPlanResponse;
import org.arcos.LLM.Prompts.PromptBuilder;
import org.arcos.PlannedAction.Models.ActionStatus;
import org.arcos.PlannedAction.Models.ActionType;
import org.arcos.PlannedAction.Models.PlannedActionEntry;
import org.arcos.PlannedAction.Models.ReWOOPlan;
import org.arcos.PlannedAction.PlannedActionService;
import org.arcos.common.utils.ObjectCreationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlannedActionServiceTest {

    @Mock
    private EventQueue eventQueue;

    @Mock
    private LLMClient llmClient;

    @Mock
    private PromptBuilder promptBuilder;

    private PlannedActionService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new PlannedActionService(eventQueue, llmClient, promptBuilder);
    }

    @Test
    void createAction_ShouldStoreAction() {
        // Given
        PlannedActionEntry entry = ObjectCreationUtils.createSimpleReminderEntry();

        // When
        service.createAction(entry);

        // Then
        List<PlannedActionEntry> activeActions = service.listActiveActions();
        assertEquals(1, activeActions.size());
        assertEquals("Appeler le dentiste", activeActions.get(0).getLabel());
    }

    @Test
    void cancelAction_ShouldDisableMatchingAction() {
        // Given
        PlannedActionEntry entry = ObjectCreationUtils.createSimpleReminderEntry();
        service.createAction(entry);

        // When
        boolean cancelled = service.cancelAction("dentiste");

        // Then
        assertTrue(cancelled);
        assertTrue(service.listActiveActions().isEmpty());
    }

    @Test
    void cancelAction_NoMatch_ShouldReturnFalse() {
        // Given
        PlannedActionEntry entry = ObjectCreationUtils.createSimpleReminderEntry();
        service.createAction(entry);

        // When
        boolean cancelled = service.cancelAction("inexistant");

        // Then
        assertFalse(cancelled);
        assertEquals(1, service.listActiveActions().size());
    }

    @Test
    void markCompleted_ShouldSetStatusCompleted() {
        // Given
        PlannedActionEntry entry = ObjectCreationUtils.createSimpleReminderEntry();
        service.createAction(entry);

        // When
        service.markCompleted(entry);

        // Then
        assertTrue(service.listActiveActions().isEmpty());
        assertEquals(ActionStatus.COMPLETED, entry.getStatus());
    }

    @Test
    void deleteAction_ShouldRemoveAction() {
        // Given
        PlannedActionEntry entry = ObjectCreationUtils.createSimpleReminderEntry();
        service.createAction(entry);

        // When
        service.deleteAction(entry.getId());

        // Then
        assertTrue(service.listActiveActions().isEmpty());
    }

    @Test
    void listActiveActions_ShouldOnlyReturnActive() {
        // Given
        PlannedActionEntry active = ObjectCreationUtils.createSimpleReminderEntry();
        PlannedActionEntry completed = ObjectCreationUtils.createSimpleReminderEntry();
        completed.setLabel("Completed action");
        completed.setStatus(ActionStatus.COMPLETED);

        service.createAction(active);
        service.createAction(completed);

        // When
        List<PlannedActionEntry> result = service.listActiveActions();

        // Then
        assertEquals(1, result.size());
        assertEquals("Appeler le dentiste", result.get(0).getLabel());
    }

    @Test
    void generateExecutionPlan_Success_ShouldSetPlanOnEntry() {
        // Given
        PlannedActionEntry entry = ObjectCreationUtils.createSimpleReminderEntry();
        service.createAction(entry);

        PlannedActionPlanResponse response = new PlannedActionPlanResponse();
        ReWOOPlan plan = new ReWOOPlan(List.of());
        response.setExecutionPlan(plan);
        response.setSynthesisPromptTemplate("template");

        when(promptBuilder.buildReWOOPlanPrompt(entry)).thenReturn(new Prompt("plan prompt"));
        when(llmClient.generatePlannedActionPlanResponse(any(Prompt.class))).thenReturn(response);

        // When
        ReWOOPlan result = service.generateExecutionPlan(entry);

        // Then
        assertNotNull(result);
        assertEquals(plan, entry.getExecutionPlan());
        assertEquals("template", entry.getSynthesisPromptTemplate());
    }

    @Test
    void generateExecutionPlan_AllRetriesFail_ShouldReturnNull() {
        // Given
        PlannedActionEntry entry = ObjectCreationUtils.createSimpleReminderEntry();
        service.createAction(entry);

        when(promptBuilder.buildReWOOPlanPrompt(entry)).thenReturn(new Prompt("plan prompt"));
        when(llmClient.generatePlannedActionPlanResponse(any(Prompt.class)))
                .thenThrow(new RuntimeException("LLM error"));

        // When
        ReWOOPlan result = service.generateExecutionPlan(entry);

        // Then
        assertNull(result);
        assertNull(entry.getExecutionPlan());
        verify(llmClient, times(3)).generatePlannedActionPlanResponse(any(Prompt.class));
    }

    @Test
    void onActionTriggered_ShouldPushEventToQueue() {
        // Given
        PlannedActionEntry entry = ObjectCreationUtils.createSimpleReminderEntry();
        when(eventQueue.offer(any())).thenReturn(true);

        // When
        service.onActionTriggered(entry);

        // Then
        verify(eventQueue).offer(any());
        assertNotNull(entry.getLastExecutedAt());
        assertEquals(1, entry.getExecutionCount());
    }
}
