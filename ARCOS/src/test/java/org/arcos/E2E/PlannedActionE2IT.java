package org.arcos.E2E;

import org.arcos.EventBus.Events.Event;
import org.arcos.EventBus.Events.EventType;
import org.arcos.PlannedAction.ExecutionHistoryService;
import org.arcos.PlannedAction.Models.ActionStatus;
import org.arcos.PlannedAction.Models.ActionType;
import org.arcos.PlannedAction.Models.ExecutionHistoryEntry;
import org.arcos.PlannedAction.Models.PlannedActionEntry;
import org.arcos.PlannedAction.PlannedActionExecutor;
import org.arcos.PlannedAction.PlannedActionRepository;
import org.arcos.PlannedAction.PlannedActionService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlannedActionE2IT extends BaseE2IT {

    @Autowired private PlannedActionService plannedActionService;
    @Autowired private PlannedActionRepository plannedActionRepository;
    @Autowired private PlannedActionExecutor plannedActionExecutor;
    @Autowired private ExecutionHistoryService executionHistoryService;

    private PlannedActionEntry makeAction(ActionType type, String label) {
        PlannedActionEntry action = new PlannedActionEntry();
        action.setId(UUID.randomUUID().toString());
        action.setLabel(label);
        action.setActionType(type);
        action.setReminderTrigger(false);
        action.setCreatedAt(LocalDateTime.now());
        return action;
    }

    @Test
    void t1_todoActionExecutedAndCompleted() {
        PlannedActionEntry action = makeAction(ActionType.TODO, "Appeler le dentiste");
        plannedActionService.createAction(action);

        orchestrator.dispatch(new Event<>(EventType.PLANNED_ACTION, action, "test"));

        assertTrue(mockTTS.hasSpoken(), "TODO action should produce TTS output");
        assertTrue(mockTTS.getLastSpokenText().toLowerCase().contains("dentiste"),
            "Spoken text should mention the action label");

        List<ExecutionHistoryEntry> history = executionHistoryService.getHistoryForAction(action.getId(), 1);
        assertFalse(history.isEmpty(), "Execution history should be recorded");
        assertTrue(history.get(0).isSuccess(), "TODO execution should succeed");

        PlannedActionEntry saved = plannedActionRepository.findById(action.getId());
        assertNotNull(saved);
        assertEquals(ActionStatus.COMPLETED, saved.getStatus(),
            "TODO action should be marked COMPLETED");
    }

    @Test
    void t2_habitActionExecutedButNotCompleted() {
        PlannedActionEntry habit = makeAction(ActionType.HABIT, "Briefing matinal");
        plannedActionService.createAction(habit);

        orchestrator.dispatch(new Event<>(EventType.PLANNED_ACTION, habit, "test"));

        assertTrue(mockTTS.hasSpoken(), "HABIT should produce TTS output");

        List<ExecutionHistoryEntry> history = executionHistoryService.getHistoryForAction(habit.getId(), 1);
        assertFalse(history.isEmpty());
        assertTrue(history.get(0).isSuccess());

        PlannedActionEntry saved = plannedActionRepository.findById(habit.getId());
        assertNotNull(saved);
        assertNotEquals(ActionStatus.COMPLETED, saved.getStatus(),
            "HABIT should never be marked COMPLETED");
    }

    @Test
    void t3_deadlineReminderFutureIncludesTimeRemaining() {
        PlannedActionEntry deadline = makeAction(ActionType.DEADLINE, "Rendre le rapport");
        deadline.setDeadlineDatetime(LocalDateTime.now().plusHours(3));
        deadline.setReminderTrigger(true);
        plannedActionService.createAction(deadline);

        orchestrator.dispatch(new Event<>(EventType.PLANNED_ACTION, deadline, "test"));

        assertTrue(mockTTS.hasSpoken());
        String spoken = mockTTS.getLastSpokenText();
        assertTrue(spoken.contains("Rappel"), "Reminder message should contain 'Rappel'");
        assertTrue(spoken.contains("heure"), "Should mention time remaining in hours");
        assertFalse(deadline.isReminderTrigger(), "isReminderTrigger should be reset to false");
    }

    @Test
    void t4_deadlineReminderPastSaysOverdue() {
        PlannedActionEntry overdue = makeAction(ActionType.DEADLINE, "Soumettre la déclaration");
        overdue.setDeadlineDatetime(LocalDateTime.now().minusHours(1));
        overdue.setReminderTrigger(true);
        plannedActionService.createAction(overdue);

        orchestrator.dispatch(new Event<>(EventType.PLANNED_ACTION, overdue, "test"));

        assertTrue(mockTTS.hasSpoken());
        assertTrue(mockTTS.getLastSpokenText().contains("échéance dépassée"),
            "Overdue reminder should say 'échéance dépassée'");
    }

    @Test
    void t5_executionFailureRecordedInHistory() {
        PlannedActionEntry action = makeAction(ActionType.TODO, "Action qui échoue");
        plannedActionService.createAction(action);

        // Replace executor with one that throws
        PlannedActionExecutor realExecutor = (PlannedActionExecutor) ReflectionTestUtils
            .getField(orchestrator, "plannedActionExecutor");
        PlannedActionExecutor failingExecutor = Mockito.mock(PlannedActionExecutor.class);
        Mockito.when(failingExecutor.execute(action))
            .thenThrow(new RuntimeException("Simulated executor failure"));
        ReflectionTestUtils.setField(orchestrator, "plannedActionExecutor", failingExecutor);

        try {
            orchestrator.dispatch(new Event<>(EventType.PLANNED_ACTION, action, "test"));
        } finally {
            ReflectionTestUtils.setField(orchestrator, "plannedActionExecutor", realExecutor);
        }

        List<ExecutionHistoryEntry> history = executionHistoryService.getHistoryForAction(action.getId(), 1);
        assertFalse(history.isEmpty(), "History should be recorded even on failure");
        assertFalse(history.get(0).isSuccess(), "Failed execution should be recorded as failure");

        PlannedActionEntry saved = plannedActionRepository.findById(action.getId());
        if (saved != null) {
            assertNotEquals(ActionStatus.COMPLETED, saved.getStatus(),
                "Failed action should not be marked completed");
        }
    }
}
