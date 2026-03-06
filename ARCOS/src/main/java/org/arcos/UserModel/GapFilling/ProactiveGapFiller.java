package org.arcos.UserModel.GapFilling;

import lombok.extern.slf4j.Slf4j;
import org.arcos.UserModel.Models.ProfileStability;
import org.arcos.UserModel.Models.TreeBranch;
import org.arcos.UserModel.UserModelProperties;
import org.arcos.UserModel.UserObservationTree;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class ProactiveGapFiller {

    private final GapDetector gapDetector;
    private final UserObservationTree tree;
    private final UserModelProperties properties;

    private int cachedForConversation = -1;
    private String cachedHint = null;

    public ProactiveGapFiller(GapDetector gapDetector,
                               UserObservationTree tree,
                               UserModelProperties properties) {
        this.gapDetector = gapDetector;
        this.tree = tree;
        this.properties = properties;
    }

    public Optional<String> getGapHint() {
        int currentConversation = tree.getConversationCount();

        if (currentConversation == cachedForConversation) {
            return Optional.ofNullable(cachedHint);
        }

        cachedForConversation = currentConversation;
        cachedHint = computeHint(currentConversation);
        return Optional.ofNullable(cachedHint);
    }

    private String computeHint(int currentConversation) {
        if (!properties.getProactiveGapFilling().isEnabled()) {
            return null;
        }

        ProfileStability stability = tree.getProfileStability();
        if (stability.ordinal() < ProfileStability.MEDIUM.ordinal()) {
            return null;
        }

        Map<TreeBranch, Integer> lastGap = tree.getLastGapQuestionPerBranch();
        int minBetween = properties.getProactiveGapFilling().getMinConversationsBetweenSameBranch();

        Optional<TreeBranch> target = gapDetector.findLeastPopulatedBranch(lastGap, currentConversation, minBetween);
        if (target.isEmpty()) {
            return null;
        }

        TreeBranch branch = target.get();
        String hint = generateHint(branch);

        tree.recordGapQuestion(branch, currentConversation);
        log.debug("Gap-filling hint generated for branch {} at conversation {}", branch, currentConversation);
        return hint;
    }

    public static String generateHint(TreeBranch branch) {
        return switch (branch) {
            case IDENTITE ->
                    "Si l'occasion se présente naturellement, demande à l'utilisateur des détails sur lui (nom, métier, lieu de vie).";
            case COMMUNICATION ->
                    "Si l'occasion se présente naturellement, observe comment l'utilisateur préfère communiquer (longueur, ton, style).";
            case HABITUDES ->
                    "Si l'occasion se présente naturellement, demande à l'utilisateur ses habitudes quotidiennes ou routines.";
            case OBJECTIFS ->
                    "Si l'occasion se présente naturellement, demande à l'utilisateur ses projets ou objectifs actuels.";
            case EMOTIONS ->
                    "Si l'occasion se présente naturellement, intéresse-toi à ce qui motive ou préoccupe l'utilisateur.";
            case INTERETS ->
                    "Si l'occasion se présente naturellement, demande à l'utilisateur ses centres d'intérêt ou passions.";
        };
    }
}
