package org.arcos.UnitTests.PlannedAction;

import org.arcos.Configuration.PlannedActionProperties;
import org.arcos.PlannedAction.ExecutionHistoryService;
import org.arcos.PlannedAction.Models.ExecutionHistoryEntry;
import org.arcos.PlannedAction.Models.PlannedActionEntry;
import org.arcos.common.utils.ObjectCreationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionHistoryServiceTest {

    @TempDir
    Path tempDir;

    private ExecutionHistoryService service;

    @BeforeEach
    void setUp() {
        PlannedActionProperties props = new PlannedActionProperties();
        props.setHistoryStoragePath(tempDir.resolve("execution-history.json").toString());
        service = new ExecutionHistoryService(props);
        service.init();
    }

    @Test
    void recordExecution_ShouldStoreEntry() {
        // Given
        PlannedActionEntry action = ObjectCreationUtils.createSimpleReminderEntry();

        // When
        service.recordExecution(action, "Rappel : Appeler le dentiste", true);

        // Then
        List<ExecutionHistoryEntry> history = service.getHistoryForAction(action.getId(), 10);
        assertEquals(1, history.size());
        assertEquals(action.getId(), history.get(0).getActionId());
        assertEquals("Appeler le dentiste", history.get(0).getLabel());
        assertTrue(history.get(0).isSuccess());
    }

    @Test
    void getHistoryForAction_ShouldReturnLatestFirst() {
        // Given
        PlannedActionEntry action = ObjectCreationUtils.createSimpleReminderEntry();
        service.recordExecution(action, "Exécution 1", true);
        service.recordExecution(action, "Exécution 2", true);
        service.recordExecution(action, "Exécution 3", false);

        // When
        List<ExecutionHistoryEntry> history = service.getHistoryForAction(action.getId(), 2);

        // Then
        assertEquals(2, history.size());
        assertEquals("Exécution 3", history.get(0).getResult());
        assertEquals("Exécution 2", history.get(1).getResult());
    }

    @Test
    void searchHistoryByLabel_ShouldMatchPartially() {
        // Given
        PlannedActionEntry action1 = ObjectCreationUtils.createSimpleReminderEntry();
        PlannedActionEntry action2 = ObjectCreationUtils.createComplexHabitEntry();
        service.recordExecution(action1, "Résultat dentiste", true);
        service.recordExecution(action2, "Résultat briefing", true);

        // When
        List<ExecutionHistoryEntry> results = service.searchHistoryByLabel("dentiste", 5);

        // Then
        assertEquals(1, results.size());
        assertEquals("Appeler le dentiste", results.get(0).getLabel());
    }

    @Test
    void recordExecution_WithContext_ShouldPreserveContext() {
        // Given
        PlannedActionEntry action = ObjectCreationUtils.createSimpleReminderWithContextEntry();

        // When
        service.recordExecution(action, "Rappel effectué", true);

        // Then
        List<ExecutionHistoryEntry> history = service.getHistoryForAction(action.getId(), 1);
        assertEquals(1, history.size());
        assertEquals("Numéro : 04 72 00 00 00, motif : détartrage annuel", history.get(0).getContext());
    }

    @Test
    void persistence_ShouldSurviveReload() {
        // Given
        PlannedActionEntry action = ObjectCreationUtils.createSimpleReminderEntry();
        service.recordExecution(action, "Résultat persisté", true);

        // When — create a new service instance pointing to the same file
        PlannedActionProperties props = new PlannedActionProperties();
        props.setHistoryStoragePath(tempDir.resolve("execution-history.json").toString());
        ExecutionHistoryService reloadedService = new ExecutionHistoryService(props);
        reloadedService.init();

        // Then
        List<ExecutionHistoryEntry> history = reloadedService.getHistoryForAction(action.getId(), 10);
        assertEquals(1, history.size());
        assertEquals("Résultat persisté", history.get(0).getResult());
    }
}
