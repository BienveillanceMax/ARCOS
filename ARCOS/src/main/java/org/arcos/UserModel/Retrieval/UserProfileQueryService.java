package org.arcos.UserModel.Retrieval;

import lombok.extern.slf4j.Slf4j;
import org.arcos.UserModel.Embedding.LocalEmbeddingService;
import org.arcos.UserModel.Models.ObservationLeaf;
import org.arcos.UserModel.Models.ProfileStability;
import org.arcos.UserModel.Models.TreeBranch;
import org.arcos.UserModel.UserObservationTree;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class UserProfileQueryService {

    private final UserObservationTree tree;
    private final LocalEmbeddingService embeddingService;

    public UserProfileQueryService(UserObservationTree tree,
                                   LocalEmbeddingService embeddingService) {
        this.tree = tree;
        this.embeddingService = embeddingService;
    }

    public List<ObservationLeaf> getTopLeaves(TreeBranch branch, int limit) {
        return tree.getActiveLeaves(branch).stream()
                .sorted(Comparator.comparingInt(ObservationLeaf::getObservationCount).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<ObservationLeaf> searchLeaves(String query, int topK, double minScore) {
        if (!embeddingService.isReady()) {
            log.debug("Embedding service not ready, skipping semantic search");
            return List.of();
        }

        float[] queryEmbedding = embeddingService.embed(query);
        if (queryEmbedding == null) {
            return List.of();
        }

        List<ScoredLeaf> scored = new ArrayList<>();
        Instant now = Instant.now();

        for (TreeBranch branch : TreeBranch.values()) {
            for (ObservationLeaf leaf : tree.getActiveLeaves(branch)) {
                if (leaf.getEmbedding() == null) continue;

                double cosine = embeddingService.cosineSimilarity(queryEmbedding, leaf.getEmbedding());
                double retention = computeRetention(leaf, now);
                double srank = 0.6 * cosine
                        + 0.15 * leaf.getEmotionalImportance()
                        + 0.25 * retention;

                if (srank >= minScore) {
                    scored.add(new ScoredLeaf(leaf, srank));
                }
            }
        }

        scored.sort(Comparator.comparingDouble(ScoredLeaf::score).reversed());

        return scored.stream()
                .limit(topK)
                .map(ScoredLeaf::leaf)
                .collect(Collectors.toList());
    }

    public Optional<String> getBranchSummary(TreeBranch branch) {
        return Optional.ofNullable(tree.getSummary(branch));
    }

    public ProfileStability getProfileStability() {
        return tree.getProfileStability();
    }

    private double computeRetention(ObservationLeaf leaf, Instant now) {
        double daysSinceReinforced = Duration.between(leaf.getLastReinforced(), now).toHours() / 24.0;
        double stability = leaf.getObservationCount();
        double k = leaf.getBranch().getDecayClass().getRetentionFactor();
        return Math.exp(-daysSinceReinforced / (k * stability));
    }

    private record ScoredLeaf(ObservationLeaf leaf, double score) {}
}
