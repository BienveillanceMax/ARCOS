package org.arcos.UnitTests.UserModel;

import org.arcos.UserModel.Lifecycle.EbbinghausPruningService;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class EbbinghausPruningServiceTest {

    private UserObservationTree tree;
    private EbbinghausPruningService pruningService;

    @Mock
    private UserTreePersistenceService persistenceService;
    @Mock
    private BranchSummaryBuilder summaryBuilder;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        UserModelProperties props = new UserModelProperties();
        tree = new UserObservationTree(props);
        pruningService = new EbbinghausPruningService(tree, persistenceService, summaryBuilder);
    }

    @Test
    void prune_LowRetention_ShouldArchiveAndRemove() {
        ObservationLeaf old = new ObservationLeaf("Old observation", TreeBranch.INTERETS, ObservationSource.LLM_EXTRACTED);
        old.setLastReinforced(Instant.now().minus(365, ChronoUnit.DAYS));
        old.setObservationCount(1);
        tree.addLeaf(old);

        ObservationLeaf recent = new ObservationLeaf("Recent observation", TreeBranch.INTERETS, ObservationSource.LLM_EXTRACTED);
        recent.setLastReinforced(Instant.now());
        recent.setObservationCount(5);
        tree.addLeaf(recent);

        pruningService.prune();

        assertEquals(1, tree.getAllActiveLeaves().size());
        assertEquals("Recent observation", tree.getAllActiveLeaves().get(0).getText());
        verify(persistenceService).archiveLeaf(any(), anyString());
        verify(summaryBuilder).rebuild(TreeBranch.INTERETS);
        verify(persistenceService).scheduleSave();
    }

    @Test
    void prune_AllFresh_ShouldPruneNothing() {
        ObservationLeaf fresh1 = new ObservationLeaf("Fresh 1", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED);
        fresh1.setLastReinforced(Instant.now());
        fresh1.setObservationCount(3);
        tree.addLeaf(fresh1);

        ObservationLeaf fresh2 = new ObservationLeaf("Fresh 2", TreeBranch.COMMUNICATION, ObservationSource.HEURISTIC);
        fresh2.setLastReinforced(Instant.now());
        fresh2.setObservationCount(2);
        tree.addLeaf(fresh2);

        pruningService.prune();

        assertEquals(2, tree.getAllActiveLeaves().size());
        verify(persistenceService, never()).archiveLeaf(any(), anyString());
        verify(persistenceService, never()).scheduleSave();
    }

    @Test
    void prune_ShouldRebuildSummariesOnlyForModifiedBranches() {
        ObservationLeaf old1 = new ObservationLeaf("Old INTERETS", TreeBranch.INTERETS, ObservationSource.LLM_EXTRACTED);
        old1.setLastReinforced(Instant.now().minus(500, ChronoUnit.DAYS));
        old1.setObservationCount(1);
        tree.addLeaf(old1);

        ObservationLeaf old2 = new ObservationLeaf("Old HABITUDES", TreeBranch.HABITUDES, ObservationSource.HEURISTIC);
        old2.setLastReinforced(Instant.now().minus(500, ChronoUnit.DAYS));
        old2.setObservationCount(1);
        tree.addLeaf(old2);

        ObservationLeaf fresh = new ObservationLeaf("Fresh IDENTITE", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED);
        fresh.setLastReinforced(Instant.now());
        fresh.setObservationCount(10);
        tree.addLeaf(fresh);

        pruningService.prune();

        verify(summaryBuilder).rebuild(TreeBranch.INTERETS);
        verify(summaryBuilder).rebuild(TreeBranch.HABITUDES);
        verify(summaryBuilder, never()).rebuild(TreeBranch.IDENTITE);
    }

    @Test
    void computeRetention_HighStabilityRecentLeaf_ShouldBeHigh() {
        ObservationLeaf leaf = new ObservationLeaf("test", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED);
        leaf.setLastReinforced(Instant.now());
        leaf.setObservationCount(10);

        double retention = pruningService.computeRetention(leaf);

        assertTrue(retention > 0.9, "Recent leaf with high stability should have high retention, got " + retention);
    }

    @Test
    void computeRetention_LowStabilityOldLeaf_ShouldBeLow() {
        ObservationLeaf leaf = new ObservationLeaf("test", TreeBranch.INTERETS, ObservationSource.LLM_EXTRACTED);
        leaf.setLastReinforced(Instant.now().minus(180, ChronoUnit.DAYS));
        leaf.setObservationCount(1);

        double retention = pruningService.computeRetention(leaf);

        assertTrue(retention < 0.05, "Old leaf with low stability should have very low retention, got " + retention);
    }
}
