package org.arcos.UnitTests.Tools;

import org.arcos.PlannedAction.ExecutionHistoryService;
import org.arcos.PlannedAction.Models.ActionType;
import org.arcos.PlannedAction.Models.ExecutionHistoryEntry;
import org.arcos.PlannedAction.Models.PlannedActionEntry;
import org.arcos.PlannedAction.PlannedActionService;
import org.arcos.Tools.Actions.ActionResult;
import org.arcos.Tools.Actions.PlannedActionActions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlannedActionActionsTest {

    @Mock
    private PlannedActionService plannedActionService;

    @Mock
    private ExecutionHistoryService executionHistoryService;

    private PlannedActionActions plannedActionActions;

    @BeforeEach
    void setUp() {
        plannedActionActions = new PlannedActionActions(plannedActionService, executionHistoryService);
    }

    // ── TODO ─────────────────────────────────────────────────────────────────

    @Test
    void planAction_WithTodoType_ShouldCreateAction() {
        // Given / When
        ActionResult result = plannedActionActions.planAction(
                "Appeler le médecin", "TODO", "2026-04-01T10:00:00", null,
                false, null, null, null);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("Appeler le médecin");
        assertThat(result.getMessage()).contains("TODO");

        ArgumentCaptor<PlannedActionEntry> captor = ArgumentCaptor.forClass(PlannedActionEntry.class);
        verify(plannedActionService).createAction(captor.capture());
        assertThat(captor.getValue().getActionType()).isEqualTo(ActionType.TODO);
        assertThat(captor.getValue().getTriggerDatetime()).isEqualTo(LocalDateTime.of(2026, 4, 1, 10, 0, 0));
    }

    @Test
    void planAction_WithTodoType_WithoutTriggerDatetime_ShouldReturnFailure() {
        // Given / When
        ActionResult result = plannedActionActions.planAction(
                "Rappel", "TODO", null, null,
                false, null, null, null);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("triggerDatetime est requis");
        verify(plannedActionService, never()).createAction(any());
    }

    @Test
    void planAction_WithTodoType_WithInvalidDateFormat_ShouldReturnFailure() {
        // Given / When
        ActionResult result = plannedActionActions.planAction(
                "Rappel", "TODO", "pas-une-date", null,
                false, null, null, null);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Format de date invalide");
        verify(plannedActionService, never()).createAction(any());
    }

    // ── HABIT ────────────────────────────────────────────────────────────────

    @Test
    void planAction_WithHabitType_ShouldCreateAction() {
        // Given / When
        ActionResult result = plannedActionActions.planAction(
                "Boire de l'eau", "HABIT", null, "0 30 8 * * *",
                false, null, null, null);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("HABIT");

        ArgumentCaptor<PlannedActionEntry> captor = ArgumentCaptor.forClass(PlannedActionEntry.class);
        verify(plannedActionService).createAction(captor.capture());
        assertThat(captor.getValue().getActionType()).isEqualTo(ActionType.HABIT);
        assertThat(captor.getValue().getCronExpression()).isEqualTo("0 30 8 * * *");
    }

    @Test
    void planAction_WithHabitType_WithoutCronExpression_ShouldReturnFailure() {
        // Given / When
        ActionResult result = plannedActionActions.planAction(
                "Habitude", "HABIT", null, null,
                false, null, null, null);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("cronExpression est requise");
        verify(plannedActionService, never()).createAction(any());
    }

    // ── DEADLINE ─────────────────────────────────────────────────────────────

    @Test
    void planAction_WithDeadlineType_ShouldCreateAction() {
        // Given / When
        ActionResult result = plannedActionActions.planAction(
                "Rendu projet", "DEADLINE", null, null,
                false, null, "2026-06-01T23:59:00", null);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("DEADLINE");

        ArgumentCaptor<PlannedActionEntry> captor = ArgumentCaptor.forClass(PlannedActionEntry.class);
        verify(plannedActionService).createAction(captor.capture());
        assertThat(captor.getValue().getActionType()).isEqualTo(ActionType.DEADLINE);
        assertThat(captor.getValue().getDeadlineDatetime()).isEqualTo(LocalDateTime.of(2026, 6, 1, 23, 59, 0));
    }

    @Test
    void planAction_WithDeadlineType_WithReminders_ShouldParseReminders() {
        // Given / When
        ActionResult result = plannedActionActions.planAction(
                "Examen", "DEADLINE", null, null,
                false, null, "2026-06-15T09:00:00",
                List.of("2026-06-14T09:00:00", "2026-06-13T09:00:00"));

        // Then
        assertThat(result.isSuccess()).isTrue();

        ArgumentCaptor<PlannedActionEntry> captor = ArgumentCaptor.forClass(PlannedActionEntry.class);
        verify(plannedActionService).createAction(captor.capture());
        assertThat(captor.getValue().getReminderDatetimes()).hasSize(2);
    }

    @Test
    void planAction_WithDeadlineType_WithoutDeadlineDatetime_ShouldReturnFailure() {
        // Given / When
        ActionResult result = plannedActionActions.planAction(
                "Échéance", "DEADLINE", null, null,
                false, null, null, null);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("deadlineDatetime est requis");
    }

    @Test
    void planAction_WithDeadlineType_WithInvalidReminderDate_ShouldReturnFailure() {
        // Given / When
        ActionResult result = plannedActionActions.planAction(
                "Examen", "DEADLINE", null, null,
                false, null, "2026-06-15T09:00:00",
                List.of("pas-une-date"));

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Format de date invalide pour un rappel");
    }

    // ── Validation générale ──────────────────────────────────────────────────

    @Test
    void planAction_WithInvalidType_ShouldReturnFailure() {
        // Given / When
        ActionResult result = plannedActionActions.planAction(
                "Action", "INVALID_TYPE", null, null,
                false, null, null, null);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Type d'action invalide");
        assertThat(result.getMessage()).contains("TODO, HABIT, DEADLINE");
    }

    @Test
    void planAction_WithContext_ShouldStoreContext() {
        // Given / When
        plannedActionActions.planAction(
                "Appeler", "TODO", "2026-04-01T10:00:00", null,
                false, "Numéro: 0612345678", null, null);

        // Then
        ArgumentCaptor<PlannedActionEntry> captor = ArgumentCaptor.forClass(PlannedActionEntry.class);
        verify(plannedActionService).createAction(captor.capture());
        assertThat(captor.getValue().getContext()).isEqualTo("Numéro: 0612345678");
    }

    @Test
    void planAction_WithNeedsTools_ShouldGenerateExecutionPlan() {
        // Given / When
        plannedActionActions.planAction(
                "Recherche météo", "TODO", "2026-04-01T10:00:00", null,
                true, null, null, null);

        // Then
        ArgumentCaptor<PlannedActionEntry> captor = ArgumentCaptor.forClass(PlannedActionEntry.class);
        verify(plannedActionService).createAction(captor.capture());
        verify(plannedActionService).generateExecutionPlan(captor.getValue());
    }

    // ── Annulation ──────────────────────────────────────────────────────────

    @Test
    void cancelAction_WhenFound_ShouldReturnSuccess() {
        // Given
        when(plannedActionService.cancelAction("Rappel")).thenReturn(true);

        // When
        ActionResult result = plannedActionActions.cancelAction("Rappel");

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("annulée");
    }

    @Test
    void cancelAction_WhenNotFound_ShouldReturnFailure() {
        // Given
        when(plannedActionService.cancelAction("Inexistant")).thenReturn(false);

        // When
        ActionResult result = plannedActionActions.cancelAction("Inexistant");

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Aucune action active");
    }

    // ── Liste ───────────────────────────────────────────────────────────────

    @Test
    void listActions_WhenEmpty_ShouldReturnSuccessMessage() {
        // Given
        when(plannedActionService.listActiveActions()).thenReturn(Collections.emptyList());

        // When
        ActionResult result = plannedActionActions.listActions(false, null);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("Aucune action planifiée active");
    }

    @Test
    void listActions_WithHistoryLabel_ShouldReturnHistory() {
        // Given
        ExecutionHistoryEntry histEntry = new ExecutionHistoryEntry();
        histEntry.setLabel("Habitude eau");
        histEntry.setExecutedAt(LocalDateTime.of(2026, 3, 20, 8, 30));
        histEntry.setSuccess(true);
        histEntry.setResult("Rappel effectué");
        when(executionHistoryService.searchHistoryByLabel("eau", 5)).thenReturn(List.of(histEntry));

        // When
        ActionResult result = plannedActionActions.listActions(false, "eau");

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("1 entrée(s) d'historique");
    }

    @Test
    void listActions_WithHistoryLabel_WhenNoHistory_ShouldReturnMessage() {
        // Given
        when(executionHistoryService.searchHistoryByLabel("inconnu", 5)).thenReturn(Collections.emptyList());

        // When
        ActionResult result = plannedActionActions.listActions(false, "inconnu");

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("Aucun historique");
    }
}
