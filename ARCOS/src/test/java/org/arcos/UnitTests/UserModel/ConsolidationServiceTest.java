package org.arcos.UnitTests.UserModel;

import org.arcos.LLM.Local.LocalLlmService;
import org.arcos.UserModel.Consolidation.ConsolidationPromptBuilder;
import org.arcos.UserModel.Consolidation.ConsolidationService;
import org.arcos.UserModel.Consolidation.Models.ConsolidationResult;
import org.arcos.UserModel.Embedding.LocalEmbeddingService;
import org.arcos.UserModel.Models.ObservationLeaf;
import org.arcos.UserModel.Models.ObservationSource;
import org.arcos.UserModel.Models.TreeBranch;
import org.arcos.UserModel.Persistence.UserTreePersistenceService;
import org.arcos.UserModel.Retrieval.BranchSummaryBuilder;
import org.arcos.UserModel.UserModelProperties;
import org.arcos.UserModel.UserObservationTree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConsolidationServiceTest {

    @Mock private LocalLlmService localLlmService;
    @Mock private UserTreePersistenceService persistenceService;
    @Mock private LocalEmbeddingService embeddingService;
    @Mock private BranchSummaryBuilder summaryBuilder;

    private UserModelProperties properties;
    private UserObservationTree tree;
    private ConsolidationPromptBuilder promptBuilder;
    private ConsolidationService service;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        properties = new UserModelProperties();
        properties.getConsolidation().setEnabled(true);
        properties.getConsolidation().setMaxParseErrors(3);
        properties.getConsolidation().setMaxTimeoutsPerRun(3);
        properties.getConsolidation().setDecisionConfidenceThreshold(0.6);
        properties.getConsolidation().setTimeoutMs(5000);
        properties.getConsolidation().setMinLeavesForPercentageCheck(20);
        properties.getConsolidation().setMaxAbsoluteRemovalWhenBelowFloor(3);

        tree = new UserObservationTree(properties);
        promptBuilder = new ConsolidationPromptBuilder();

        when(persistenceService.createSnapshot()).thenReturn(Path.of("/tmp/snapshot.json"));
        when(localLlmService.isAvailable()).thenReturn(true);
        when(summaryBuilder.rebuild(any())).thenReturn("summary");

        service = new ConsolidationService(
                localLlmService, tree, persistenceService,
                promptBuilder, embeddingService, summaryBuilder, properties);
    }

    @Test
    void successfulCycle_shouldProcessConflictsAndReturnMetrics() throws Exception {
        // Given — two leaves with conflictsWith
        ObservationLeaf leafA = new ObservationLeaf("Pierre aime le cafe", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED);
        ObservationLeaf leafB = new ObservationLeaf("Il boit du cafe le matin", TreeBranch.HABITUDES, ObservationSource.HEURISTIC);
        leafA.setConflictsWith(leafB.getId());
        leafA.setNeedsConsolidation(true);
        tree.addLeaf(leafA);
        tree.addLeaf(leafB);

        String mergeJson = "{\"decision\":\"MERGE\",\"winner_id\":\"%s\",\"confidence\":0.9,\"reasoning\":\"doublon\"}".formatted(leafA.getId());
        when(localLlmService.generateComplexAsync(anyString()))
                .thenReturn(CompletableFuture.completedFuture(mergeJson));

        // When
        ConsolidationResult result = service.runConsolidation();

        // Then
        assertFalse(result.rolledBack());
        assertEquals(1, result.totalScanned());
        assertEquals(1, result.duplicatesMerged());
        assertEquals(0, result.parseErrors());
        verify(persistenceService).createSnapshot();
        verify(persistenceService).doSave();
    }

    @Test
    void rollbackOnParseErrors_shouldRestoreSnapshot() throws Exception {
        // Given — multiple conflict pairs to trigger multiple LLM calls
        properties.getConsolidation().setMaxParseErrors(2);

        ObservationLeaf leafA1 = new ObservationLeaf("A1", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED);
        ObservationLeaf leafB1 = new ObservationLeaf("B1", TreeBranch.HABITUDES, ObservationSource.HEURISTIC);
        leafA1.setConflictsWith(leafB1.getId());
        leafA1.setNeedsConsolidation(true);

        ObservationLeaf leafA2 = new ObservationLeaf("A2", TreeBranch.INTERETS, ObservationSource.LLM_EXTRACTED);
        ObservationLeaf leafB2 = new ObservationLeaf("B2", TreeBranch.EMOTIONS, ObservationSource.HEURISTIC);
        leafA2.setConflictsWith(leafB2.getId());
        leafA2.setNeedsConsolidation(true);

        ObservationLeaf leafA3 = new ObservationLeaf("A3", TreeBranch.OBJECTIFS, ObservationSource.LLM_EXTRACTED);
        ObservationLeaf leafB3 = new ObservationLeaf("B3", TreeBranch.COMMUNICATION, ObservationSource.HEURISTIC);
        leafA3.setConflictsWith(leafB3.getId());
        leafA3.setNeedsConsolidation(true);

        tree.addLeaf(leafA1); tree.addLeaf(leafB1);
        tree.addLeaf(leafA2); tree.addLeaf(leafB2);
        tree.addLeaf(leafA3); tree.addLeaf(leafB3);

        // Make LLM return garbage repeatedly
        when(localLlmService.generateComplexAsync(anyString()))
                .thenReturn(CompletableFuture.completedFuture("not json at all"));

        // When
        ConsolidationResult result = service.runConsolidation();

        // Then
        assertTrue(result.rolledBack());
        assertTrue(result.parseErrors() >= 2);
        verify(persistenceService).restoreSnapshot(any());
    }

    @Test
    void rollbackOnLeafCountDrop_aboveFloor_shouldTriggerOnPercentage() throws Exception {
        // Given — 25 leaves (above the 20-leaf floor), remove more than 10%
        for (int i = 0; i < 25; i++) {
            ObservationLeaf leaf = new ObservationLeaf("Leaf " + i, TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED);
            tree.addLeaf(leaf);
        }

        // Mark first 5 leaves with conflicts to trigger processing
        var leaves = tree.getAllActiveLeaves();
        for (int i = 0; i < 5; i++) {
            leaves.get(i).setConflictsWith(leaves.get(i + 5).getId());
            leaves.get(i).setNeedsConsolidation(true);
        }

        // LLM always says ARCHIVE (remove the loser)
        when(localLlmService.generateComplexAsync(anyString()))
                .thenAnswer(inv -> CompletableFuture.completedFuture(
                        "{\"decision\":\"ARCHIVE\",\"confidence\":0.9,\"reasoning\":\"obsolete\"}"));

        // When
        ConsolidationResult result = service.runConsolidation();

        // Then — should rollback after removing >10% of 25 leaves (>2.5)
        assertTrue(result.rolledBack());
        verify(persistenceService).restoreSnapshot(any());
    }

    @Test
    void noRollbackBelowFloor_withinAbsoluteLimit() throws Exception {
        // Given — 10 leaves (below the 20-leaf floor)
        ObservationLeaf leafA = new ObservationLeaf("A", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED);
        ObservationLeaf leafB = new ObservationLeaf("B", TreeBranch.HABITUDES, ObservationSource.LLM_EXTRACTED);
        leafA.setConflictsWith(leafB.getId());
        leafA.setNeedsConsolidation(true);
        tree.addLeaf(leafA);
        tree.addLeaf(leafB);

        // LLM says ARCHIVE
        String archiveJson = "{\"decision\":\"ARCHIVE\",\"confidence\":0.9,\"reasoning\":\"duplicate\"}";
        when(localLlmService.generateComplexAsync(anyString()))
                .thenReturn(CompletableFuture.completedFuture(archiveJson));

        // When
        ConsolidationResult result = service.runConsolidation();

        // Then — 1 removal from 2 leaves is within absolute limit (3), no rollback
        assertFalse(result.rolledBack());
        assertEquals(1, result.leavesArchived());
    }

    @Test
    void skipWhenLlmUnavailable() throws IOException {
        // Given
        when(localLlmService.isAvailable()).thenReturn(false);

        // When
        ConsolidationResult result = service.runConsolidation();

        // Then
        assertEquals(0, result.totalScanned());
        verify(persistenceService, never()).createSnapshot();
    }

    @Test
    void skipWhenAlreadyRunning() throws Exception {
        // Given — simulate already running via concurrent call
        // We use a latch-based approach: make the first call block
        float[] embedding = new float[]{1.0f};
        ObservationLeaf leaf = new ObservationLeaf("Test", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED, embedding);
        leaf.setNeedsConsolidation(true);
        tree.addLeaf(leaf);
        ObservationLeaf similar = new ObservationLeaf("Similar", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED, embedding);
        tree.addLeaf(similar);

        when(embeddingService.cosineSimilarity(any(), any())).thenReturn(0.8);

        var latch = new java.util.concurrent.CountDownLatch(1);
        when(localLlmService.generateComplexAsync(anyString())).thenAnswer(inv -> {
            CompletableFuture<String> f = new CompletableFuture<>();
            new Thread(() -> {
                try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                f.complete("{\"decision\":\"KEEP_BOTH\",\"confidence\":0.9}");
            }).start();
            return f;
        });

        // Start first call in background
        var firstFuture = CompletableFuture.supplyAsync(() -> service.runConsolidation());
        Thread.sleep(200); // let it start

        // When — second call while first is running
        ConsolidationResult result = service.runConsolidation();

        // Then — second call was skipped
        assertEquals(0, result.totalScanned());

        // Cleanup
        latch.countDown();
        firstFuture.get(5, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Test
    void timeoutHandling_shouldCountTimeoutsAndAbort() throws Exception {
        // Given
        float[] embedding = new float[]{1.0f};
        for (int i = 0; i < 5; i++) {
            ObservationLeaf leaf = new ObservationLeaf("Leaf " + i, TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED, embedding);
            leaf.setNeedsConsolidation(true);
            tree.addLeaf(leaf);
        }
        when(embeddingService.cosineSimilarity(any(), any())).thenReturn(0.8);

        properties.getConsolidation().setMaxTimeoutsPerRun(2);
        when(localLlmService.generateComplexAsync(anyString()))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("timeout")));

        // When
        ConsolidationResult result = service.runConsolidation();

        // Then
        assertTrue(result.timeouts() >= 2);
    }

    @Test
    void conflictsPairsProcessedBeforeNeedsConsolidation() throws Exception {
        // Given — a conflict pair and a needsConsolidation leaf
        ObservationLeaf conflictLeaf = new ObservationLeaf("Conflict", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED);
        ObservationLeaf conflictTarget = new ObservationLeaf("Target", TreeBranch.HABITUDES, ObservationSource.HEURISTIC);
        conflictLeaf.setConflictsWith(conflictTarget.getId());
        conflictLeaf.setNeedsConsolidation(true);

        float[] embedding = new float[]{1.0f};
        ObservationLeaf consolidateLeaf = new ObservationLeaf("Consolidate", TreeBranch.INTERETS, ObservationSource.LLM_EXTRACTED, embedding);
        consolidateLeaf.setNeedsConsolidation(true);
        ObservationLeaf similarLeaf = new ObservationLeaf("Similar consolidate", TreeBranch.INTERETS, ObservationSource.LLM_EXTRACTED, embedding);
        tree.addLeaf(similarLeaf);

        tree.addLeaf(conflictLeaf);
        tree.addLeaf(conflictTarget);
        tree.addLeaf(consolidateLeaf);

        when(embeddingService.cosineSimilarity(any(), any())).thenReturn(0.8);

        // Track order of LLM calls — conflict prompt mentions both branches, consolidation mentions only one
        var prompts = new java.util.ArrayList<String>();
        when(localLlmService.generateComplexAsync(anyString())).thenAnswer(inv -> {
            prompts.add(inv.getArgument(0));
            return CompletableFuture.completedFuture("{\"decision\":\"KEEP_BOTH\",\"confidence\":0.9,\"reasoning\":\"ok\"}");
        });

        // When
        service.runConsolidation();

        // Then — first prompt should be about cross-branch conflict (mentions both IDENTITE and HABITUDES)
        assertFalse(prompts.isEmpty());
        String firstPrompt = prompts.get(0);
        assertTrue(firstPrompt.contains("IDENTITE") && firstPrompt.contains("HABITUDES"),
                "First processed item should be the cross-branch conflict pair");
    }
}
