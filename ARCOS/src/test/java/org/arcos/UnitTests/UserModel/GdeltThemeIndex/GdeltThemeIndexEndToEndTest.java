package org.arcos.UnitTests.UserModel.GdeltThemeIndex;

import org.arcos.UserModel.DfsNavigator.UserContextFormatter;
import org.arcos.UserModel.GdeltThemeIndex.*;
import org.arcos.UserModel.PersonaTree.*;
import org.arcos.UserModel.UserModelProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * End-to-end test: TreeOperationService applies an ADD on a GDELT-relevant leaf,
 * which triggers GdeltThemeIndexService.onLeafMutated(), extracts keywords via the LLM,
 * persists the index, and makes keywords available through the gate.
 */
@ExtendWith(MockitoExtension.class)
class GdeltThemeIndexEndToEndTest {

    // Real leaf paths from the schema under GDELT-relevant prefixes
    private static final String RELEVANT_LEAF = "4_Identity_Characteristics.Life_Beliefs.Political_Stance";
    private static final String RELEVANT_LEAF_2 = "5_Behavioral_Characteristics.Interests_and_Skills.Interests_and_Hobbies";
    private static final String IRRELEVANT_LEAF = "1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair";

    @Mock
    private OllamaChatModel ollamaChatModel;

    @Mock
    private PersonaTreeRepository treeRepository;

    @Mock
    private UserContextFormatter userContextFormatter;

    @TempDir
    Path tempDir;

    private TreeOperationService treeOperationService;
    private GdeltThemeIndexGate gate;
    private GdeltThemeIndexRepository gdeltRepository;

    @BeforeEach
    void setUp() {
        UserModelProperties userModelProperties = new UserModelProperties();
        userModelProperties.setPersonaTreeSchemaPath("persona-tree-schema.json");
        userModelProperties.setPersonaTreePath("data/persona-tree.json");

        PersonaTreeSchemaLoader schemaLoader = new PersonaTreeSchemaLoader(userModelProperties);
        schemaLoader.init();

        PersonaTreeService treeService = new PersonaTreeService(schemaLoader, treeRepository, userModelProperties);
        treeService.initialize();

        GdeltThemeIndexProperties gdeltProperties = new GdeltThemeIndexProperties();
        gdeltProperties.setPath(tempDir.resolve("gdelt-index.json").toString());
        gdeltProperties.setMaxKeywordsPerLeaf(5);

        gdeltRepository = new GdeltThemeIndexRepository();

        GdeltThemeExtractor extractor = new GdeltThemeExtractor(
                ollamaChatModel, gdeltProperties, userContextFormatter);

        GdeltThemeIndexService gdeltThemeIndexService = new GdeltThemeIndexService(
                gdeltRepository, extractor, gdeltProperties, treeService);

        treeOperationService = new TreeOperationService(
                treeService, schemaLoader, userModelProperties, gdeltThemeIndexService);

        gate = new GdeltThemeIndexGate(gdeltThemeIndexService);
    }

    @Test
    void addOnRelevantLeafTriggersExtractionAndIsReadableViaGate() {
        // Given
        when(userContextFormatter.humanReadablePath(any())).thenReturn("Croyances > Position politique");
        mockLlmResponse("fr:transition écologique\nen:climate change\nfr:environnement");

        String rawOp = "ADD(" + RELEVANT_LEAF + ", \"Écologiste convaincu, vote vert\")";

        // When
        List<TreeOperationResult> results = treeOperationService.parseAndApply(rawOp);

        // Then — operation succeeded
        assertThat(results).hasSize(1);
        assertThat(results.get(0).success()).isTrue();

        // And — GDELT index was populated
        assertThat(gate.isEmpty()).isFalse();
        List<GdeltKeyword> keywords = gate.getAllKeywords();
        assertThat(keywords).hasSize(3);
        assertThat(keywords).extracting(GdeltKeyword::term)
                .containsExactlyInAnyOrder("transition écologique", "climate change", "environnement");

        verify(ollamaChatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void addOnIrrelevantLeafDoesNotTriggerExtraction() {
        // Given
        String rawOp = "ADD(" + IRRELEVANT_LEAF + ", \"brun\")";

        // When
        List<TreeOperationResult> results = treeOperationService.parseAndApply(rawOp);

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).success()).isTrue();
        verify(ollamaChatModel, never()).call(any(Prompt.class));
        assertThat(gate.isEmpty()).isTrue();
    }

    @Test
    void deleteRemovesEntryFromIndex() {
        // Given — first, add an entry
        when(userContextFormatter.humanReadablePath(any())).thenReturn("Croyances");
        mockLlmResponse("fr:écologie\nen:ecology");
        treeOperationService.parseAndApply("ADD(" + RELEVANT_LEAF + ", \"Écologiste\")");
        assertThat(gate.isEmpty()).isFalse();

        // When — delete
        List<TreeOperationResult> results = treeOperationService.parseAndApply(
                "DELETE(" + RELEVANT_LEAF + ", None)");

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).success()).isTrue();
        assertThat(gate.isEmpty()).isTrue();
    }

    @Test
    void updateReplacesKeywords() {
        // Given — initial ADD
        when(userContextFormatter.humanReadablePath(any())).thenReturn("Croyances");
        mockLlmResponse("fr:écologie\nen:ecology");
        treeOperationService.parseAndApply("ADD(" + RELEVANT_LEAF + ", \"Écologiste\")");
        assertThat(gate.getAllKeywords()).extracting(GdeltKeyword::term)
                .containsExactlyInAnyOrder("écologie", "ecology");

        // When — UPDATE
        mockLlmResponse("fr:intelligence artificielle\nen:AI regulation");
        treeOperationService.parseAndApply(
                "UPDATE(" + RELEVANT_LEAF + ", \"S'intéresse à l'IA\")");

        // Then — old keywords replaced
        assertThat(gate.getAllKeywords()).extracting(GdeltKeyword::term)
                .containsExactlyInAnyOrder("intelligence artificielle", "AI regulation");
    }

    @Test
    void extractionFailureDoesNotBreakTreeOperation() {
        // Given — LLM throws
        when(userContextFormatter.humanReadablePath(any())).thenReturn("Croyances");
        when(ollamaChatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("Ollama down"));

        String rawOp = "ADD(" + RELEVANT_LEAF + ", \"value\")";

        // When
        List<TreeOperationResult> results = treeOperationService.parseAndApply(rawOp);

        // Then — tree operation still succeeded
        assertThat(results).hasSize(1);
        assertThat(results.get(0).success()).isTrue();
        assertThat(gate.isEmpty()).isTrue();
    }

    @Test
    void indexPersistsToFileAndCanBeReloaded() {
        // Given
        when(userContextFormatter.humanReadablePath(any())).thenReturn("Croyances");
        mockLlmResponse("fr:écologie\nen:ecology");
        treeOperationService.parseAndApply("ADD(" + RELEVANT_LEAF + ", \"Écologiste\")");

        // When — reload from file
        Path indexPath = tempDir.resolve("gdelt-index.json");
        var loaded = gdeltRepository.load(indexPath);

        // Then
        assertThat(loaded).hasSize(1);
        assertThat(loaded).containsKey(RELEVANT_LEAF);
        assertThat(loaded.values().iterator().next().keywords()).hasSize(2);
    }

    @Test
    void multipleRelevantLeavesAggregateInGate() {
        // Given
        when(userContextFormatter.humanReadablePath(any())).thenReturn("Category");
        mockLlmResponse("fr:politique\nen:politics");
        treeOperationService.parseAndApply("ADD(" + RELEVANT_LEAF + ", \"Engagé\")");

        mockLlmResponse("fr:programmation\nen:coding");
        treeOperationService.parseAndApply("ADD(" + RELEVANT_LEAF_2 + ", \"Développeur\")");

        // When
        List<GdeltKeyword> allKeywords = gate.getAllKeywords();

        // Then — keywords from both leaves aggregated
        assertThat(allKeywords).hasSize(4);
        assertThat(gate.getIndexedLeafCount()).isEqualTo(2);
    }

    // ========== Helper ==========

    private void mockLlmResponse(String text) {
        AssistantMessage message = new AssistantMessage(text);
        Generation generation = new Generation(message);
        ChatResponse response = new ChatResponse(List.of(generation));
        when(ollamaChatModel.call(any(Prompt.class))).thenReturn(response);
    }
}
