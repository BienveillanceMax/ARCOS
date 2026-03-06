package org.arcos.UserModel.Retrieval;

import lombok.extern.slf4j.Slf4j;
import org.arcos.LLM.Local.LocalLlmService;
import org.arcos.UserModel.Models.ObservationLeaf;
import org.arcos.UserModel.Models.TreeBranch;
import org.arcos.UserModel.UserModelProperties;
import org.arcos.UserModel.UserObservationTree;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ConditionalOnProperty(name = "arcos.local-llm.enabled", havingValue = "true")
public class BranchSummaryGenerator implements BranchSummaryProvider {

    private final LocalLlmService localLlmService;
    private final UserObservationTree tree;
    private final UserModelProperties properties;

    public BranchSummaryGenerator(LocalLlmService localLlmService,
                                  UserObservationTree tree,
                                  UserModelProperties properties) {
        this.localLlmService = localLlmService;
        this.tree = tree;
        this.properties = properties;
    }

    @Override
    public String rebuild(TreeBranch branch) {
        List<ObservationLeaf> leaves = tree.getActiveLeaves(branch);
        if (leaves.isEmpty()) {
            tree.setSummary(branch, null);
            return null;
        }

        leaves.sort(Comparator.comparingInt(ObservationLeaf::getObservationCount).reversed());

        String prompt = buildSummaryPrompt(branch, leaves);

        try {
            String summary = localLlmService.generateSimpleAsync(prompt)
                    .get(properties.getConsolidation().getTimeoutMs(), TimeUnit.MILLISECONDS);
            if (summary != null && !summary.isBlank()) {
                tree.setSummary(branch, summary);
                log.debug("Generated LLM summary for {}: '{}'",
                        branch, summary.length() > 60 ? summary.substring(0, 57) + "..." : summary);
                return summary;
            }
        } catch (Exception e) {
            log.warn("Failed to generate LLM summary for {}: {}", branch, e.getMessage());
        }

        return null;
    }

    @Override
    public boolean isAvailable() {
        return localLlmService.isAvailable();
    }

    String buildSummaryPrompt(TreeBranch branch, List<ObservationLeaf> leaves) {
        StringBuilder observations = new StringBuilder();
        int maxTokens = getMaxTokensForBranch(branch);
        int tokenBudget = 0;
        for (ObservationLeaf leaf : leaves) {
            int leafTokens = BranchSummaryBuilder.estimateTokens(leaf.getText());
            if (tokenBudget + leafTokens > maxTokens * 3 && tokenBudget > 0) break;
            observations.append("- ").append(leaf.getText()).append("\n");
            tokenBudget += leafTokens;
        }

        return """
                Genere un resume concis de la branche "%s" du profil utilisateur.
                Le resume doit etre ecrit a la premiere personne du point de vue de l'assistant ("Mon createur...").
                Le resume ne doit pas depasser %d mots.

                Observations:
                %s
                Resume:
                """.formatted(branch.name(), maxTokens, observations);
    }

    private int getMaxTokensForBranch(TreeBranch branch) {
        return switch (branch) {
            case IDENTITE -> properties.getIdentityBudgetTokens();
            case COMMUNICATION -> properties.getCommunicationBudgetTokens();
            default -> properties.getTotalBudgetTokens();
        };
    }
}
