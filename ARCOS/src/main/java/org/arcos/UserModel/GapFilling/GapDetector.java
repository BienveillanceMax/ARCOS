package org.arcos.UserModel.GapFilling;

import org.arcos.UserModel.Models.TreeBranch;
import org.arcos.UserModel.UserObservationTree;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class GapDetector {

    private final UserObservationTree tree;

    public GapDetector(UserObservationTree tree) {
        this.tree = tree;
    }

    public Optional<TreeBranch> findLeastPopulatedBranch(Map<TreeBranch, Integer> lastGapQuestions,
                                                          int currentConversation,
                                                          int minConversationsBetween) {
        TreeBranch leastPopulated = null;
        int minLeaves = Integer.MAX_VALUE;

        for (TreeBranch branch : TreeBranch.values()) {
            Integer lastAsked = lastGapQuestions.get(branch);
            if (lastAsked != null && (currentConversation - lastAsked) < minConversationsBetween) {
                continue;
            }

            int leafCount = tree.getActiveLeaves(branch).size();
            if (leafCount < minLeaves) {
                minLeaves = leafCount;
                leastPopulated = branch;
            }
        }

        return Optional.ofNullable(leastPopulated);
    }
}
