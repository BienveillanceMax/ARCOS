package org.arcos.UnitTests.UserModel;

import org.arcos.UserModel.Models.ObservationLeaf;
import org.arcos.UserModel.Models.ObservationSource;
import org.arcos.UserModel.Models.TreeBranch;
import org.arcos.UserModel.Retrieval.BranchSummaryBuilder;
import org.arcos.UserModel.UserModelProperties;
import org.arcos.UserModel.UserObservationTree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BranchSummaryBuilderTest {

    private UserObservationTree tree;
    private BranchSummaryBuilder builder;
    private UserModelProperties properties;

    @BeforeEach
    void setUp() {
        properties = new UserModelProperties();
        tree = new UserObservationTree(properties);
        builder = new BranchSummaryBuilder(tree, properties);
    }

    @Test
    void rebuild_Identity_ShouldTruncateToMaxTokens() {
        properties.setIdentityBudgetTokens(25);
        tree = new UserObservationTree(properties);
        builder = new BranchSummaryBuilder(tree, properties);

        ObservationLeaf leaf1 = new ObservationLeaf("Mon créateur s'appelle Pierre et il est développeur Java senior", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED);
        leaf1.setObservationCount(5);
        tree.addLeaf(leaf1);

        ObservationLeaf leaf2 = new ObservationLeaf("Mon créateur habite à Lyon dans le quartier de la Presqu'île depuis longtemps", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED);
        leaf2.setObservationCount(3);
        tree.addLeaf(leaf2);

        String summary = builder.rebuild(TreeBranch.IDENTITE);

        assertNotNull(summary);
        int estimatedTokens = BranchSummaryBuilder.estimateTokens(summary);
        assertTrue(estimatedTokens <= 25, "Identity summary should be ≤25 tokens, got " + estimatedTokens);
    }

    @Test
    void rebuild_Communication_ShouldTruncateToMaxTokens() {
        properties.setCommunicationBudgetTokens(30);
        tree = new UserObservationTree(properties);
        builder = new BranchSummaryBuilder(tree, properties);

        ObservationLeaf leaf = new ObservationLeaf(
                "Mon créateur s'exprime de manière très concise et directe sans fioritures ni détails superflus",
                TreeBranch.COMMUNICATION, ObservationSource.HEURISTIC);
        leaf.setObservationCount(4);
        tree.addLeaf(leaf);

        ObservationLeaf leaf2 = new ObservationLeaf(
                "Mon créateur pose beaucoup de questions techniques très précises sur l'architecture logicielle",
                TreeBranch.COMMUNICATION, ObservationSource.HEURISTIC);
        leaf2.setObservationCount(2);
        tree.addLeaf(leaf2);

        String summary = builder.rebuild(TreeBranch.COMMUNICATION);

        assertNotNull(summary);
        int estimatedTokens = BranchSummaryBuilder.estimateTokens(summary);
        assertTrue(estimatedTokens <= 30, "Communication summary should be ≤30 tokens, got " + estimatedTokens);
    }

    @Test
    void rebuild_ShouldSortByObservationCountDescending() {
        ObservationLeaf lowCount = new ObservationLeaf("Leaf low", TreeBranch.INTERETS, ObservationSource.LLM_EXTRACTED);
        lowCount.setObservationCount(1);
        tree.addLeaf(lowCount);

        ObservationLeaf highCount = new ObservationLeaf("Leaf high", TreeBranch.INTERETS, ObservationSource.LLM_EXTRACTED);
        highCount.setObservationCount(10);
        tree.addLeaf(highCount);

        String summary = builder.rebuild(TreeBranch.INTERETS);

        assertNotNull(summary);
        assertTrue(summary.startsWith("Leaf high"));
    }

    @Test
    void rebuild_EmptyBranch_ShouldReturnNull() {
        String summary = builder.rebuild(TreeBranch.OBJECTIFS);
        assertNull(summary);
        assertNull(tree.getSummary(TreeBranch.OBJECTIFS));
    }

    @Test
    void rebuild_ShouldStoreSummaryInTree() {
        tree.addLeaf(new ObservationLeaf("Test leaf", TreeBranch.EMOTIONS, ObservationSource.LLM_EXTRACTED));

        builder.rebuild(TreeBranch.EMOTIONS);

        assertNotNull(tree.getSummary(TreeBranch.EMOTIONS));
        assertEquals("Test leaf", tree.getSummary(TreeBranch.EMOTIONS));
    }

    @Test
    void estimateTokens_ShouldApply1_3Factor() {
        assertEquals(0, BranchSummaryBuilder.estimateTokens(null));
        assertEquals(0, BranchSummaryBuilder.estimateTokens(""));
        assertEquals(2, BranchSummaryBuilder.estimateTokens("hello")); // ceil(1 * 1.3) = 2
        assertEquals(3, BranchSummaryBuilder.estimateTokens("hello world")); // ceil(2 * 1.3) = 3
    }
}
