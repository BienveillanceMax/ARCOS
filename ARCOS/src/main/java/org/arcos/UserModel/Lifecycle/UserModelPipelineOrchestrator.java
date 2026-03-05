package org.arcos.UserModel.Lifecycle;

import lombok.extern.slf4j.Slf4j;
import org.arcos.UserModel.Extraction.UserTreeUpdater;
import org.arcos.UserModel.Heuristics.EmaBaselineManager;
import org.arcos.UserModel.Heuristics.HeuristicSignalExtractor;
import org.arcos.UserModel.Heuristics.HeuristicTextTemplates;
import org.arcos.UserModel.Models.ObservationCandidate;
import org.arcos.UserModel.Models.ObservationLeaf;
import org.arcos.UserModel.Models.SignificantChange;
import org.arcos.UserModel.Persistence.UserTreePersistenceService;
import org.arcos.UserModel.UserModelProperties;
import org.arcos.UserModel.UserObservationTree;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class UserModelPipelineOrchestrator {

    private final UserObservationTree tree;
    private final HeuristicSignalExtractor signalExtractor;
    private final EmaBaselineManager emaManager;
    private final HeuristicTextTemplates textTemplates;
    private final UserTreeUpdater treeUpdater;
    private final UserTreePersistenceService persistenceService;
    private final UserModelProperties properties;

    public UserModelPipelineOrchestrator(UserObservationTree tree,
                                         UserTreeUpdater treeUpdater,
                                         UserTreePersistenceService persistenceService,
                                         UserModelProperties properties) {
        this.tree = tree;
        this.treeUpdater = treeUpdater;
        this.persistenceService = persistenceService;
        this.properties = properties;

        this.signalExtractor = new HeuristicSignalExtractor(properties.getDisfluenceWords());
        this.emaManager = new EmaBaselineManager(
                properties.getEmaAlphaColdStart(),
                properties.getEmaAlphaStable(),
                properties.getSignificanceThreshold(),
                properties.getSignificanceConsecutiveSessions()
        );
        this.textTemplates = new HeuristicTextTemplates();
    }

    public void processConversationAsync(List<String> userMessages, boolean hadInitiative) {
        CompletableFuture.runAsync(() -> {
            try {
                processConversation(userMessages, hadInitiative);
            } catch (Exception e) {
                log.error("Error in user model pipeline", e);
            }
        });
    }

    public void processConversation(List<String> userMessages, boolean hadInitiative) {
        log.debug("User model pipeline: processing {} user messages", userMessages.size());

        // Channel A: Heuristic signals
        Map<String, Double> signals = signalExtractor.extractSignals(userMessages, hadInitiative);

        // Load baselines from tree (persisted across sessions)
        Map<String, Double> currentBaselines = tree.getHeuristicBaselines();
        if (!currentBaselines.isEmpty()) {
            emaManager.setBaselines(currentBaselines);
        }

        int conversationCount = tree.getConversationCount();
        List<SignificantChange> changes = emaManager.updateBaselines(signals, conversationCount);

        // Generate observation leaves from significant changes
        List<ObservationLeaf> heuristicLeaves = textTemplates.generateLeaves(changes, conversationCount);
        for (ObservationLeaf leaf : heuristicLeaves) {
            ObservationCandidate candidate = new ObservationCandidate(
                    leaf.getText(), leaf.getBranch(), null);
            try {
                treeUpdater.processObservation(candidate);
            } catch (Exception e) {
                log.warn("Failed to process heuristic observation: {}", e.getMessage());
            }
        }

        // Update state
        tree.incrementConversationCount();
        tree.setHeuristicBaselines(emaManager.getBaselines());
        persistenceService.scheduleSave();

        log.debug("User model pipeline complete: {} signals, {} changes, {} leaves generated, conversationCount={}",
                signals.size(), changes.size(), heuristicLeaves.size(), tree.getConversationCount());
    }
}
