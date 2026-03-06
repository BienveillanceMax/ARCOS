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
                Tu es un systeme de consolidation de profil utilisateur.
                Deux observations similaires (similarite: %.2f) existent dans des branches differentes et sont potentiellement en conflit.

                Observation A:
                - Branche: %s
                - Texte: "%s"
                - Nombre d'observations: %d
                - Importance emotionnelle: %.2f
                - Dernier renforcement: %s

                Observation B:
                - Branche: %s
                - Texte: "%s"
                - Nombre d'observations: %d
                - Importance emotionnelle: %.2f
                - Dernier renforcement: %s

                Decide de l'action a effectuer parmi: MERGE, REBRANCH, REWRITE, ARCHIVE, KEEP_BOTH.
                - MERGE: fusionner les deux, garder le gagnant (winner_id)
                - REBRANCH: deplacer une observation vers la branche de l'autre (target_branch)
                - REWRITE: reecrire le texte d'une observation (new_text)
                - ARCHIVE: archiver l'observation la moins pertinente
                - KEEP_BOTH: garder les deux (observations complementaires)

                %s

                Reponds avec ce format JSON:
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
            similar.append("- ID: %s | Texte: \"%s\" | Observations: %d | Importance: %.2f\n".formatted(
                    s.getId(), s.getText(), s.getObservationCount(), s.getEmotionalImportance()));
        }

        return ThinkingMode.THINK.getPrefix() + """
                Tu es un systeme de consolidation de profil utilisateur.
                Une observation dans la branche %s necessite une consolidation avec des observations similaires.

                Observation cible:
                - ID: %s
                - Texte: "%s"
                - Nombre d'observations: %d
                - Importance emotionnelle: %.2f

                Observations similaires dans la meme branche:
                %s
                Decide de l'action a effectuer parmi: MERGE, REWRITE, ARCHIVE, KEEP_BOTH.
                - MERGE: fusionner avec le gagnant (winner_id), supprimer les autres
                - REWRITE: reecrire le texte pour synthetiser (new_text)
                - ARCHIVE: archiver les doublons
                - KEEP_BOTH: garder toutes les observations

                %s

                Reponds avec ce format JSON:
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
                Deux observations sont quasi-identiques. Laquelle garder?

                A: ID=%s | "%s" | observations=%d
                B: ID=%s | "%s" | observations=%d

                %s

                Reponds: {"winner_id":"uuid"}
                """.formatted(
                leafA.getId(), leafA.getText(), leafA.getObservationCount(),
                leafB.getId(), leafB.getText(), leafB.getObservationCount(),
                JSON_INSTRUCTION
        );
    }

    public String buildCrossSessionPattern(List<ObservationLeaf> cluster, TreeBranch branch) {
        StringBuilder clusterDesc = new StringBuilder();
        for (ObservationLeaf leaf : cluster) {
            clusterDesc.append("- \"%s\" (observations: %d, importance: %.2f)\n".formatted(
                    leaf.getText(), leaf.getObservationCount(), leaf.getEmotionalImportance()));
        }

        return ThinkingMode.THINK.getPrefix() + """
                Tu es un systeme de detection de patterns dans un profil utilisateur.
                Voici un groupe d'observations similaires dans la branche %s:

                %s
                Ces observations forment-elles un pattern significatif qui merite une observation resumee?

                Si oui, reponds CREATE_SUMMARY avec le texte du resume.
                Si non, reponds NO_PATTERN.

                %s

                Reponds avec ce format JSON:
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
