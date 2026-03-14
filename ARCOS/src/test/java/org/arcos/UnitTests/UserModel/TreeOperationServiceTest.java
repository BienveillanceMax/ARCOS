package org.arcos.UnitTests.UserModel;

import org.arcos.UserModel.PersonaTree.*;
import org.arcos.UserModel.UserModelProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TreeOperationServiceTest {

    private TreeOperationService operationService;
    private PersonaTreeService treeService;
    private PersonaTreeSchemaLoader schemaLoader;

    @Mock
    private PersonaTreeRepository repository;

    private UserModelProperties properties;

    // Test paths
    private static final String HAIR_PATH = "1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair";
    private static final String INTELLIGENCE_PATH = "2_Psychological_Characteristics.Cognitive_Abilities.Intelligence_Level";
    private static final String MBTI_PATH = "3_Personality_Characteristics.Core_Personality.MBTI";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        properties = new UserModelProperties();
        properties.setPersonaTreeSchemaPath("persona-tree-schema.json");
        properties.setPersonaTreePath("data/persona-tree.json");
        properties.setDebounceSaveMs(100);

        schemaLoader = new PersonaTreeSchemaLoader(properties);
        schemaLoader.init();

        treeService = new PersonaTreeService(schemaLoader, repository, properties);
        treeService.initialize();

        operationService = new TreeOperationService(treeService, schemaLoader, properties);
    }

    // ========== Parsing Tests ==========

    @Test
    void parseAddOperation() {
        // Given
        String input = "ADD(1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair, \"cheveux bruns\")";

        // When
        List<TreeOperation> operations = operationService.parseOperations(input);

        // Then
        assertThat(operations).hasSize(1);
        TreeOperation op = operations.get(0);
        assertThat(op.type()).isEqualTo(TreeOperationType.ADD);
        assertThat(op.path()).isEqualTo(HAIR_PATH);
        assertThat(op.value()).isEqualTo("cheveux bruns");
    }

    @Test
    void parseUpdateOperation() {
        // Given
        String input = "UPDATE(2_Psychological_Characteristics.Cognitive_Abilities.Intelligence_Level, \"analytique\")";

        // When
        List<TreeOperation> operations = operationService.parseOperations(input);

        // Then
        assertThat(operations).hasSize(1);
        TreeOperation op = operations.get(0);
        assertThat(op.type()).isEqualTo(TreeOperationType.UPDATE);
        assertThat(op.path()).isEqualTo(INTELLIGENCE_PATH);
        assertThat(op.value()).isEqualTo("analytique");
    }

    @Test
    void parseDeleteOperation() {
        // Given
        String input = "DELETE(3_Personality_Characteristics.Core_Personality.MBTI, None)";

        // When
        List<TreeOperation> operations = operationService.parseOperations(input);

        // Then
        assertThat(operations).hasSize(1);
        TreeOperation op = operations.get(0);
        assertThat(op.type()).isEqualTo(TreeOperationType.DELETE);
        assertThat(op.path()).isEqualTo(MBTI_PATH);
        assertThat(op.value()).isNull();
    }

    @Test
    void parseNoOpOperation() {
        // Given
        String input = "NO_OP()";

        // When
        List<TreeOperation> operations = operationService.parseOperations(input);

        // Then
        assertThat(operations).hasSize(1);
        TreeOperation op = operations.get(0);
        assertThat(op.type()).isEqualTo(TreeOperationType.NO_OP);
        assertThat(op.path()).isNull();
        assertThat(op.value()).isNull();
    }

    @Test
    void parseMultiLineOutput() {
        // Given
        String input = """
                ADD(1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair, "cheveux bruns")
                UPDATE(2_Psychological_Characteristics.Cognitive_Abilities.Intelligence_Level, "analytique")
                DELETE(3_Personality_Characteristics.Core_Personality.MBTI, None)
                NO_OP()
                """;

        // When
        List<TreeOperation> operations = operationService.parseOperations(input);

        // Then
        assertThat(operations).hasSize(4);
        assertThat(operations.get(0).type()).isEqualTo(TreeOperationType.ADD);
        assertThat(operations.get(1).type()).isEqualTo(TreeOperationType.UPDATE);
        assertThat(operations.get(2).type()).isEqualTo(TreeOperationType.DELETE);
        assertThat(operations.get(3).type()).isEqualTo(TreeOperationType.NO_OP);
    }

    @Test
    void parseHandlesValueWithCommas() {
        // Given
        String input = "ADD(1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair, \"cheveux bruns, courts, bouclés\")";

        // When
        List<TreeOperation> operations = operationService.parseOperations(input);

        // Then
        assertThat(operations).hasSize(1);
        TreeOperation op = operations.get(0);
        assertThat(op.value()).isEqualTo("cheveux bruns, courts, bouclés");
    }

    @Test
    void parseSkipsUnparseableLines() {
        // Given
        String input = """
                ADD(1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair, "cheveux bruns")
                This is garbage
                Random text that won't parse
                UPDATE(2_Psychological_Characteristics.Cognitive_Abilities.Intelligence_Level, "analytique")
                """;

        // When
        List<TreeOperation> operations = operationService.parseOperations(input);

        // Then
        assertThat(operations).hasSize(2);
        assertThat(operations.get(0).type()).isEqualTo(TreeOperationType.ADD);
        assertThat(operations.get(1).type()).isEqualTo(TreeOperationType.UPDATE);
    }

    @Test
    void parseDeleteWithNullVariant() {
        // Given
        String input = "DELETE(3_Personality_Characteristics.Core_Personality.MBTI, null)";

        // When
        List<TreeOperation> operations = operationService.parseOperations(input);

        // Then
        assertThat(operations).hasSize(1);
        TreeOperation op = operations.get(0);
        assertThat(op.type()).isEqualTo(TreeOperationType.DELETE);
        assertThat(op.path()).isEqualTo(MBTI_PATH);
    }

    @Test
    void parseHandlesMixedCaseOperationNames() {
        // Given
        String input = """
                Add(1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair, "cheveux bruns")
                update(2_Psychological_Characteristics.Cognitive_Abilities.Intelligence_Level, "analytique")
                Delete(3_Personality_Characteristics.Core_Personality.MBTI, None)
                no_op()
                """;

        // When
        List<TreeOperation> operations = operationService.parseOperations(input);

        // Then
        assertThat(operations).hasSize(4);
        assertThat(operations.get(0).type()).isEqualTo(TreeOperationType.ADD);
        assertThat(operations.get(1).type()).isEqualTo(TreeOperationType.UPDATE);
        assertThat(operations.get(2).type()).isEqualTo(TreeOperationType.DELETE);
        assertThat(operations.get(3).type()).isEqualTo(TreeOperationType.NO_OP);
    }

    // ========== Application Tests ==========

    @Test
    void applyAddSetsLeafValue() {
        // Given
        TreeOperation addOp = new TreeOperation(TreeOperationType.ADD, HAIR_PATH, "cheveux bruns");

        // When
        List<TreeOperationResult> results = operationService.applyOperations(List.of(addOp));

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).success()).isTrue();
        assertThat(results.get(0).errorMessage()).isNull();

        // Verify the value was set
        String value = treeService.getLeafValue(HAIR_PATH);
        assertThat(value).isEqualTo("cheveux bruns");

        // Verify persist was called
        verify(repository, times(1)).save(any(), any());
    }

    @Test
    void applyDeleteClearsLeafValue() {
        // Given - first set a value
        treeService.setLeafValue(HAIR_PATH, "cheveux bruns");
        assertThat(treeService.getLeafValue(HAIR_PATH)).isEqualTo("cheveux bruns");

        // When - delete it
        TreeOperation deleteOp = new TreeOperation(TreeOperationType.DELETE, HAIR_PATH, null);
        List<TreeOperationResult> results = operationService.applyOperations(List.of(deleteOp));

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).success()).isTrue();
        assertThat(results.get(0).errorMessage()).isNull();

        // Verify the value was cleared
        String value = treeService.getLeafValue(HAIR_PATH);
        assertThat(value).isEmpty();

        // Verify persist was called
        verify(repository, times(1)).save(any(), any());
    }

    @Test
    void applyInvalidPathReturnsFalse() {
        // Given
        TreeOperation invalidOp = new TreeOperation(TreeOperationType.ADD, "Invalid.Path.Does.Not.Exist", "value");

        // When
        List<TreeOperationResult> results = operationService.applyOperations(List.of(invalidOp));

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).success()).isFalse();
        assertThat(results.get(0).errorMessage()).contains("Invalid or non-leaf path");

        // Verify persist was still called (after batch)
        verify(repository, times(1)).save(any(), any());
    }

    @Test
    void applyNoOpIsAlwaysSuccessful() {
        // Given
        TreeOperation noOp = new TreeOperation(TreeOperationType.NO_OP, null, null);

        // When
        List<TreeOperationResult> results = operationService.applyOperations(List.of(noOp));

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).success()).isTrue();
        assertThat(results.get(0).errorMessage()).isNull();

        // Verify persist was called
        verify(repository, times(1)).save(any(), any());
    }

    @Test
    void parseAndApplyEndToEnd() {
        // Given
        String rawOutput = """
                ADD(1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair, "cheveux bruns")
                UPDATE(2_Psychological_Characteristics.Cognitive_Abilities.Intelligence_Level, "analytique")
                """;

        // When
        List<TreeOperationResult> results = operationService.parseAndApply(rawOutput);

        // Then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).success()).isTrue();
        assertThat(results.get(1).success()).isTrue();

        // Verify values were set
        assertThat(treeService.getLeafValue(HAIR_PATH)).isEqualTo("cheveux bruns");
        assertThat(treeService.getLeafValue(INTELLIGENCE_PATH)).isEqualTo("analytique");

        // Verify persist was called once (after batch)
        verify(repository, times(1)).save(any(), any());
    }

    // ========== Truncation Tests ==========

    @Test
    void applyAdd_truncatesOverlongValue() {
        // Given
        properties.setLeafMaxChars(20);
        String longValue = "a]".repeat(25); // 50 chars
        TreeOperation addOp = new TreeOperation(TreeOperationType.ADD, HAIR_PATH, longValue);

        // When
        List<TreeOperationResult> results = operationService.applyOperations(List.of(addOp));

        // Then
        assertThat(results.get(0).success()).isTrue();
        String stored = treeService.getLeafValue(HAIR_PATH);
        assertThat(stored).hasSize(20);
    }

    @Test
    void applyUpdate_valueBelowLimit_storedAsIs() {
        // Given
        properties.setLeafMaxChars(300);
        String shortValue = "brun clair";
        TreeOperation addOp = new TreeOperation(TreeOperationType.ADD, HAIR_PATH, shortValue);

        // When
        List<TreeOperationResult> results = operationService.applyOperations(List.of(addOp));

        // Then
        assertThat(results.get(0).success()).isTrue();
        String stored = treeService.getLeafValue(HAIR_PATH);
        assertThat(stored).isEqualTo("brun clair");
    }
}
