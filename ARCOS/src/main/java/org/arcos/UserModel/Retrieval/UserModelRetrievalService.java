package org.arcos.UserModel.Retrieval;

import lombok.extern.slf4j.Slf4j;
import org.arcos.UserModel.Embedding.LocalEmbeddingService;
import org.arcos.UserModel.Models.ObservationLeaf;
import org.arcos.UserModel.Models.TreeBranch;
import org.arcos.UserModel.Models.UserProfileContext;
import org.arcos.UserModel.UserModelProperties;
import org.arcos.UserModel.UserObservationTree;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
public class UserModelRetrievalService {

    private final UserObservationTree tree;
    private final LocalEmbeddingService embeddingService;
    private final UserModelProperties properties;

    public UserModelRetrievalService(UserObservationTree tree,
                                     LocalEmbeddingService embeddingService,
                                     UserModelProperties properties) {
        this.tree = tree;
        this.embeddingService = embeddingService;
        this.properties = properties;
    }

    public UserProfileContext retrieveUserContext(String userQuery) {
        int conversationCount = tree.getConversationCount();
        if (conversationCount < properties.getMinConversationsBeforeInjection()) {
            log.debug("Cold-start gate: {} conversations < {} required, skipping user profile injection",
                    conversationCount, properties.getMinConversationsBeforeInjection());
            return new UserProfileContext(null, null, null, conversationCount);
        }

        String identitySummary = tree.getSummary(TreeBranch.IDENTITE);
        String communicationSummary = tree.getSummary(TreeBranch.COMMUNICATION);

        String onDemandText = retrieveOnDemandLeaf(userQuery);

        int totalTokens = estimateTokens(identitySummary)
                + estimateTokens(communicationSummary)
                + estimateTokens(onDemandText);

        if (totalTokens > properties.getTotalBudgetTokens()) {
            onDemandText = truncateToFit(identitySummary, communicationSummary, onDemandText);
        }

        return new UserProfileContext(identitySummary, communicationSummary, onDemandText, conversationCount);
    }

    private String retrieveOnDemandLeaf(String userQuery) {
        if (!embeddingService.isReady()) {
            log.debug("Embedding service not ready, skipping on-demand leaf retrieval");
            return null;
        }

        float[] queryEmbedding = embeddingService.embed(userQuery);
        if (queryEmbedding == null) return null;

        ObservationLeaf bestLeaf = null;
        double bestSrank = -1.0;

        for (TreeBranch branch : TreeBranch.values()) {
            if (branch.isAlwaysInjected()) continue;

            List<ObservationLeaf> leaves = tree.getActiveLeaves(branch);
            for (ObservationLeaf leaf : leaves) {
                if (leaf.getEmbedding() == null) continue;

                double cosine = embeddingService.cosineSimilarity(queryEmbedding, leaf.getEmbedding());
                double retention = computeRetention(leaf);
                double srank = 0.6 * cosine
                        + 0.15 * leaf.getEmotionalImportance()
                        + 0.25 * retention;

                if (srank > bestSrank) {
                    bestSrank = srank;
                    bestLeaf = leaf;
                }
            }
        }

        if (bestLeaf != null && bestSrank >= properties.getRetrievalMinSrank()) {
            bestLeaf.setObservationCount(bestLeaf.getObservationCount() + 1);
            bestLeaf.setLastReinforced(Instant.now());
            log.debug("On-demand leaf: '{}' (S_rank={:.3f})", bestLeaf.getText(), bestSrank);
            return bestLeaf.getText();
        }

        return null;
    }

    private double computeRetention(ObservationLeaf leaf) {
        double daysSinceReinforced = Duration.between(leaf.getLastReinforced(), Instant.now()).toHours() / 24.0;
        double stability = leaf.getObservationCount();
        double k = leaf.getBranch().getDecayClass().getRetentionFactor();
        return Math.exp(-daysSinceReinforced / (k * stability));
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        return (int) Math.ceil(text.split("\\s+").length * 1.3);
    }

    private String truncateToFit(String identity, String communication, String onDemand) {
        int usedTokens = estimateTokens(identity) + estimateTokens(communication);
        int remaining = properties.getTotalBudgetTokens() - usedTokens;
        if (remaining <= 0 || onDemand == null) return null;

        String[] words = onDemand.split("\\s+");
        int maxWords = (int) (remaining / 1.3);
        if (maxWords >= words.length) return onDemand;
        if (maxWords <= 0) return null;

        StringBuilder truncated = new StringBuilder();
        for (int i = 0; i < maxWords; i++) {
            if (i > 0) truncated.append(" ");
            truncated.append(words[i]);
        }
        return truncated.toString();
    }
}
