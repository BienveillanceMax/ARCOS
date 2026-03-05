package org.arcos.UnitTests.UserModel;

import org.arcos.UserModel.Embedding.LocalEmbeddingService;
import org.arcos.UserModel.Models.ObservationLeaf;
import org.arcos.UserModel.Models.ObservationSource;
import org.arcos.UserModel.Models.TreeBranch;
import org.arcos.UserModel.Models.UserProfileContext;
import org.arcos.UserModel.Retrieval.UserModelRetrievalService;
import org.arcos.UserModel.UserModelProperties;
import org.arcos.UserModel.UserObservationTree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class UserModelRetrievalServiceTest {

    private UserObservationTree tree;
    private UserModelRetrievalService retrievalService;
    private UserModelProperties properties;

    @Mock
    private LocalEmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        properties = new UserModelProperties();
        properties.setMinConversationsBeforeInjection(3);
        properties.setTotalBudgetTokens(80);
        properties.setRetrievalMinSrank(0.3);
        tree = new UserObservationTree(properties);
        retrievalService = new UserModelRetrievalService(tree, embeddingService, properties);
    }

    @Test
    void retrieveUserContext_ColdStartGate_ShouldReturnEmptyWhenLessThan3Conversations() {
        tree.setConversationCount(2);

        UserProfileContext context = retrievalService.retrieveUserContext("Bonjour");

        assertTrue(context.isEmpty());
        assertEquals(2, context.conversationCount());
        verifyNoInteractions(embeddingService);
    }

    @Test
    void retrieveUserContext_ShouldReturnIdentityAndCommunicationSummaries() {
        tree.setConversationCount(5);
        tree.setSummary(TreeBranch.IDENTITE, "Pierre, développeur Java");
        tree.setSummary(TreeBranch.COMMUNICATION, "S'exprime de manière concise");

        when(embeddingService.isReady()).thenReturn(false);

        UserProfileContext context = retrievalService.retrieveUserContext("test query");

        assertEquals("Pierre, développeur Java", context.identitySummary());
        assertEquals("S'exprime de manière concise", context.communicationSummary());
        assertFalse(context.isEmpty());
    }

    @Test
    void retrieveUserContext_BudgetShouldNotExceed80Tokens() {
        tree.setConversationCount(10);
        tree.setSummary(TreeBranch.IDENTITE, "Pierre développeur Java senior travaillant à Lyon sur des projets Spring Boot");
        tree.setSummary(TreeBranch.COMMUNICATION, "S'exprime de manière concise et directe avec un vocabulaire technique");

        when(embeddingService.isReady()).thenReturn(true);
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f, 0.3f});

        ObservationLeaf onDemandLeaf = new ObservationLeaf(
                "Mon créateur s'intéresse beaucoup à l'intelligence artificielle et aux systèmes autonomes avancés avec des capacités de raisonnement étendues",
                TreeBranch.INTERETS, ObservationSource.LLM_EXTRACTED, new float[]{0.1f, 0.2f, 0.3f});
        onDemandLeaf.setEmotionalImportance(0.9f);
        onDemandLeaf.setObservationCount(5);
        tree.addLeaf(onDemandLeaf);

        when(embeddingService.cosineSimilarity(any(), any())).thenReturn(0.8);

        UserProfileContext context = retrievalService.retrieveUserContext("parle-moi d'IA");

        int totalTokens = estimateTokens(context.identitySummary())
                + estimateTokens(context.communicationSummary())
                + estimateTokens(context.onDemandLeafText());

        assertTrue(totalTokens <= 80, "Total tokens should be ≤80, got " + totalTokens);
    }

    @Test
    void retrieveUserContext_SrankFilter_ShouldExcludeLowRelevanceLeaves() {
        tree.setConversationCount(5);

        when(embeddingService.isReady()).thenReturn(true);
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f, 0.3f});

        ObservationLeaf leaf = new ObservationLeaf(
                "Mon créateur aime le café", TreeBranch.HABITUDES, ObservationSource.LLM_EXTRACTED,
                new float[]{0.9f, 0.8f, 0.7f});
        leaf.setEmotionalImportance(0.1f);
        leaf.setObservationCount(1);
        tree.addLeaf(leaf);

        when(embeddingService.cosineSimilarity(any(), any())).thenReturn(0.05);

        UserProfileContext context = retrievalService.retrieveUserContext("astronomie");

        assertNull(context.onDemandLeafText());
    }

    @Test
    void retrieveUserContext_ShouldReinforceSelectedLeaf() {
        tree.setConversationCount(5);

        when(embeddingService.isReady()).thenReturn(true);
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f, 0.3f});

        ObservationLeaf leaf = new ObservationLeaf(
                "Mon créateur adore la musique jazz", TreeBranch.INTERETS, ObservationSource.LLM_EXTRACTED,
                new float[]{0.1f, 0.2f, 0.3f});
        leaf.setEmotionalImportance(0.7f);
        leaf.setObservationCount(2);
        tree.addLeaf(leaf);

        when(embeddingService.cosineSimilarity(any(), any())).thenReturn(0.8);

        retrievalService.retrieveUserContext("musique");

        ObservationLeaf updatedLeaf = tree.getActiveLeaves(TreeBranch.INTERETS).get(0);
        assertEquals(3, updatedLeaf.getObservationCount());
    }

    @Test
    void retrieveUserContext_EmbeddingNotReady_ShouldReturnSummariesOnly() {
        tree.setConversationCount(5);
        tree.setSummary(TreeBranch.IDENTITE, "Pierre");

        when(embeddingService.isReady()).thenReturn(false);

        UserProfileContext context = retrievalService.retrieveUserContext("test");

        assertEquals("Pierre", context.identitySummary());
        assertNull(context.onDemandLeafText());
        verify(embeddingService, never()).embed(anyString());
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        return (int) Math.ceil(text.split("\\s+").length * 1.3);
    }
}
