package org.arcos.UserModel.Retrieval;

import lombok.extern.slf4j.Slf4j;
import org.arcos.UserModel.Models.ObservationLeaf;
import org.arcos.UserModel.Models.TreeBranch;
import org.arcos.UserModel.UserModelProperties;
import org.arcos.UserModel.UserObservationTree;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
public class BranchSummaryBuilder {

    private final UserObservationTree tree;
    private final UserModelProperties properties;

    public BranchSummaryBuilder(UserObservationTree tree, UserModelProperties properties) {
        this.tree = tree;
        this.properties = properties;
    }

    public String rebuild(TreeBranch branch) {
        List<ObservationLeaf> leaves = tree.getActiveLeaves(branch);
        if (leaves.isEmpty()) {
            tree.setSummary(branch, null);
            return null;
        }

        leaves.sort(Comparator.comparingInt(ObservationLeaf::getObservationCount).reversed());

        int maxTokens = getMaxTokensForBranch(branch);
        StringBuilder summary = new StringBuilder();
        int tokenCount = 0;

        for (ObservationLeaf leaf : leaves) {
            String text = leaf.getText();
            if (text == null || text.isBlank()) continue;

            int leafTokens = estimateTokens(text);

            if (tokenCount + leafTokens > maxTokens && tokenCount > 0) {
                break;
            }

            if (summary.length() > 0) {
                summary.append(". ");
                tokenCount += 1;
            }
            summary.append(text);
            tokenCount += leafTokens;
        }

        String result = summary.toString();
        tree.setSummary(branch, result);
        log.debug("Rebuilt summary for {}: ~{} tokens — '{}'",
                branch, tokenCount, result.length() > 60 ? result.substring(0, 57) + "..." : result);
        return result;
    }

    private int getMaxTokensForBranch(TreeBranch branch) {
        return switch (branch) {
            case IDENTITE -> properties.getIdentityBudgetTokens();
            case COMMUNICATION -> properties.getCommunicationBudgetTokens();
            default -> properties.getTotalBudgetTokens();
        };
    }

    public static int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        String[] words = text.split("\\s+");
        return (int) Math.ceil(words.length * 1.3);
    }
}
