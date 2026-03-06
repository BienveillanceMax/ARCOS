package org.arcos.UnitTests.UserModel;

import org.arcos.UserModel.GapFilling.GapDetector;
import org.arcos.UserModel.Models.ObservationLeaf;
import org.arcos.UserModel.Models.ObservationSource;
import org.arcos.UserModel.Models.TreeBranch;
import org.arcos.UserModel.UserModelProperties;
import org.arcos.UserModel.UserObservationTree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GapDetectorTest {

    private UserObservationTree tree;
    private GapDetector detector;

    @BeforeEach
    void setUp() {
        tree = new UserObservationTree(new UserModelProperties());
        detector = new GapDetector(tree);
    }

    @Test
    void findLeastPopulatedBranch_ReturnsEmptyBranch_WhenAllEmpty() {
        // Given: all branches empty, no cooldowns
        Optional<TreeBranch> result = detector.findLeastPopulatedBranch(
                new EnumMap<>(TreeBranch.class), 10, 3);

        assertTrue(result.isPresent());
        // First enum value with 0 leaves
        assertEquals(TreeBranch.IDENTITE, result.get());
    }

    @Test
    void findLeastPopulatedBranch_ReturnsLeastPopulated() {
        // Given: IDENTITE has 3 leaves, COMMUNICATION has 1, others empty
        tree.addLeaf(new ObservationLeaf("A", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED));
        tree.addLeaf(new ObservationLeaf("B", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED));
        tree.addLeaf(new ObservationLeaf("C", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED));
        tree.addLeaf(new ObservationLeaf("D", TreeBranch.COMMUNICATION, ObservationSource.HEURISTIC));

        Optional<TreeBranch> result = detector.findLeastPopulatedBranch(
                new EnumMap<>(TreeBranch.class), 10, 3);

        assertTrue(result.isPresent());
        // Should be one of the branches with 0 leaves (HABITUDES, OBJECTIFS, EMOTIONS, INTERETS)
        assertEquals(0, tree.getActiveLeaves(result.get()).size());
    }

    @Test
    void findLeastPopulatedBranch_RespectsCooldown() {
        // Given: all branches empty, but IDENTITE was asked recently
        Map<TreeBranch, Integer> lastGap = new EnumMap<>(TreeBranch.class);
        lastGap.put(TreeBranch.IDENTITE, 9); // Asked at conversation 9

        Optional<TreeBranch> result = detector.findLeastPopulatedBranch(lastGap, 10, 3);

        assertTrue(result.isPresent());
        assertNotEquals(TreeBranch.IDENTITE, result.get(),
                "Should skip IDENTITE because cooldown not met (10-9 < 3)");
    }

    @Test
    void findLeastPopulatedBranch_AllowsAfterCooldown() {
        // Given: IDENTITE was asked at conversation 5, current is 10 (gap = 5 >= 3)
        Map<TreeBranch, Integer> lastGap = new EnumMap<>(TreeBranch.class);
        lastGap.put(TreeBranch.IDENTITE, 5);

        // Make other branches populated
        tree.addLeaf(new ObservationLeaf("A", TreeBranch.COMMUNICATION, ObservationSource.HEURISTIC));
        tree.addLeaf(new ObservationLeaf("B", TreeBranch.HABITUDES, ObservationSource.HEURISTIC));
        tree.addLeaf(new ObservationLeaf("C", TreeBranch.OBJECTIFS, ObservationSource.HEURISTIC));
        tree.addLeaf(new ObservationLeaf("D", TreeBranch.EMOTIONS, ObservationSource.HEURISTIC));
        tree.addLeaf(new ObservationLeaf("E", TreeBranch.INTERETS, ObservationSource.HEURISTIC));

        Optional<TreeBranch> result = detector.findLeastPopulatedBranch(lastGap, 10, 3);

        assertTrue(result.isPresent());
        assertEquals(TreeBranch.IDENTITE, result.get(),
                "IDENTITE should be eligible after cooldown");
    }

    @Test
    void findLeastPopulatedBranch_ReturnsEmpty_WhenAllOnCooldown() {
        // Given: all branches recently asked
        Map<TreeBranch, Integer> lastGap = new EnumMap<>(TreeBranch.class);
        for (TreeBranch branch : TreeBranch.values()) {
            lastGap.put(branch, 9);
        }

        Optional<TreeBranch> result = detector.findLeastPopulatedBranch(lastGap, 10, 3);

        assertFalse(result.isPresent(), "All branches on cooldown → empty");
    }
}
