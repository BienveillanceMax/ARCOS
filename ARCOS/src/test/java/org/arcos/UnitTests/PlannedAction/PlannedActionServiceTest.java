package org.arcos.UnitTests.PlannedAction;

import org.arcos.Configuration.PlannedActionProperties;
import org.arcos.LLM.Client.PlannedActionLLMClient;
import org.arcos.LLM.Client.ResponseObject.PlannedActionPlanResponse;
import org.arcos.LLM.Prompts.PromptBuilder;
import org.arcos.PlannedAction.Models.ActionStatus;
import org.arcos.PlannedAction.Models.PlannedActionEntry;
import org.arcos.PlannedAction.Models.ReWOOPlan;
import org.arcos.PlannedAction.PlannedActionRepository;
import org.arcos.PlannedAction.PlannedActionService;
import org.arcos.Producers.PlannedActionProducer;
import org.arcos.common.utils.ObjectCreationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlannedActionServiceTest {

    @Mock
    private PlannedActionRepository repository;

    @Mock
    private PlannedActionProducer producer;

    @Mock
    private PlannedActionLLMClient llmClient;

    @Mock
    private PromptBuilder promptBuilder;

    private PlannedActionService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(repository.findAll()).thenReturn(List.of());
        service = new PlannedActionService(repository, producer, llmClient, promptBuilder, new PlannedActionProperties());
    }

    @Test
    void createAction_ShouldSaveAndLog() {
        // Given
        PlannedActionEntry entry = ObjectCreationUtils.createSimpleReminderEntry();

        // When
        service.createAction(entry);

        // Then
        verify(repository).save(entry);
    }

    @Test
    void cancelAction_ShouldDisableAndSave() {
        // Given
        PlannedActionEntry entry = ObjectCreationUtils.createSimpleReminderEntry();
        when(repository.findActiveByLabelContaining("dentiste")).thenReturn(entry);

        // When
        boolean cancelled = service.cancelAction("dentiste");

        // Then
        assertTrue(cancelled);
        assertEquals(ActionStatus.DISABLED, entry.getStatus());
        verify(repository).save(entry);
    }

    @Test
    void cancelAction_NoMatch_ShouldReturnFalse() {
        // Given
        when(repository.findActiveByLabelContaining("inexistant")).thenReturn(null);

        // When
        boolean cancelled = service.cancelAction("inexistant");

        // Then
        assertFalse(cancelled);
        verify(repository, never()).save(any());
    }

    @Test
    void listActiveActions_ShouldDelegateToRepository() {
        // Given
        List<PlannedActionEntry> expected = List.of(ObjectCreationUtils.createSimpleReminderEntry());
        when(repository.findAllActive()).thenReturn(expected);

        // When
        List<PlannedActionEntry> result = service.listActiveActions();

        // Then
        assertEquals(expected, result);
        verify(repository).findAllActive();
    }

    @Test
    void markCompleted_ShouldSetStatusAndSave() {
        // Given
        PlannedActionEntry entry = ObjectCreationUtils.createSimpleReminderEntry();

        // When
        service.markCompleted(entry);

        // Then
        assertEquals(ActionStatus.COMPLETED, entry.getStatus());
        verify(repository).save(entry);
    }

    @Test
    void deleteAction_ShouldDelegateToRepository() {
        // Given
        PlannedActionEntry entry = ObjectCreationUtils.createSimpleReminderEntry();
        when(repository.delete(entry.getId())).thenReturn(entry);

        // When
        service.deleteAction(entry.getId());

        // Then
        verify(repository).delete(entry.getId());
    }

    @Test
    void generateExecutionPlan_Success_ShouldSetPlanAndSave() {
        // Given
        PlannedActionEntry entry = ObjectCreationUtils.createSimpleReminderEntry();

        PlannedActionPlanResponse response = new PlannedActionPlanResponse();
        ReWOOPlan plan = new ReWOOPlan(List.of());
        response.setExecutionPlan(plan);
        response.setSynthesisPromptTemplate("template");

        when(promptBuilder.buildReWOOPlanPrompt(entry)).thenReturn(new Prompt(new SystemMessage("plan prompt")));
        when(llmClient.generatePlannedActionPlanResponse(any(Prompt.class))).thenReturn(response);

        // When
        ReWOOPlan result = service.generateExecutionPlan(entry);

        // Then
        assertNotNull(result);
        assertEquals(plan, entry.getExecutionPlan());
        assertEquals("template", entry.getSynthesisPromptTemplate());
        verify(repository).save(entry);
    }

    @Test
    void generateExecutionPlan_AllRetriesFail_ShouldReturnNull() {
        // Given
        PlannedActionEntry entry = ObjectCreationUtils.createSimpleReminderEntry();

        when(promptBuilder.buildReWOOPlanPrompt(entry)).thenReturn(new Prompt(new SystemMessage("plan prompt")));
        when(llmClient.generatePlannedActionPlanResponse(any(Prompt.class)))
                .thenThrow(new RuntimeException("LLM error"));

        // When
        ReWOOPlan result = service.generateExecutionPlan(entry);

        // Then
        assertNull(result);
        assertNull(entry.getExecutionPlan());
        verify(llmClient, times(3)).generatePlannedActionPlanResponse(any(Prompt.class));
        verify(repository).save(entry);
    }
}
