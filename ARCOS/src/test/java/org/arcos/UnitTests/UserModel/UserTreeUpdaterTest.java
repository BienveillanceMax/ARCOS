package org.arcos.UnitTests.UserModel;

import org.arcos.UserModel.Embedding.LocalEmbeddingService;
import org.arcos.UserModel.Extraction.UserTreeUpdater;
import org.arcos.UserModel.Extraction.UserTreeUpdater.UpdateResult;
import org.arcos.UserModel.Models.*;
import org.arcos.UserModel.Persistence.UserTreePersistenceService;
import org.arcos.UserModel.Retrieval.BranchSummaryBuilder;
import org.arcos.UserModel.UserModelProperties;
import org.arcos.UserModel.UserObservationTree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class UserTreeUpdaterTest {

    private UserObservationTree tree;
    private UserTreeUpdater updater;

    @Mock
    private LocalEmbeddingService embeddingService;
    @Mock
    private BranchSummaryBuilder summaryBuilder;
    @Mock
    private UserTreePersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        UserModelProperties props = new UserModelProperties();
        tree = new UserObservationTree(props);
        updater = new UserTreeUpdater(tree, embeddingService, summaryBuilder, persistenceService);

        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
    }

    @Test
    void processObservation_NewObservation_ShouldADD() {
        ObservationCandidate candidate = new ObservationCandidate(
                "Mon créateur s'appelle Pierre", TreeBranch.IDENTITE, null);

        UpdateResult result = updater.processObservation(candidate);

        assertEquals(UpdateResult.ADD, result);
        assertEquals(1, tree.getActiveLeaves(TreeBranch.IDENTITE).size());
        verify(summaryBuilder).rebuild(TreeBranch.IDENTITE);
        verify(persistenceService).scheduleSave();
    }

    @Test
    void processObservation_HighSimilarity_ShouldUPDATE() {
        ObservationLeaf existing = new ObservationLeaf(
                "Mon créateur s'appelle Pierre", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED,
                new float[]{0.1f, 0.2f, 0.3f});
        existing.setObservationCount(1);
        tree.addLeaf(existing);

        when(embeddingService.cosineSimilarity(any(), any())).thenReturn(0.95);

        ObservationCandidate candidate = new ObservationCandidate(
                "Mon créateur se nomme Pierre", TreeBranch.IDENTITE, null);

        UpdateResult result = updater.processObservation(candidate);

        assertEquals(UpdateResult.UPDATE, result);
        assertEquals(1, tree.getActiveLeaves(TreeBranch.IDENTITE).size());
        assertEquals(2, tree.getActiveLeaves(TreeBranch.IDENTITE).get(0).getObservationCount());
    }

    @Test
    void processObservation_MediumSimilarity_ShouldBeAMBIGUOUS() {
        ObservationLeaf existing = new ObservationLeaf(
                "Mon créateur aime le café", TreeBranch.HABITUDES, ObservationSource.LLM_EXTRACTED,
                new float[]{0.1f, 0.2f, 0.3f});
        tree.addLeaf(existing);

        when(embeddingService.cosineSimilarity(any(), any())).thenReturn(0.7);

        ObservationCandidate candidate = new ObservationCandidate(
                "Mon créateur boit du thé le matin", TreeBranch.HABITUDES, null);

        UpdateResult result = updater.processObservation(candidate);

        assertEquals(UpdateResult.AMBIGUOUS, result);
        assertEquals(2, tree.getActiveLeaves(TreeBranch.HABITUDES).size());
        assertTrue(tree.getActiveLeaves(TreeBranch.HABITUDES).stream()
                .allMatch(ObservationLeaf::isNeedsConsolidation));
    }

    @Test
    void processObservation_CrossBranch_ShouldSetConflictsWith() {
        ObservationLeaf existing = new ObservationLeaf(
                "Mon créateur est curieux", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED,
                new float[]{0.1f, 0.2f, 0.3f});
        tree.addLeaf(existing);

        when(embeddingService.cosineSimilarity(any(), any())).thenReturn(0.7);

        ObservationCandidate candidate = new ObservationCandidate(
                "Mon créateur est curieux de nature", TreeBranch.EMOTIONS, null);

        UpdateResult result = updater.processObservation(candidate);

        assertEquals(UpdateResult.AMBIGUOUS, result);
        ObservationLeaf newLeaf = tree.getActiveLeaves(TreeBranch.EMOTIONS).get(0);
        assertNotNull(newLeaf.getConflictsWith());
        assertEquals(existing.getId(), newLeaf.getConflictsWith());
    }

    @Test
    void processObservation_ExplicitDeclaration_ShouldSetHighCountAndImportance() {
        ObservationCandidate candidate = new ObservationCandidate(
                "Mon créateur est développeur Java", TreeBranch.IDENTITE, null, true, 0.8f);

        UpdateResult result = updater.processObservation(candidate);

        assertEquals(UpdateResult.ADD, result);
        ObservationLeaf added = tree.getActiveLeaves(TreeBranch.IDENTITE).get(0);
        assertEquals(3, added.getObservationCount());
        assertEquals(0.8f, added.getEmotionalImportance(), 0.01f);
        assertEquals(ObservationSource.USER_EXPLICIT, added.getSource());
    }

    @Test
    void processObservation_WithReplacement_ShouldRemoveOldAndAddNew() {
        ObservationLeaf existing = new ObservationLeaf(
                "Mon créateur vit à Paris", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED);
        tree.addLeaf(existing);

        ObservationCandidate candidate = new ObservationCandidate(
                "Mon créateur vit à Lyon", TreeBranch.IDENTITE, "Mon créateur vit à Paris");

        UpdateResult result = updater.processObservation(candidate);

        assertEquals(UpdateResult.ADD, result);
        assertEquals(1, tree.getActiveLeaves(TreeBranch.IDENTITE).size());
        assertEquals("Mon créateur vit à Lyon", tree.getActiveLeaves(TreeBranch.IDENTITE).get(0).getText());
        verify(persistenceService).archiveLeaf(any(), contains("Replaced by"));
    }

    @Test
    void processObservation_NullCandidate_ShouldReturnAdd() {
        UpdateResult result = updater.processObservation(null);
        assertEquals(UpdateResult.ADD, result);
        assertTrue(tree.getAllActiveLeaves().isEmpty());
    }

    @Test
    void processObservation_BlankText_ShouldReturnAdd() {
        ObservationCandidate candidate = new ObservationCandidate("  ", TreeBranch.IDENTITE, null);
        UpdateResult result = updater.processObservation(candidate);
        assertEquals(UpdateResult.ADD, result);
        assertTrue(tree.getAllActiveLeaves().isEmpty());
    }
}
