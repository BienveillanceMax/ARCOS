package org.arcos.UnitTests.UserModel;

import org.arcos.UserModel.Models.ObservationLeaf;
import org.arcos.UserModel.Models.ObservationSource;
import org.arcos.UserModel.Models.ProfileStability;
import org.arcos.UserModel.Models.TreeBranch;
import org.arcos.UserModel.UserModelProperties;
import org.arcos.UserModel.UserObservationTree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class UserObservationTreeTest {

    private UserObservationTree tree;
    private UserModelProperties properties;

    @BeforeEach
    void setUp() {
        properties = new UserModelProperties();
        properties.setMaxActiveObservations(300);
        tree = new UserObservationTree(properties);
    }

    @Test
    void addLeaf_ShouldAddLeafToCorrectBranch() {
        ObservationLeaf leaf = new ObservationLeaf("Mon créateur s'appelle Pierre", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED);

        tree.addLeaf(leaf);

        List<ObservationLeaf> identityLeaves = tree.getActiveLeaves(TreeBranch.IDENTITE);
        assertEquals(1, identityLeaves.size());
        assertEquals("Mon créateur s'appelle Pierre", identityLeaves.get(0).getText());
    }

    @Test
    void addLeaf_ShouldRejectWhenMaxReached() {
        properties.setMaxActiveObservations(5);
        tree = new UserObservationTree(properties);

        for (int i = 0; i < 5; i++) {
            tree.addLeaf(new ObservationLeaf("leaf " + i, TreeBranch.IDENTITE, ObservationSource.HEURISTIC));
        }

        ObservationLeaf extra = new ObservationLeaf("extra leaf", TreeBranch.IDENTITE, ObservationSource.HEURISTIC);
        tree.addLeaf(extra);

        assertEquals(5, tree.getAllActiveLeaves().size());
    }

    @Test
    void findLeafById_ShouldReturnLeaf() {
        ObservationLeaf leaf = new ObservationLeaf("test", TreeBranch.INTERETS, ObservationSource.LLM_EXTRACTED);
        tree.addLeaf(leaf);

        Optional<ObservationLeaf> found = tree.findLeafById(leaf.getId());

        assertTrue(found.isPresent());
        assertEquals(leaf.getId(), found.get().getId());
    }

    @Test
    void findLeafById_ShouldReturnEmptyWhenNotFound() {
        Optional<ObservationLeaf> found = tree.findLeafById("nonexistent");
        assertTrue(found.isEmpty());
    }

    @Test
    void removeLeaf_ShouldRemoveAndReturnTrue() {
        ObservationLeaf leaf = new ObservationLeaf("to remove", TreeBranch.HABITUDES, ObservationSource.HEURISTIC);
        tree.addLeaf(leaf);

        assertTrue(tree.removeLeaf(leaf.getId()));
        assertTrue(tree.getAllActiveLeaves().isEmpty());
    }

    @Test
    void removeLeaf_ShouldReturnFalseWhenNotFound() {
        assertFalse(tree.removeLeaf("nonexistent"));
    }

    @Test
    void getAllActiveLeaves_ShouldReturnLeavesFromAllBranches() {
        tree.addLeaf(new ObservationLeaf("id", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED));
        tree.addLeaf(new ObservationLeaf("comm", TreeBranch.COMMUNICATION, ObservationSource.HEURISTIC));
        tree.addLeaf(new ObservationLeaf("habit", TreeBranch.HABITUDES, ObservationSource.LLM_EXTRACTED));

        assertEquals(3, tree.getAllActiveLeaves().size());
    }

    @Test
    void conversationCount_ShouldIncrementAndUpdateStability() {
        assertEquals(0, tree.getConversationCount());
        assertEquals(ProfileStability.LOW, tree.getProfileStability());

        for (int i = 0; i < 5; i++) {
            tree.incrementConversationCount();
        }
        assertEquals(5, tree.getConversationCount());
        assertEquals(ProfileStability.MEDIUM, tree.getProfileStability());

        for (int i = 0; i < 5; i++) {
            tree.incrementConversationCount();
        }
        assertEquals(10, tree.getConversationCount());
        assertEquals(ProfileStability.HIGH, tree.getProfileStability());
    }

    @Test
    void setConversationCount_ShouldSetAndUpdateStability() {
        tree.setConversationCount(12);
        assertEquals(12, tree.getConversationCount());
        assertEquals(ProfileStability.HIGH, tree.getProfileStability());
    }

    @Test
    void summaries_ShouldSetAndGet() {
        tree.setSummary(TreeBranch.IDENTITE, "Pierre, développeur");
        assertEquals("Pierre, développeur", tree.getSummary(TreeBranch.IDENTITE));
        assertNull(tree.getSummary(TreeBranch.COMMUNICATION));
    }

    @Test
    void heuristicBaselines_ShouldSetAndGet() {
        tree.setHeuristicBaselines(java.util.Map.of("avg_word_count", 15.0));
        assertEquals(15.0, tree.getHeuristicBaselines().get("avg_word_count"));
    }

    @Test
    void concurrentAccess_ShouldNotCorruptData() throws InterruptedException {
        int threadCount = 10;
        int leavesPerThread = 20;
        properties.setMaxActiveObservations(threadCount * leavesPerThread + 100);
        tree = new UserObservationTree(properties);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<String> addedIds = java.util.Collections.synchronizedList(new ArrayList<>());

        for (int t = 0; t < threadCount; t++) {
            final int threadIdx = t;
            executor.submit(() -> {
                try {
                    TreeBranch branch = TreeBranch.values()[threadIdx % TreeBranch.values().length];
                    for (int i = 0; i < leavesPerThread; i++) {
                        ObservationLeaf leaf = new ObservationLeaf(
                                "Thread " + threadIdx + " leaf " + i, branch, ObservationSource.HEURISTIC);
                        tree.addLeaf(leaf);
                        addedIds.add(leaf.getId());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(threadCount * leavesPerThread, tree.getAllActiveLeaves().size());

        for (String id : addedIds) {
            assertTrue(tree.findLeafById(id).isPresent());
        }
    }

    @Test
    void replaceAll_ShouldReplaceTreeData() {
        tree.addLeaf(new ObservationLeaf("old", TreeBranch.IDENTITE, ObservationSource.HEURISTIC));

        java.util.Map<TreeBranch, List<ObservationLeaf>> newBranches = new java.util.EnumMap<>(TreeBranch.class);
        for (TreeBranch b : TreeBranch.values()) {
            newBranches.put(b, new ArrayList<>());
        }
        newBranches.get(TreeBranch.COMMUNICATION).add(
                new ObservationLeaf("new", TreeBranch.COMMUNICATION, ObservationSource.LLM_EXTRACTED));

        java.util.Map<TreeBranch, String> newSummaries = new java.util.EnumMap<>(TreeBranch.class);
        newSummaries.put(TreeBranch.COMMUNICATION, "Summary");

        tree.replaceAll(newBranches, 7, newSummaries, java.util.Map.of("signal", 1.0));

        assertTrue(tree.getActiveLeaves(TreeBranch.IDENTITE).isEmpty());
        assertEquals(1, tree.getActiveLeaves(TreeBranch.COMMUNICATION).size());
        assertEquals(7, tree.getConversationCount());
        assertEquals(ProfileStability.MEDIUM, tree.getProfileStability());
        assertEquals("Summary", tree.getSummary(TreeBranch.COMMUNICATION));
        assertEquals(1.0, tree.getHeuristicBaselines().get("signal"));
    }
}
