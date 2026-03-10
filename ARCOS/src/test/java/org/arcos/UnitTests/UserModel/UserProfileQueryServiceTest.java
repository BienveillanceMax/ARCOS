package org.arcos.UnitTests.UserModel;

import org.arcos.UserModel.Embedding.LocalEmbeddingService;
import org.arcos.UserModel.Models.ObservationLeaf;
import org.arcos.UserModel.Models.ObservationSource;
import org.arcos.UserModel.Models.ProfileStability;
import org.arcos.UserModel.Models.TreeBranch;
import org.arcos.UserModel.Retrieval.UserProfileQueryService;
import org.arcos.UserModel.UserModelProperties;
import org.arcos.UserModel.UserObservationTree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class UserProfileQueryServiceTest {

    private UserObservationTree tree;
    private UserProfileQueryService queryService;

    @Mock
    private LocalEmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        UserModelProperties properties = new UserModelProperties();
        tree = new UserObservationTree(properties);
        queryService = new UserProfileQueryService(tree, embeddingService);
    }

    // ==================== getTopLeaves ====================

    @Test
    void getTopLeaves_returnsSortedByObservationCount() {
        // Given
        ObservationLeaf leaf1 = new ObservationLeaf("Aime le café", TreeBranch.HABITUDES, ObservationSource.LLM_EXTRACTED);
        leaf1.setObservationCount(2);
        ObservationLeaf leaf2 = new ObservationLeaf("Fait du sport le matin", TreeBranch.HABITUDES, ObservationSource.LLM_EXTRACTED);
        leaf2.setObservationCount(10);
        ObservationLeaf leaf3 = new ObservationLeaf("Cuisine souvent", TreeBranch.HABITUDES, ObservationSource.LLM_EXTRACTED);
        leaf3.setObservationCount(5);
        tree.addLeaf(leaf1);
        tree.addLeaf(leaf2);
        tree.addLeaf(leaf3);

        // When
        List<ObservationLeaf> result = queryService.getTopLeaves(TreeBranch.HABITUDES, 10);

        // Then
        assertEquals(3, result.size());
        assertEquals(10, result.get(0).getObservationCount());
        assertEquals(5, result.get(1).getObservationCount());
        assertEquals(2, result.get(2).getObservationCount());
    }

    @Test
    void getTopLeaves_respectsLimit() {
        // Given
        for (int i = 0; i < 5; i++) {
            ObservationLeaf leaf = new ObservationLeaf("Observation " + i, TreeBranch.INTERETS, ObservationSource.LLM_EXTRACTED);
            leaf.setObservationCount(i + 1);
            tree.addLeaf(leaf);
        }

        // When
        List<ObservationLeaf> result = queryService.getTopLeaves(TreeBranch.INTERETS, 3);

        // Then
        assertEquals(3, result.size());
        assertEquals(5, result.get(0).getObservationCount());
        assertEquals(4, result.get(1).getObservationCount());
        assertEquals(3, result.get(2).getObservationCount());
    }

    @Test
    void getTopLeaves_returnsEmptyForEmptyBranch() {
        // Given — no leaves added

        // When
        List<ObservationLeaf> result = queryService.getTopLeaves(TreeBranch.OBJECTIFS, 5);

        // Then
        assertTrue(result.isEmpty());
    }

    // ==================== searchLeaves ====================

    @Test
    void searchLeaves_returnsMatchesAboveMinScore() {
        // Given
        when(embeddingService.isReady()).thenReturn(true);
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f, 0.3f});

        ObservationLeaf highRelevance = new ObservationLeaf(
                "Passionné d'intelligence artificielle", TreeBranch.INTERETS,
                ObservationSource.LLM_EXTRACTED, new float[]{0.1f, 0.2f, 0.3f});
        highRelevance.setEmotionalImportance(0.8f);
        highRelevance.setObservationCount(5);
        tree.addLeaf(highRelevance);

        ObservationLeaf lowRelevance = new ObservationLeaf(
                "Aime les pâtes", TreeBranch.HABITUDES,
                ObservationSource.LLM_EXTRACTED, new float[]{0.9f, 0.8f, 0.7f});
        lowRelevance.setEmotionalImportance(0.1f);
        lowRelevance.setObservationCount(1);
        tree.addLeaf(lowRelevance);

        when(embeddingService.cosineSimilarity(any(), eq(highRelevance.getEmbedding()))).thenReturn(0.9);
        when(embeddingService.cosineSimilarity(any(), eq(lowRelevance.getEmbedding()))).thenReturn(0.05);

        // When
        List<ObservationLeaf> result = queryService.searchLeaves("intelligence artificielle", 5, 0.5);

        // Then
        assertEquals(1, result.size());
        assertEquals("Passionné d'intelligence artificielle", result.get(0).getText());
    }

    @Test
    void searchLeaves_returnsEmptyWhenEmbeddingNotReady() {
        // Given
        when(embeddingService.isReady()).thenReturn(false);
        tree.addLeaf(new ObservationLeaf("Test", TreeBranch.INTERETS, ObservationSource.LLM_EXTRACTED));

        // When
        List<ObservationLeaf> result = queryService.searchLeaves("query", 5, 0.3);

        // Then
        assertTrue(result.isEmpty());
        verify(embeddingService, never()).embed(anyString());
    }

    @Test
    void searchLeaves_searchesAllBranches() {
        // Given
        when(embeddingService.isReady()).thenReturn(true);
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
        when(embeddingService.cosineSimilarity(any(), any())).thenReturn(0.8);

        ObservationLeaf identityLeaf = new ObservationLeaf(
                "S'appelle Pierre", TreeBranch.IDENTITE,
                ObservationSource.LLM_EXTRACTED, new float[]{0.1f, 0.2f, 0.3f});
        identityLeaf.setObservationCount(3);
        tree.addLeaf(identityLeaf);

        ObservationLeaf interestLeaf = new ObservationLeaf(
                "Aime la musique", TreeBranch.INTERETS,
                ObservationSource.LLM_EXTRACTED, new float[]{0.1f, 0.2f, 0.3f});
        interestLeaf.setObservationCount(3);
        tree.addLeaf(interestLeaf);

        ObservationLeaf goalLeaf = new ObservationLeaf(
                "Veut apprendre le piano", TreeBranch.OBJECTIFS,
                ObservationSource.LLM_EXTRACTED, new float[]{0.1f, 0.2f, 0.3f});
        goalLeaf.setObservationCount(3);
        tree.addLeaf(goalLeaf);

        // When
        List<ObservationLeaf> result = queryService.searchLeaves("musique", 10, 0.3);

        // Then — all 3 branches should be searched
        assertEquals(3, result.size());
    }

    // ==================== getBranchSummary ====================

    @Test
    void getBranchSummary_returnsSummaryWhenPresent() {
        // Given
        tree.setSummary(TreeBranch.IDENTITE, "Pierre, développeur Java à Lyon");

        // When
        Optional<String> result = queryService.getBranchSummary(TreeBranch.IDENTITE);

        // Then
        assertTrue(result.isPresent());
        assertEquals("Pierre, développeur Java à Lyon", result.get());
    }

    @Test
    void getBranchSummary_returnsEmptyWhenAbsent() {
        // Given — no summary set

        // When
        Optional<String> result = queryService.getBranchSummary(TreeBranch.EMOTIONS);

        // Then
        assertTrue(result.isEmpty());
    }

    // ==================== getProfileStability ====================

    @Test
    void getProfileStability_delegatesToTree() {
        // Given
        tree.setConversationCount(12);

        // When
        ProfileStability stability = queryService.getProfileStability();

        // Then
        assertEquals(ProfileStability.HIGH, stability);
    }
}
