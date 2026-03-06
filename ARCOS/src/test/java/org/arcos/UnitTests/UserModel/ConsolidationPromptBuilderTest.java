package org.arcos.UnitTests.UserModel;

import org.arcos.LLM.Local.ThinkingMode;
import org.arcos.UserModel.Consolidation.ConsolidationPromptBuilder;
import org.arcos.UserModel.Models.ObservationLeaf;
import org.arcos.UserModel.Models.ObservationSource;
import org.arcos.UserModel.Models.TreeBranch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConsolidationPromptBuilderTest {

    private ConsolidationPromptBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new ConsolidationPromptBuilder();
    }

    @Test
    void buildCrossBranchConflict_shouldProduceThinkPrefixedPromptWithMetadata() {
        // Given
        ObservationLeaf leafA = new ObservationLeaf("Pierre aime le cafe", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED);
        ObservationLeaf leafB = new ObservationLeaf("Il boit du cafe le matin", TreeBranch.HABITUDES, ObservationSource.HEURISTIC);

        // When
        String prompt = builder.buildCrossBranchConflict(leafA, leafB, 0.72);

        // Then
        assertTrue(prompt.startsWith(ThinkingMode.THINK.getPrefix()));
        assertTrue(prompt.contains("Pierre aime le cafe"));
        assertTrue(prompt.contains("Il boit du cafe le matin"));
        assertTrue(prompt.contains("IDENTITE"));
        assertTrue(prompt.contains("HABITUDES"));
        assertTrue(prompt.contains("0.72"));
        assertTrue(prompt.contains("Reponds UNIQUEMENT en JSON strict"));
        assertTrue(prompt.contains("MERGE"));
        assertTrue(prompt.contains("KEEP_BOTH"));
    }

    @Test
    void buildIntraBranchConsolidation_shouldProduceThinkPrefixedPromptWithSimilarLeaves() {
        // Given
        ObservationLeaf target = new ObservationLeaf("Pierre est developpeur", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED);
        ObservationLeaf similar1 = new ObservationLeaf("Pierre fait du code", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED);
        ObservationLeaf similar2 = new ObservationLeaf("Pierre programme en Java", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED);

        // When
        String prompt = builder.buildIntraBranchConsolidation(target, List.of(similar1, similar2));

        // Then
        assertTrue(prompt.startsWith(ThinkingMode.THINK.getPrefix()));
        assertTrue(prompt.contains("Pierre est developpeur"));
        assertTrue(prompt.contains("Pierre fait du code"));
        assertTrue(prompt.contains("Pierre programme en Java"));
        assertTrue(prompt.contains("IDENTITE"));
        assertTrue(prompt.contains("Reponds UNIQUEMENT en JSON strict"));
    }

    @Test
    void buildSimpleMerge_shouldProduceNoThinkPrefixedPrompt() {
        // Given
        ObservationLeaf leafA = new ObservationLeaf("Il aime le the", TreeBranch.INTERETS, ObservationSource.LLM_EXTRACTED);
        ObservationLeaf leafB = new ObservationLeaf("Il aime le the vert", TreeBranch.INTERETS, ObservationSource.LLM_EXTRACTED);

        // When
        String prompt = builder.buildSimpleMerge(leafA, leafB);

        // Then
        assertTrue(prompt.startsWith(ThinkingMode.NO_THINK.getPrefix()));
        assertTrue(prompt.contains("Il aime le the"));
        assertTrue(prompt.contains("Il aime le the vert"));
        assertTrue(prompt.contains("winner_id"));
        assertTrue(prompt.contains("Reponds UNIQUEMENT en JSON strict"));
    }

    @Test
    void buildCrossSessionPattern_shouldProduceThinkPrefixedPromptWithCluster() {
        // Given
        ObservationLeaf leaf1 = new ObservationLeaf("Parle le matin", TreeBranch.HABITUDES, ObservationSource.HEURISTIC);
        ObservationLeaf leaf2 = new ObservationLeaf("Actif vers 8h", TreeBranch.HABITUDES, ObservationSource.HEURISTIC);
        ObservationLeaf leaf3 = new ObservationLeaf("Sessions matinales", TreeBranch.HABITUDES, ObservationSource.HEURISTIC);

        // When
        String prompt = builder.buildCrossSessionPattern(List.of(leaf1, leaf2, leaf3), TreeBranch.HABITUDES);

        // Then
        assertTrue(prompt.startsWith(ThinkingMode.THINK.getPrefix()));
        assertTrue(prompt.contains("Parle le matin"));
        assertTrue(prompt.contains("Actif vers 8h"));
        assertTrue(prompt.contains("Sessions matinales"));
        assertTrue(prompt.contains("HABITUDES"));
        assertTrue(prompt.contains("CREATE_SUMMARY"));
        assertTrue(prompt.contains("NO_PATTERN"));
        assertTrue(prompt.contains("Reponds UNIQUEMENT en JSON strict"));
    }
}
