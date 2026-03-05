package org.arcos.UserModel.Extraction;

import lombok.extern.slf4j.Slf4j;
import org.arcos.UserModel.Embedding.LocalEmbeddingService;
import org.arcos.UserModel.Models.ObservationCandidate;
import org.arcos.UserModel.Models.ObservationLeaf;
import org.arcos.UserModel.Models.ObservationSource;
import org.arcos.UserModel.Persistence.UserTreePersistenceService;
import org.arcos.UserModel.Retrieval.BranchSummaryBuilder;
import org.arcos.UserModel.UserObservationTree;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
public class UserTreeUpdater {

    private static final double THRESHOLD_ADD = 0.5;
    private static final double THRESHOLD_UPDATE = 0.9;

    private final UserObservationTree tree;
    private final LocalEmbeddingService embeddingService;
    private final BranchSummaryBuilder summaryBuilder;
    private final UserTreePersistenceService persistenceService;

    public enum UpdateResult { ADD, UPDATE, AMBIGUOUS }

    public UserTreeUpdater(UserObservationTree tree,
                           LocalEmbeddingService embeddingService,
                           BranchSummaryBuilder summaryBuilder,
                           UserTreePersistenceService persistenceService) {
        this.tree = tree;
        this.embeddingService = embeddingService;
        this.summaryBuilder = summaryBuilder;
        this.persistenceService = persistenceService;
    }

    public UpdateResult processObservation(ObservationCandidate candidate) {
        if (candidate == null || candidate.text() == null || candidate.text().isBlank()) {
            log.debug("Ignoring null or blank observation candidate");
            return UpdateResult.ADD;
        }

        if (candidate.replacesText() != null && !candidate.replacesText().isBlank()) {
            return processContradiction(candidate);
        }

        float[] candidateEmbedding = embeddingService.embed(candidate.text());

        ObservationLeaf bestMatch = null;
        double bestSimilarity = -1.0;

        List<ObservationLeaf> allLeaves = tree.getAllActiveLeaves();
        for (ObservationLeaf leaf : allLeaves) {
            if (leaf.getEmbedding() == null) continue;
            double similarity = embeddingService.cosineSimilarity(candidateEmbedding, leaf.getEmbedding());
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestMatch = leaf;
            }
        }

        UpdateResult result;

        if (bestSimilarity >= THRESHOLD_UPDATE && bestMatch != null) {
            bestMatch.setObservationCount(bestMatch.getObservationCount() + 1);
            bestMatch.setLastReinforced(Instant.now());
            if (candidate.explicit()) {
                bestMatch.setObservationCount(Math.max(bestMatch.getObservationCount(), 3));
                bestMatch.setEmotionalImportance(Math.max(bestMatch.getEmotionalImportance(), 0.8f));
            }
            result = UpdateResult.UPDATE;
            log.debug("Updated existing leaf '{}' (count={}, similarity={:.2f})",
                    bestMatch.getText(), bestMatch.getObservationCount(), bestSimilarity);

        } else if (bestSimilarity >= THRESHOLD_ADD && bestMatch != null) {
            bestMatch.setNeedsConsolidation(true);
            ObservationLeaf newLeaf = createLeaf(candidate, candidateEmbedding);
            newLeaf.setNeedsConsolidation(true);
            if (bestMatch.getBranch() != candidate.branch()) {
                newLeaf.setConflictsWith(bestMatch.getId());
            }
            tree.addLeaf(newLeaf);
            result = UpdateResult.AMBIGUOUS;
            log.debug("Ambiguous observation '{}' (similarity={:.2f}), added with consolidation flag",
                    candidate.text(), bestSimilarity);

        } else {
            ObservationLeaf newLeaf = createLeaf(candidate, candidateEmbedding);
            if (candidate.explicit()) {
                newLeaf.setObservationCount(3);
                newLeaf.setEmotionalImportance(0.8f);
            }
            tree.addLeaf(newLeaf);
            result = UpdateResult.ADD;
            log.debug("Added new observation: '{}'", candidate.text());
        }

        summaryBuilder.rebuild(candidate.branch());
        persistenceService.scheduleSave();

        return result;
    }

    private UpdateResult processContradiction(ObservationCandidate candidate) {
        List<ObservationLeaf> branchLeaves = tree.getActiveLeaves(candidate.branch());
        ObservationLeaf toReplace = null;

        for (ObservationLeaf leaf : branchLeaves) {
            if (leaf.getText() != null && leaf.getText().equals(candidate.replacesText())) {
                toReplace = leaf;
                break;
            }
        }

        if (toReplace != null) {
            tree.removeLeaf(toReplace.getId());
            persistenceService.archiveLeaf(toReplace, "Replaced by: " + candidate.text());
            log.debug("Replaced leaf '{}' with '{}'", toReplace.getText(), candidate.text());
        }

        float[] candidateEmbedding = embeddingService.embed(candidate.text());
        ObservationLeaf newLeaf = createLeaf(candidate, candidateEmbedding);
        if (candidate.explicit()) {
            newLeaf.setObservationCount(3);
            newLeaf.setEmotionalImportance(0.8f);
        }
        tree.addLeaf(newLeaf);

        summaryBuilder.rebuild(candidate.branch());
        persistenceService.scheduleSave();

        return UpdateResult.ADD;
    }

    private ObservationLeaf createLeaf(ObservationCandidate candidate, float[] embedding) {
        ObservationSource source = candidate.explicit()
                ? ObservationSource.USER_EXPLICIT
                : ObservationSource.LLM_EXTRACTED;
        ObservationLeaf leaf = new ObservationLeaf(candidate.text(), candidate.branch(), source, embedding);
        leaf.setEmotionalImportance(candidate.emotionalImportance());
        return leaf;
    }
}
