package org.arcos.UnitTests.UserModel;

import org.arcos.UserModel.PersonaTree.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonaTreeGateTest {

    @Mock
    private PersonaTreeService treeService;

    @Mock
    private TreeOperationService operationService;

    @Mock
    private PersonaTreeSchemaLoader schemaLoader;

    @InjectMocks
    private PersonaTreeGate gate;

    @Test
    void getLeafValueDelegatesToService() {
        // Given
        String path = "1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair";
        when(treeService.getLeafValue(path)).thenReturn("brown");

        // When
        Optional<String> result = gate.getLeafValue(path);

        // Then
        assertTrue(result.isPresent());
        assertEquals("brown", result.get());
        verify(treeService).getLeafValue(path);
    }

    @Test
    void getLeafValueReturnsEmptyForEmptyString() {
        // Given
        String path = "1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair";
        when(treeService.getLeafValue(path)).thenReturn("");

        // When
        Optional<String> result = gate.getLeafValue(path);

        // Then
        assertFalse(result.isPresent());
        verify(treeService).getLeafValue(path);
    }

    @Test
    void getLeafValueReturnsEmptyForNull() {
        // Given
        String path = "1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair";
        when(treeService.getLeafValue(path)).thenReturn(null);

        // When
        Optional<String> result = gate.getLeafValue(path);

        // Then
        assertFalse(result.isPresent());
        verify(treeService).getLeafValue(path);
    }

    @Test
    void getNonEmptyLeavesDelegates() {
        // Given
        Map<String, String> expectedMap = Map.of(
                "path1", "value1",
                "path2", "value2"
        );
        when(treeService.getNonEmptyLeaves()).thenReturn(expectedMap);

        // When
        Map<String, String> result = gate.getNonEmptyLeaves();

        // Then
        assertEquals(expectedMap, result);
        verify(treeService).getNonEmptyLeaves();
    }

    @Test
    void getLeavesUnderPathDelegates() {
        // Given
        String pathPrefix = "1_Biological_Characteristics";
        Map<String, String> expectedMap = Map.of(
                "1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair", "brown"
        );
        when(treeService.getLeavesUnderPath(pathPrefix)).thenReturn(expectedMap);

        // When
        Map<String, String> result = gate.getLeavesUnderPath(pathPrefix);

        // Then
        assertEquals(expectedMap, result);
        verify(treeService).getLeavesUnderPath(pathPrefix);
    }

    @Test
    void applyRawOperationsDelegates() {
        // Given
        String rawOutput = "ADD(path1, \"value1\")\nUPDATE(path2, \"value2\")";
        TreeOperation op1 = new TreeOperation(TreeOperationType.ADD, "path1", "value1");
        TreeOperation op2 = new TreeOperation(TreeOperationType.UPDATE, "path2", "value2");
        List<TreeOperationResult> expectedResults = List.of(
                new TreeOperationResult(op1, true, null),
                new TreeOperationResult(op2, true, null)
        );
        when(operationService.parseAndApply(rawOutput)).thenReturn(expectedResults);

        // When
        List<TreeOperationResult> results = gate.applyRawOperations(rawOutput);

        // Then
        assertEquals(expectedResults, results);
        verify(operationService).parseAndApply(rawOutput);
    }

    @Test
    void applyOperationsDelegates() {
        // Given
        TreeOperation op1 = new TreeOperation(TreeOperationType.ADD, "path1", "value1");
        TreeOperation op2 = new TreeOperation(TreeOperationType.UPDATE, "path2", "value2");
        List<TreeOperation> operations = List.of(op1, op2);
        List<TreeOperationResult> expectedResults = List.of(
                new TreeOperationResult(op1, true, null),
                new TreeOperationResult(op2, true, null)
        );
        when(operationService.applyOperations(operations)).thenReturn(expectedResults);

        // When
        List<TreeOperationResult> results = gate.applyOperations(operations);

        // Then
        assertEquals(expectedResults, results);
        verify(operationService).applyOperations(operations);
    }

    @Test
    void getFilledLeafCountDelegates() {
        // Given
        when(treeService.getNonEmptyLeafCount()).thenReturn(42);

        // When
        int result = gate.getFilledLeafCount();

        // Then
        assertEquals(42, result);
        verify(treeService).getNonEmptyLeafCount();
    }

    @Test
    void getConversationCountDelegates() {
        // Given
        when(treeService.getConversationCount()).thenReturn(100);

        // When
        int result = gate.getConversationCount();

        // Then
        assertEquals(100, result);
        verify(treeService).getConversationCount();
    }

    @Test
    void incrementConversationCountDelegates() {
        // Given / When
        gate.incrementConversationCount();

        // Then
        verify(treeService).incrementConversationCount();
    }

    @Test
    void persistDelegates() {
        // Given / When
        gate.persist();

        // Then
        verify(treeService).persist();
    }

    @Test
    void createSnapshotDelegates() {
        // Given
        Path expectedPath = Paths.get("/data/persona-tree-snapshot-20260314-120000.json");
        when(treeService.createSnapshot()).thenReturn(expectedPath);

        // When
        Path result = gate.createSnapshot();

        // Then
        assertEquals(expectedPath, result);
        verify(treeService).createSnapshot();
    }

    @Test
    void getValidLeafPathsDelegates() {
        // Given
        Set<String> expectedPaths = Set.of(
                "1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair",
                "2_Psychological_Attributes.Personality.Traits.Openness"
        );
        when(schemaLoader.getValidLeafPaths()).thenReturn(expectedPaths);

        // When
        Set<String> result = gate.getValidLeafPaths();

        // Then
        assertEquals(expectedPaths, result);
        verify(schemaLoader).getValidLeafPaths();
    }

    @Test
    void isValidLeafPathDelegates() {
        // Given
        String path = "1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair";
        when(schemaLoader.isValidLeafPath(path)).thenReturn(true);

        // When
        boolean result = gate.isValidLeafPath(path);

        // Then
        assertTrue(result);
        verify(schemaLoader).isValidLeafPath(path);
    }
}
