package org.arcos.IntegrationTests.UserModel;

import org.arcos.UserModel.Embedding.LocalEmbeddingService;
import org.arcos.UserModel.Engagement.EngagementTracker;
import org.arcos.UserModel.Extraction.UserTreeUpdater;
import org.arcos.UserModel.GapFilling.GapDetector;
import org.arcos.UserModel.GapFilling.ProactiveGapFiller;
import org.arcos.UserModel.Greeting.PersonalizedGreetingService;
import org.arcos.UserModel.Heuristics.EmaBaselineManager;
import org.arcos.UserModel.Heuristics.HeuristicSignalExtractor;
import org.arcos.UserModel.Heuristics.HeuristicTextTemplates;
import org.arcos.UserModel.Lifecycle.UserModelPipelineOrchestrator;
import org.arcos.UserModel.Models.*;
import org.arcos.UserModel.Persistence.UserTreePersistenceService;
import org.arcos.UserModel.Retrieval.BranchSummaryBuilder;
import org.arcos.UserModel.Retrieval.UserModelRetrievalService;
import org.arcos.UserModel.UserModelProperties;
import org.arcos.UserModel.UserObservationTree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class UserModelPipelineIT {

    @TempDir
    Path tempDir;

    private UserObservationTree tree;
    private UserModelProperties properties;
    private UserTreePersistenceService persistenceService;
    private BranchSummaryBuilder summaryBuilder;
    private UserTreeUpdater treeUpdater;
    private UserModelPipelineOrchestrator pipeline;
    private UserModelRetrievalService retrievalService;

    @Mock
    private LocalEmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        properties = new UserModelProperties();
        properties.setStoragePath(tempDir.resolve("user-tree.json").toString());
        properties.setArchivePath(tempDir.resolve("archive.json").toString());
        properties.setDebounceSaveMs(50);

        tree = new UserObservationTree(properties);
        persistenceService = new UserTreePersistenceService(tree, properties);
        summaryBuilder = new BranchSummaryBuilder(tree, properties);
        treeUpdater = new UserTreeUpdater(tree, embeddingService, summaryBuilder, persistenceService);
        EngagementTracker engagementTracker = new EngagementTracker(tree, properties);
        pipeline = new UserModelPipelineOrchestrator(tree, treeUpdater, persistenceService, properties, new HeuristicTextTemplates(), engagementTracker);
        GapDetector gapDetector = new GapDetector(tree);
        ProactiveGapFiller gapFiller = new ProactiveGapFiller(gapDetector, tree, properties);
        PersonalizedGreetingService greetingService = new PersonalizedGreetingService(tree);
        retrievalService = new UserModelRetrievalService(tree, embeddingService, properties, gapFiller, engagementTracker, greetingService);

        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
        when(embeddingService.isReady()).thenReturn(true);
        when(embeddingService.cosineSimilarity(any(), any())).thenReturn(0.1);
    }

    @Test
    void fullCycle_ChannelA_ShouldExtractSignalsAndUpdateTree() {
        // Simulate 6 conversations to get past cold-start
        for (int i = 0; i < 6; i++) {
            pipeline.processConversation(
                    List.of("Bonjour comment vas-tu ?",
                            "J'aimerais savoir quelque chose",
                            "Merci beaucoup"),
                    false);
        }

        assertEquals(6, tree.getConversationCount());
        assertEquals(ProfileStability.MEDIUM, tree.getProfileStability());
        assertFalse(tree.getHeuristicBaselines().isEmpty());
    }

    @Test
    void fullCycle_ChannelB_ShouldProcessObservationsAndBuildSummary() {
        tree.setConversationCount(5);

        ObservationCandidate candidate1 = new ObservationCandidate(
                "Mon créateur s'appelle Pierre", TreeBranch.IDENTITE, null, true, 0.8f);
        ObservationCandidate candidate2 = new ObservationCandidate(
                "Mon créateur est développeur Java", TreeBranch.IDENTITE, null, false, 0.5f);

        treeUpdater.processObservation(candidate1);
        treeUpdater.processObservation(candidate2);

        assertEquals(2, tree.getActiveLeaves(TreeBranch.IDENTITE).size());
        assertNotNull(tree.getSummary(TreeBranch.IDENTITE));

        // Verify explicit declaration got special treatment
        ObservationLeaf pierreLeaf = tree.getActiveLeaves(TreeBranch.IDENTITE).stream()
                .filter(l -> l.getText().contains("Pierre"))
                .findFirst().orElse(null);
        assertNotNull(pierreLeaf);
        assertEquals(3, pierreLeaf.getObservationCount());
        assertEquals(0.8f, pierreLeaf.getEmotionalImportance(), 0.01f);
    }

    @Test
    void fullCycle_Retrieval_ShouldReturnContextAfterMinConversations() {
        tree.setConversationCount(5);
        tree.setSummary(TreeBranch.IDENTITE, "Pierre, développeur Java senior");
        tree.setSummary(TreeBranch.COMMUNICATION, "S'exprime de manière concise");

        UserProfileContext context = retrievalService.retrieveUserContext("Bonjour");

        assertFalse(context.isEmpty());
        assertEquals("Pierre, développeur Java senior", context.identitySummary());
        assertEquals("S'exprime de manière concise", context.communicationSummary());
        assertEquals(5, context.conversationCount());
    }

    @Test
    void fullCycle_Retrieval_ShouldBlockOnColdStart() {
        tree.setConversationCount(1);

        UserProfileContext context = retrievalService.retrieveUserContext("Bonjour");

        assertTrue(context.isEmpty());
        assertEquals(1, context.conversationCount());
    }

    @Test
    void fullCycle_PersistenceRoundtrip_ShouldPreserveState() throws Exception {
        tree.setConversationCount(7);
        tree.addLeaf(new ObservationLeaf("Mon créateur s'appelle Pierre", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED));
        tree.addLeaf(new ObservationLeaf("Mon créateur est concis", TreeBranch.COMMUNICATION, ObservationSource.HEURISTIC));
        tree.setSummary(TreeBranch.IDENTITE, "Pierre");
        tree.setHeuristicBaselines(Map.of("avg_word_count", 12.5));

        persistenceService.doSave();

        // Create fresh tree and service, load from file
        UserObservationTree freshTree = new UserObservationTree(properties);
        UserTreePersistenceService freshPersistence = new UserTreePersistenceService(freshTree, properties);
        freshPersistence.load();

        assertEquals(7, freshTree.getConversationCount());
        assertEquals(2, freshTree.getAllActiveLeaves().size());
        assertEquals("Pierre", freshTree.getSummary(TreeBranch.IDENTITE));
        assertEquals(12.5, freshTree.getHeuristicBaselines().get("avg_word_count"));
    }

    @Test
    void fullCycle_ContradictionReplacement_ShouldArchiveOld() {
        tree.setConversationCount(5);

        treeUpdater.processObservation(new ObservationCandidate(
                "Mon créateur vit à Paris", TreeBranch.IDENTITE, null));

        assertEquals(1, tree.getActiveLeaves(TreeBranch.IDENTITE).size());

        treeUpdater.processObservation(new ObservationCandidate(
                "Mon créateur vit à Lyon", TreeBranch.IDENTITE, "Mon créateur vit à Paris"));

        assertEquals(1, tree.getActiveLeaves(TreeBranch.IDENTITE).size());
        assertEquals("Mon créateur vit à Lyon", tree.getActiveLeaves(TreeBranch.IDENTITE).get(0).getText());
    }
}
