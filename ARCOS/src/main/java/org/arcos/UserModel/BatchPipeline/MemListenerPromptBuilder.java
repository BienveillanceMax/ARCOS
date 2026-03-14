package org.arcos.UserModel.BatchPipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.arcos.UserModel.BatchPipeline.Queue.ConversationChunk;
import org.arcos.UserModel.BatchPipeline.Queue.ConversationPair;
import org.arcos.UserModel.PersonaTree.PersonaTree;
import org.arcos.UserModel.PersonaTree.PersonaTreeGate;
import org.arcos.UserModel.PersonaTree.PersonaTreeSchemaLoader;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class MemListenerPromptBuilder {

    private final PersonaTreeGate personaTreeGate;
    private final PersonaTreeSchemaLoader schemaLoader;
    private final ObjectMapper objectMapper;

    public MemListenerPromptBuilder(PersonaTreeGate personaTreeGate,
                                     PersonaTreeSchemaLoader schemaLoader) {
        this.personaTreeGate = personaTreeGate;
        this.schemaLoader = schemaLoader;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public String buildPrompt(ConversationChunk chunk) {
        StringBuilder sb = new StringBuilder();

        // 1. Role
        sb.append("Tu es un générateur d'opérations pour un arbre de mémoire PersonaTree.\n\n");

        // 2. Inputs
        sb.append("## Entrées\n");
        sb.append("(1) Un schéma de persona sous forme d'arbre JSON hiérarchique.\n");
        sb.append("(2) Un historique de dialogue.\n\n");

        // 3. Objective
        sb.append("## Objectif\n");
        sb.append("Transformer l'historique de dialogue en séquence d'opérations pour mettre à jour le schéma de persona, ");
        sb.append("en couvrant le plus exhaustivement possible toutes les informations sur cette personne, ");
        sb.append("notamment les caractéristiques personnalisées.\n\n");

        // 4. Schema rules
        sb.append("## Règles sur le schéma\n");
        sb.append("- Le schéma représente l'information déjà structurée sur l'utilisateur ; ne pas ré-extraire ce qui y figure déjà.\n");
        sb.append("- Ne générer des opérations que pour des faits, détails ou préférences nouveaux présents dans le dialogue.\n");
        sb.append("- En cas de conflit entre le schéma et le dialogue, la déclaration explicite la plus récente du dialogue prévaut.\n");
        sb.append("- N'invente rien qui n'est pas dans le dialogue.\n\n");

        // 5. Operation principles
        sb.append("## Principes d'opération\n");
        sb.append("- ADD(chemin, \"valeur\") : la feuille est actuellement vide et le dialogue contient une information pertinente.\n");
        sb.append("- UPDATE(chemin, \"valeur fusionnée\") : la feuille contient déjà une valeur et le dialogue apporte une information nouvelle ou complémentaire.\n");
        sb.append("- DELETE(chemin, None) : le dialogue contredit explicitement l'information existante et aucune valeur de remplacement n'est fournie.\n");
        sb.append("- NO_OP() : le dialogue ne contient aucune information pertinente pour le schéma.\n\n");

        // 6. UPDATE merge rules
        sb.append("## Règles de fusion pour UPDATE\n");
        sb.append("- La nouvelle valeur doit sémantiquement contenir et intégrer l'ancienne valeur ET la nouvelle information.\n");
        sb.append("- Il est strictement interdit de supprimer du contenu utile de la valeur originale.\n");
        sb.append("- Information complémentaire → expression intégrée combinant ancien et nouveau.\n");
        sb.append("- Information contradictoire → état actuel le plus récent, en conservant les détails anciens non contradictoires.\n\n");

        // 7. Notes
        sb.append("## Notes\n");
        sb.append("1. Une feuille est un slot d'attribut destiné à une valeur textuelle.\n");
        sb.append("2. Localiser la feuille la plus spécifique ; exactement une opération par attribut.\n");
        sb.append("3. Utiliser uniquement les 4 types d'opération ci-dessus.\n\n");

        // 8. Path format
        sb.append("## Format des chemins\n");
        sb.append("Chemins séparés par des points, exemple : 1_Biological_Characteristics.Physiological_Status.Age_Related_Characteristics.Chronological_Age\n\n");

        // 9. Value format
        sb.append("## Format des valeurs\n");
        sb.append("En langage naturel, en français, entre guillemets anglais doubles.\n\n");

        // 10. Output format
        sb.append("## Format de sortie\n");
        sb.append("Opérations uniquement, une par ligne, sans explications. Seules les formes suivantes sont permises :\n");
        sb.append("ADD(chemin.complet, \"valeur en français\")\n");
        sb.append("UPDATE(chemin.complet, \"nouvelle valeur fusionnée\")\n");
        sb.append("DELETE(chemin.complet, None)\n");
        sb.append("NO_OP()\n\n");

        // 11. Schema (full JSON tree with current values)
        sb.append("## Schéma PersonaTree\n");
        sb.append(buildSchemaJson());
        sb.append("\n\n");

        // 12. Dialogue
        sb.append("## Dialogue\n");
        for (ConversationPair pair : chunk.pairs()) {
            sb.append("Utilisateur: ").append(pair.userMessage()).append("\n");
            sb.append("Assistant: ").append(pair.assistantMessage()).append("\n");
        }
        sb.append("\n");

        // 13. Trigger
        sb.append("Maintenant, à partir de l'historique de dialogue ci-dessus, génère uniquement les opérations :\n");

        return sb.toString();
    }

    private String buildSchemaJson() {
        try {
            PersonaTree schemaTree = schemaLoader.loadSchema();
            Map<String, String> currentValues = personaTreeGate.getNonEmptyLeaves();

            // Overlay current values onto the schema tree
            for (Map.Entry<String, String> entry : currentValues.entrySet()) {
                setValueInTree(schemaTree, entry.getKey(), entry.getValue());
            }

            return objectMapper.writeValueAsString(schemaTree.getRoots());
        } catch (Exception e) {
            log.error("Failed to build schema JSON, falling back to empty object: {}", e.getMessage());
            return "{}";
        }
    }

    private void setValueInTree(PersonaTree tree, String dotPath, String value) {
        String[] parts = dotPath.split("\\.");
        if (parts.length == 0) return;

        var currentChildren = tree.getRoots();
        for (int i = 0; i < parts.length - 1; i++) {
            var node = currentChildren.get(parts[i]);
            if (node == null || node.isLeaf()) return;
            currentChildren = node.getChildren();
        }

        var leafNode = currentChildren.get(parts[parts.length - 1]);
        if (leafNode != null && leafNode.isLeaf()) {
            leafNode.setValue(value);
        }
    }
}
