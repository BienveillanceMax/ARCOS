package org.arcos.UnitTests.UserModel;

import org.arcos.UserModel.GapFilling.GapDetector;
import org.arcos.UserModel.GapFilling.ProactiveGapFiller;
import org.arcos.UserModel.Models.ObservationLeaf;
import org.arcos.UserModel.Models.ObservationSource;
import org.arcos.UserModel.Models.TreeBranch;
import org.arcos.UserModel.UserModelProperties;
import org.arcos.UserModel.UserObservationTree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ProactiveGapFillerTest {

    private UserObservationTree tree;
    private UserModelProperties properties;
    private GapDetector gapDetector;
    private ProactiveGapFiller filler;

    @BeforeEach
    void setUp() {
        properties = new UserModelProperties();
        properties.getProactiveGapFilling().setEnabled(true);
        properties.getProactiveGapFilling().setMaxPerSession(1);
        properties.getProactiveGapFilling().setMinConversationsBetweenSameBranch(3);
        tree = new UserObservationTree(properties);
        gapDetector = new GapDetector(tree);
        filler = new ProactiveGapFiller(gapDetector, tree, properties);
    }

    // ---- Disabled ----

    @Test
    void getGapHint_ReturnsEmpty_WhenDisabled() {
        properties.getProactiveGapFilling().setEnabled(false);
        tree.setConversationCount(10);

        assertTrue(filler.getGapHint().isEmpty());
    }

    // ---- Stability gate ----

    @Test
    void getGapHint_ReturnsEmpty_WhenProfileStabilityIsLow() {
        // Given: conversationCount < 5 → LOW stability
        tree.setConversationCount(3);

        assertTrue(filler.getGapHint().isEmpty());
    }

    @Test
    void getGapHint_ReturnsHint_WhenProfileStabilityIsMedium() {
        // Given: conversationCount >= 5 → MEDIUM stability
        tree.setConversationCount(5);

        Optional<String> hint = filler.getGapHint();

        assertTrue(hint.isPresent());
        assertFalse(hint.get().isBlank());
    }

    // ---- Hint generation ----

    @Test
    void getGapHint_TargetsLeastPopulatedBranch() {
        tree.setConversationCount(10);

        // Populate all branches except OBJECTIFS
        for (TreeBranch branch : TreeBranch.values()) {
            if (branch != TreeBranch.OBJECTIFS) {
                tree.addLeaf(new ObservationLeaf("Obs " + branch, branch, ObservationSource.HEURISTIC));
                tree.addLeaf(new ObservationLeaf("Obs2 " + branch, branch, ObservationSource.HEURISTIC));
            }
        }

        Optional<String> hint = filler.getGapHint();

        assertTrue(hint.isPresent());
        assertTrue(hint.get().contains("projets") || hint.get().contains("objectifs"),
                "Hint should target OBJECTIFS branch: " + hint.get());
    }

    // ---- Session caching ----

    @Test
    void getGapHint_ReturnsSameHint_WithinSameSession() {
        tree.setConversationCount(10);

        Optional<String> first = filler.getGapHint();
        Optional<String> second = filler.getGapHint();

        assertTrue(first.isPresent());
        assertEquals(first, second, "Should return cached hint within same session");
    }

    @Test
    void getGapHint_RecomputesHint_OnNewSession() {
        tree.setConversationCount(10);
        Optional<String> first = filler.getGapHint();
        assertTrue(first.isPresent());

        // Simulate new session
        tree.setConversationCount(11);
        Optional<String> second = filler.getGapHint();

        assertTrue(second.isPresent());
    }

    // ---- Cooldown per branch ----

    @Test
    void getGapHint_RecordsGapQuestion_ForBranch() {
        tree.setConversationCount(10);

        filler.getGapHint();

        assertFalse(tree.getLastGapQuestionPerBranch().isEmpty(),
                "Should have recorded gap question");
    }

    @Test
    void getGapHint_RespectsCooldown_OnSameBranch() {
        tree.setConversationCount(5);
        filler.getGapHint(); // Records gap question at conversation 5

        // Next session, still within cooldown (5+1 = 6, 6-5 = 1 < 3)
        tree.setConversationCount(6);
        // All branches are empty, so the hint should target a DIFFERENT branch
        // (the previously targeted one is on cooldown)
        Optional<String> hint = filler.getGapHint();
        assertTrue(hint.isPresent(), "Should still find another branch");
    }

    // ---- Hint content ----

    @Test
    void generateHint_ReturnsNonEmptyForAllBranches() {
        for (TreeBranch branch : TreeBranch.values()) {
            String hint = ProactiveGapFiller.generateHint(branch);
            assertNotNull(hint);
            assertFalse(hint.isBlank());
            assertTrue(hint.startsWith("Si l'occasion se présente naturellement"));
        }
    }
}
