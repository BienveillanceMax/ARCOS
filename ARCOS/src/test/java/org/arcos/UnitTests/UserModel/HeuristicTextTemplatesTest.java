package org.arcos.UnitTests.UserModel;

import org.arcos.UserModel.Heuristics.HeuristicTextTemplates;
import org.arcos.UserModel.Models.ObservationLeaf;
import org.arcos.UserModel.Models.SignificantChange;
import org.arcos.UserModel.Models.TreeBranch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HeuristicTextTemplatesTest {

    private HeuristicTextTemplates templates;

    @BeforeEach
    void setUp() {
        templates = new HeuristicTextTemplates();
    }

    @Test
    void generateLeaves_ColdStart_ShouldReturnEmptyList() {
        // Given — conversationCount < 5
        List<SignificantChange> changes = List.of(
                new SignificantChange("avg_word_count", 15.0, 8.0, TreeBranch.COMMUNICATION)
        );

        // When
        List<ObservationLeaf> leaves = templates.generateLeaves(changes, 3);

        // Then
        assertTrue(leaves.isEmpty());
    }

    @Test
    void generateLeaves_AvgWordCountLow_ShouldGenerateConciseTemplate() {
        // Given — avg_word_count with newValue=8 (< 11)
        List<SignificantChange> changes = List.of(
                new SignificantChange("avg_word_count", 15.0, 8.0, TreeBranch.COMMUNICATION)
        );

        // When
        List<ObservationLeaf> leaves = templates.generateLeaves(changes, 10);

        // Then
        assertEquals(1, leaves.size());
        assertTrue(leaves.get(0).getText().contains("concise"));
    }

    @Test
    void generateLeaves_AvgWordCountHigh_ShouldGenerateDetailedTemplate() {
        // Given — avg_word_count with newValue=25 (> 20)
        List<SignificantChange> changes = List.of(
                new SignificantChange("avg_word_count", 10.0, 25.0, TreeBranch.COMMUNICATION)
        );

        // When
        List<ObservationLeaf> leaves = templates.generateLeaves(changes, 10);

        // Then
        assertEquals(1, leaves.size());
        assertTrue(leaves.get(0).getText().contains("détaillée"));
    }

    @Test
    void generateLeaves_AllLeavesStartWithMonCreateur() {
        // Given — multiple changes that produce leaves
        List<SignificantChange> changes = List.of(
                new SignificantChange("avg_word_count", 15.0, 8.0, TreeBranch.COMMUNICATION),
                new SignificantChange("avg_word_length", 4.0, 5.0, TreeBranch.COMMUNICATION),
                new SignificantChange("question_ratio", 0.2, 0.5, TreeBranch.COMMUNICATION),
                new SignificantChange("time_of_day", 10.0, 14.0, TreeBranch.HABITUDES),
                new SignificantChange("vocabulary_diversity", 0.5, 0.8, TreeBranch.COMMUNICATION)
        );

        // When
        List<ObservationLeaf> leaves = templates.generateLeaves(changes, 10);

        // Then
        assertFalse(leaves.isEmpty());
        for (ObservationLeaf leaf : leaves) {
            assertTrue(leaf.getText().startsWith("Mon créateur"),
                    "Leaf text should start with 'Mon créateur' but was: " + leaf.getText());
        }
    }
}
