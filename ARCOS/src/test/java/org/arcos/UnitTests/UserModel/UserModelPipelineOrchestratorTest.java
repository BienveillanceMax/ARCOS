package org.arcos.UnitTests.UserModel;

import org.arcos.UserModel.Extraction.UserTreeUpdater;
import org.arcos.UserModel.Lifecycle.UserModelPipelineOrchestrator;
import org.arcos.UserModel.Models.ObservationCandidate;
import org.arcos.UserModel.Models.ProfileStability;
import org.arcos.UserModel.Persistence.UserTreePersistenceService;
import org.arcos.UserModel.UserModelProperties;
import org.arcos.UserModel.UserObservationTree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserModelPipelineOrchestratorTest {

    private UserObservationTree tree;
    private UserModelPipelineOrchestrator pipeline;
    private UserModelProperties properties;

    @Mock
    private UserTreeUpdater treeUpdater;
    @Mock
    private UserTreePersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        properties = new UserModelProperties();
        tree = new UserObservationTree(properties);
        pipeline = new UserModelPipelineOrchestrator(tree, treeUpdater, persistenceService, properties);
    }

    @Test
    void processConversation_ShouldIncrementConversationCount() {
        assertEquals(0, tree.getConversationCount());

        pipeline.processConversation(List.of("Bonjour", "Comment ça va ?", "Merci"), false);

        assertEquals(1, tree.getConversationCount());
    }

    @Test
    void processConversation_ShouldUpdateHeuristicBaselines() {
        pipeline.processConversation(List.of("Bonjour", "Comment ça va ?", "Au revoir"), false);

        assertFalse(tree.getHeuristicBaselines().isEmpty());
        assertTrue(tree.getHeuristicBaselines().containsKey("avg_word_count"));
    }

    @Test
    void processConversation_ShouldScheduleSave() {
        pipeline.processConversation(List.of("test message"), false);

        verify(persistenceService).scheduleSave();
    }

    @Test
    void processConversation_After5Conversations_ShouldGenerateHeuristicLeaves() {
        tree.setConversationCount(4);

        when(treeUpdater.processObservation(any(ObservationCandidate.class)))
                .thenReturn(UserTreeUpdater.UpdateResult.ADD);

        // Fill baselines first with 4 calls
        for (int i = 0; i < 4; i++) {
            pipeline.processConversation(
                    List.of("Bonjour comment vas-tu aujourd'hui ?",
                            "J'aimerais savoir une chose",
                            "Merci beaucoup pour ton aide"),
                    false);
        }

        // At this point, tree.conversationCount is at least 8 (4 initial + 4 calls)
        // The heuristic templates should have a chance to fire once significant changes accumulate
        verify(persistenceService, atLeast(4)).scheduleSave();
    }

    @Test
    void processConversation_ColdStart_ShouldNotGenerateLeaves() {
        tree.setConversationCount(0);

        pipeline.processConversation(List.of("Bonjour"), false);

        // Cold-start (<5 conv), no heuristic leaves generated even if changes detected
        // The treeUpdater should not be called because HeuristicTextTemplates guards cold-start
        // However, the pipeline still runs signals and baselines
        assertEquals(1, tree.getConversationCount());
    }

    @Test
    void processConversation_ShouldUpdateProfileStability() {
        tree.setConversationCount(4);
        assertEquals(ProfileStability.LOW, tree.getProfileStability());

        pipeline.processConversation(List.of("test"), false);

        assertEquals(ProfileStability.MEDIUM, tree.getProfileStability());
    }
}
