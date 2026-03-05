package org.arcos.UserModel.Lifecycle;

import lombok.extern.slf4j.Slf4j;
import org.arcos.UserModel.Models.ObservationLeaf;
import org.arcos.UserModel.Models.TreeBranch;
import org.arcos.UserModel.Persistence.UserTreePersistenceService;
import org.arcos.UserModel.Retrieval.BranchSummaryBuilder;
import org.arcos.UserModel.UserObservationTree;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class EbbinghausPruningService {

    private static final double RETENTION_THRESHOLD = 0.05;
    private static final double WEEKLY_DECAY_FACTOR = 0.95;

    private final UserObservationTree tree;
    private final UserTreePersistenceService persistenceService;
    private final BranchSummaryBuilder summaryBuilder;

    public EbbinghausPruningService(UserObservationTree tree,
                                    UserTreePersistenceService persistenceService,
                                    BranchSummaryBuilder summaryBuilder) {
        this.tree = tree;
        this.persistenceService = persistenceService;
        this.summaryBuilder = summaryBuilder;
    }

    @Scheduled(cron = "${arcos.user-model.pruning-cron:0 0 3 * * *}")
    public void prune() {
        log.info("Starting Ebbinghaus pruning cycle");
        Set<TreeBranch> modifiedBranches = new HashSet<>();
        int pruned = 0;

        List<ObservationLeaf> allLeaves = tree.getAllActiveLeaves();

        for (ObservationLeaf leaf : allLeaves) {
            double retention = computeRetention(leaf);

            if (retention < RETENTION_THRESHOLD) {
                tree.removeLeaf(leaf.getId());
                persistenceService.archiveLeaf(leaf, "Ebbinghaus pruning (retention=" + String.format("%.4f", retention) + ")");
                modifiedBranches.add(leaf.getBranch());
                pruned++;
            }
        }

        for (TreeBranch branch : modifiedBranches) {
            summaryBuilder.rebuild(branch);
        }

        if (pruned > 0) {
            persistenceService.scheduleSave();
            log.info("Pruned {} observations, rebuilt summaries for {} branches", pruned, modifiedBranches.size());
        } else {
            log.info("No observations pruned");
        }
    }

    public double computeRetention(ObservationLeaf leaf) {
        double daysSinceReinforced = Duration.between(leaf.getLastReinforced(), Instant.now()).toHours() / 24.0;
        double stability = Math.max(leaf.getObservationCount(), 1);
        double k = leaf.getBranch().getDecayClass().getRetentionFactor();

        double baseRetention = Math.exp(-daysSinceReinforced / (k * stability));

        double weeksSinceReinforced = daysSinceReinforced / 7.0;
        double weeklyDecay = Math.pow(WEEKLY_DECAY_FACTOR, weeksSinceReinforced);

        return baseRetention * weeklyDecay;
    }
}
