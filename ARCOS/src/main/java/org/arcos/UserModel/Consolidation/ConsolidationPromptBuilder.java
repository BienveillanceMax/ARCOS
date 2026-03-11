package org.arcos.UserModel.Consolidation;

import org.arcos.LLM.Local.ThinkingMode;
import org.arcos.UserModel.Models.ObservationLeaf;
import org.arcos.UserModel.Models.TreeBranch;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConsolidationPromptBuilder {

    private static final String JSON_INSTRUCTION = "Reponds UNIQUEMENT en JSON strict. Pas de texte avant ou apres le JSON.";

    public String buildCrossBranchConflict(ObservationLeaf leafA, ObservationLeaf leafB, double similarity) {
        return ThinkingMode.THINK.getPrefix() + """
                Consolidation profil utilisateur.
                Deux observations similaires (sim: %.2f) dans branches differentes, conflit potentiel.

                A: %s | "%s" | obs:%d | importance:%.2f | renfort:%s
                B: %s | "%s" | obs:%d | importance:%.2f | renfort:%s

                Actions:
                - MERGE: fusionner (winner_id)
                - REBRANCH: deplacer vers autre branche (target_branch)
                - REWRITE: reecrire texte (new_text)
                - ARCHIVE: archiver moins pertinente
                - KEEP_BOTH: garder (complementaires)

                %s
                {"decision":"ACTION","winner_id":"uuid","merge_target_id":"uuid","new_text":"texte","target_branch":"BRANCHE","confidence":0.0,"reasoning":"explication"}
                """.formatted(
                similarity,
                leafA.getBranch(), leafA.getText(), leafA.getObservationCount(),
                leafA.getEmotionalImportance(), leafA.getLastReinforced(),
                leafB.getBranch(), leafB.getText(), leafB.getObservationCount(),
                leafB.getEmotionalImportance(), leafB.getLastReinforced(),
                JSON_INSTRUCTION
        );
    }

    public String buildIntraBranchConsolidation(ObservationLeaf leaf, List<ObservationLeaf> similarLeaves) {
        StringBuilder similar = new StringBuilder();
        for (ObservationLeaf s : similarLeaves) {
            similar.append("- %s | \"%s\" | obs:%d | importance:%.2f\n".formatted(
                    s.getId(), s.getText(), s.getObservationCount(), s.getEmotionalImportance()));
        }

        return ThinkingMode.THINK.getPrefix() + """
                Consolidation profil utilisateur.
                Observation branche %s a consolider avec similaires.

                Cible: %s | "%s" | obs:%d | importance:%.2f

                Similaires:
                %s
                Actions:
                - MERGE: fusionner avec gagnant (winner_id)
                - REWRITE: reecrire pour synthetiser (new_text)
                - ARCHIVE: archiver doublons
                - KEEP_BOTH: garder toutes

                %s
                {"decision":"ACTION","winner_id":"uuid","new_text":"texte","confidence":0.0,"reasoning":"explication"}
                """.formatted(
                leaf.getBranch(),
                leaf.getId(), leaf.getText(), leaf.getObservationCount(), leaf.getEmotionalImportance(),
                similar,
                JSON_INSTRUCTION
        );
    }

    public String buildSimpleMerge(ObservationLeaf leafA, ObservationLeaf leafB) {
        return ThinkingMode.NO_THINK.getPrefix() + """
                Observations quasi-identiques. Laquelle garder?

                A: %s | "%s" | obs=%d
                B: %s | "%s" | obs=%d

                %s
                {"winner_id":"uuid"}
                """.formatted(
                leafA.getId(), leafA.getText(), leafA.getObservationCount(),
                leafB.getId(), leafB.getText(), leafB.getObservationCount(),
                JSON_INSTRUCTION
        );
    }

    public String buildCrossSessionPattern(List<ObservationLeaf> cluster, TreeBranch branch) {
        StringBuilder clusterDesc = new StringBuilder();
        for (ObservationLeaf leaf : cluster) {
            clusterDesc.append("- \"%s\" (obs:%d, importance:%.2f)\n".formatted(
                    leaf.getText(), leaf.getObservationCount(), leaf.getEmotionalImportance()));
        }

        return ThinkingMode.THINK.getPrefix() + """
                Detection patterns profil utilisateur.
                Observations similaires (branche %s):

                %s
                Pattern significatif meritant resume?

                Oui → CREATE_SUMMARY avec texte
                Non → NO_PATTERN

                %s
                {"decision":"CREATE_SUMMARY","new_text":"Mon createur...","confidence":0.0,"reasoning":"explication"}
                ou
                {"decision":"NO_PATTERN","confidence":0.0,"reasoning":"explication"}
                """.formatted(
                branch,
                clusterDesc,
                JSON_INSTRUCTION
        );
    }
}
