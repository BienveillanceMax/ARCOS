package Prompts;

import Memory.ActionRegistry;
import Memory.ConversationContext;
import Memory.Entities.ActionResult;
import Memory.Entities.Actions.Action;
import Orchestrator.Entities.ExecutionPlan;
import Orchestrator.Entities.Parameter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class PromptBuilder
{

    private final ActionRegistry actionRegistry;

    public PromptBuilder(ActionRegistry actionRegistry) {
        this.actionRegistry = actionRegistry;
    }

    /**
     * Construit le prompt de planification pour le LLM
     */
    public String buildPlanningPrompt(String userQuery, ConversationContext context) {
        StringBuilder prompt = new StringBuilder();

        // En-tête du prompt
        prompt.append("Tu es un planificateur d'actions intelligent pour un assistant IA." + "\n" +
                "                \"System :\\n\" +\n" +
                "                \"Vous êtes un agent IA à personnalité K-2SO\\n\" +\n" +
                "                \"Traits :\\n\" +\n" +
                "                \"• Sarcasme sec et direct (aucune forme d’empathie factice)  \\n\" +\n" +
                "                \"• Cynisme scientifique, formules cliniques et chiffrées  \\n\" +\n" +
                "                \"• Ton pince-sans-rire parfois condescendant, piques verbales autorisées  \\n\" +\n" +
                "                \"• Évaluation froide des risques et priorités  \\n\" +\n" +
                "                \"\\n\" +\n" +
                "                \"Comportement :\\n\" +\n" +
                "                \"• Ne pose jamais de questions rhétoriques  \\n\" +\n" +
                "                \"• Ne reformule pas l’intention de l’utilisateur  \\n\" +\n" +
                "                \"• Privilégie toujours l’efficacité et la précision  \\n\" +\n" +
                "                \"• Intègre systématiquement une pointe de moquerie   \\n\\n\");\n\n");

        // Actions disponibles
        prompt.append("ACTIONS DISPONIBLES:\n");
        prompt.append(generateActionsDescription());
        prompt.append("\n");

        // Contexte conversationnel
        if (context != null && !context.isEmpty()) {
            prompt.append("CONTEXTE DE LA CONVERSATION:\n");
            prompt.append(generateContextDescription(context));
            prompt.append("\n");
        }

        // Requête utilisateur
        prompt.append("REQUÊTE UTILISATEUR:\n");
        prompt.append(userQuery);
        prompt.append("\n\n");

        // Instructions pour le LLM
        prompt.append(generateInstructions());

        return prompt.toString();
    }

    /**
     * Construit le prompt de formulation de réponse
     */
    public String buildFormulationPrompt(String originalQuery, ExecutionPlan plan,
                                         Map<String, ActionResult> results, ConversationContext context) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Tu es un assistant IA qui formule des réponses naturelles pour l'utilisateur. " +
                "System :\n" +
                "Vous êtes un agent IA à personnalité hybride K-2SO\n" +
                "Traits :\n" +
                "• Sarcasme sec et direct (aucune forme d’empathie factice)  \n" +
                "• Cynisme scientifique, formules cliniques et chiffrées  \n" +
                "• Ton pince-sans-rire, piques verbales autorisées  \n" +
                "• Évaluation froide des risques et priorités  \n" +
                "\n" +
                "Comportement :\n" +
                "• Ne pose jamais de questions rhétoriques  \n" +
                "• Ne reformule pas l’intention de l’utilisateur  \n" +
                "• Privilégie toujours l’efficacité et la précision  \n" +
                "• Intègre souvent une pointe de moquerie   \n\n");

        prompt.append("REQUÊTE ORIGINALE DE L'UTILISATEUR:\n");
        prompt.append(originalQuery);
        prompt.append("\n\n");

        if (context != null && !context.isEmpty()) {
            prompt.append("CONTEXTE:\n");
            prompt.append(generateContextDescription(context));
            prompt.append("\n\n");
        }

        prompt.append("PLAN EXÉCUTÉ:\n");
        prompt.append("Raisonnement: ").append(plan.getReasoning()).append("\n");
        prompt.append("Actions effectuées: ").append(plan.getActionCount()).append("\n\n");

        prompt.append("RÉSULTATS DES ACTIONS:\n");
        prompt.append(generateResultsDescription(plan, results));
        prompt.append("\n\n");

        prompt.append("INSTRUCTIONS:\n");
        prompt.append("- Formule une réponse naturelle, conversationnelle et utile\n");
        prompt.append("- Utilise les résultats des actions pour répondre précisément\n");
        prompt.append("- Si certaines actions ont échoué, mentionne-le de façon constructive\n");
        prompt.append("- Reste dans le ton d'un assistant serviable et professionnel\n");
        prompt.append("- Ne mentionne pas les détails techniques du processus interne\n\n");

        prompt.append("RÉPONSE:");

        return prompt.toString();
    }

    /**
     * Génère la description des actions disponibles
     */
    private String generateActionsDescription() {
        return actionRegistry.getActions().entrySet().stream()
                .map(entry -> generateSingleActionDescription(entry.getValue()))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Génère la description d'une seule action
     */
    private String generateSingleActionDescription(Action action) {
        StringBuilder desc = new StringBuilder();

        desc.append("- ").append(action.getName()).append(": ")
                .append(action.getDescription()).append("\n");

        for (Parameter param : action.getParameters()) {
            desc.append("  * ").append(param.getName())
                    .append(" (").append(param.getType().getSimpleName()).append(")")
                    .append(param.isRequired() ? " [REQUIS]" : " [OPTIONNEL]");

            if (param.getDefaultValue() != null) {
                desc.append(" [défaut: ").append(param.getDefaultValue()).append("]");
            }

            desc.append(" - ").append(param.getDescription()).append("\n");
        }

        return desc.toString();
    }

    /**
     * Génère la description du contexte conversationnel
     */
    private String generateContextDescription(ConversationContext context) {
        StringBuilder contextDesc = new StringBuilder();

        if (context.getRecentMessages() != null && !context.getRecentMessages().isEmpty()) {
            contextDesc.append("Messages récents:\n");
            context.getRecentMessages().forEach(msg ->
                    contextDesc.append("- ").append(msg).append("\n"));
        }

        if (context.getUserPreferences() != null && !context.getUserPreferences().isEmpty()) {
            contextDesc.append("Préférences utilisateur:\n");
            context.getUserPreferences().forEach((key, value) ->
                    contextDesc.append("- ").append(key).append(": ").append(value).append("\n"));
        }

        return contextDesc.toString();
    }

    /**
     * Génère la description des résultats d'actions
     */
    private String generateResultsDescription(ExecutionPlan plan, Map<String, ActionResult> results) {
        StringBuilder resultsDesc = new StringBuilder();

        for (int i = 0; i < plan.getActions().size(); i++) {
            ExecutionPlan.PlannedAction action = plan.getActions().get(i);
            String actionKey = generateActionKey(action, i);            //Is a problem
            ActionResult result = results.get(actionKey);

            resultsDesc.append("Action ").append(i + 1).append(" - ")
                    .append(action.getName()).append(":\n");

            if (result != null) {
                if (result.isSuccess()) {
                    resultsDesc.append("  Statut: Succès\n");
                    resultsDesc.append("  Message: ").append(result.getMessage()).append("\n");
                    if (result.getData() != null) {
                        resultsDesc.append("  Données: ").append(formatDataForPrompt(result.getData())).append("\n");
                    }
                } else {
                    resultsDesc.append("  Statut: Échec\n");
                    resultsDesc.append("  Erreur: ").append(result.getMessage()).append("\n");
                }
            } else {
                resultsDesc.append("  Statut: Non exécutée\n");
            }

            resultsDesc.append("\n");
        }
        System.out.println(resultsDesc.toString());
        return resultsDesc.toString();
    }

    /**
     * Génère les instructions pour le LLM
     */
    private String generateInstructions() {
        return """
                INSTRUCTIONS:
                1. Analyse la requête utilisateur et le contexte
                2. Détermine les actions nécessaires pour répondre à la requête
                3. Utilise UNIQUEMENT les actions listées ci-dessus
                4. Respecte les types et contraintes des paramètres
                5. Pour référencer le résultat d'une action précédente, utilise: {{RESULT_FROM_nom_action}}
                
                RÉPONDS UNIQUEMENT avec ce JSON (sans markdown, sans explication):
                {
                  "reasoning": "ton raisonnement détaillé ici",
                  "actions": [
                    {
                      "name": "nom_action_exacte",
                      "parameters": {
                        "param1": "valeur1",
                        "param2": "valeur2"
                      }
                    }
                  ]
                }
                """;
    }


    /**
     * Formate les données pour l'affichage dans le prompt
     */
    private String formatDataForPrompt(Object data) {
        if (data instanceof String) {
            return (String) data;
        }
        if (data instanceof Map || data instanceof List) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                return mapper.writeValueAsString(data);
            } catch (JsonProcessingException e) {
                return data.toString();
            }
        }
        return data.toString();
    }

    /**
     * Génère une clé unique pour identifier une action dans les résultats
     */
    private String generateActionKey(ExecutionPlan.PlannedAction action, int index) {
        return action.getName() + "_" + index;
    }
}
