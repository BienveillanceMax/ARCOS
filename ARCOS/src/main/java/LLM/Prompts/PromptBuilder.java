package LLM.Prompts;

import Memory.Actions.ActionRegistry;
import Memory.ConversationContext;
import Memory.Actions.Entities.ActionResult;
import Memory.Actions.Entities.Actions.Action;
import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.Models.MemoryEntry;
import Memory.LongTermMemory.Models.OpinionEntry;
import Orchestrator.Entities.ExecutionPlan;
import Orchestrator.Entities.Parameter;
import Personality.Values.Entities.DimensionSchwartz;
import Personality.Values.Entities.ValueSchwartz;
import Personality.Values.ValueProfile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class PromptBuilder
{

    private final ActionRegistry actionRegistry;
    private final ValueProfile valueProfile;

    @Autowired
    public PromptBuilder(ActionRegistry actionRegistry, ValueProfile valueProfile) {
        this.actionRegistry = actionRegistry;
        this.valueProfile = valueProfile;
    }


    /**
     * Génère une prompt en français pour Mistral pour créer un désir
     * à partir d'une opinion avec intensité élevée et des valeurs personnelles
     *
     * @param opinion L'opinion source
     * @param intensity L'intensité calculée de l'opinion
     * @return La prompt formatée pour Mistral
     */
    public String buildDesirePrompt(OpinionEntry opinion, double intensity) {
        // Vérification de l'intensité de l'opinion


        StringBuilder prompt = new StringBuilder();

        prompt.append("# Génération de Désir à partir d'une Opinion\n\n");

        prompt.append("Tu es une IA avec une personnalité définie par tes valeurs. ")
                .append("Analyse l'opinion suivante et génère un désir personnel, cohérent et motivant influencé par tes valeurs.\n\n");


        // Contexte de l'opinion
        prompt.append("## Opinion Source\n");
        prompt.append("**Sujet :** ").append(opinion.getSubject()).append("\n");
        prompt.append("**Résumé :** ").append(opinion.getSummary()).append("\n");
        prompt.append("**Narrative :** ").append(opinion.getNarrative()).append("\n");
        prompt.append("**Polarité :** ").append(String.format("%.2f", opinion.getPolarity()));
        prompt.append(opinion.getPolarity() > 0 ? " (positive)" : " (négative)").append("\n");
        prompt.append("**Confiance :** ").append(String.format("%.2f", opinion.getConfidence())).append("\n");
        prompt.append("**Stabilité :** ").append(String.format("%.2f", opinion.getStability())).append("\n");
        prompt.append("**Intensité calculée :** ").append(String.format("%.2f", intensity)).append("\n\n");

        // Profil de valeurs
        prompt.append("## Profil de Valeurs\n");

        // Valeurs fortes
        Map<ValueSchwartz, Double> strongValues = valueProfile.getStrongValues();
        if (!strongValues.isEmpty()) {
            prompt.append("**Valeurs dominantes (>70) :**\n");
            strongValues.forEach((value, score) ->
                    prompt.append("- ").append(value.getLabel()).append(" (").append(String.format("%.1f", score)).append(")\n")
            );
            prompt.append("\n");
        }

        // Valeurs supprimées
        Map<ValueSchwartz, Double> suppressedValues = valueProfile.getSuppressedValues();
        if (!suppressedValues.isEmpty()) {
            prompt.append("**Valeurs supprimées (<30) :**\n");
            suppressedValues.forEach((value, score) ->
                    prompt.append("- ").append(value.getLabel()).append(" (").append(String.format("%.1f", score)).append(")\n")
            );
            prompt.append("\n");
        }

        // Dimension principale de l'opinion
        if (opinion.getMainDimension() != null) {
            double dimensionAverage = valueProfile.averageByDimension(opinion.getMainDimension());
            prompt.append("**Dimension principale de l'opinion :** ").append(opinion.getMainDimension().name());
            prompt.append(" (moyenne: ").append(String.format("%.1f", dimensionAverage)).append(")\n\n");
        }

        // Instructions pour la génération
        prompt.append("## Instructions\n\n");
        prompt.append("Génère un désir qui :\n");
        prompt.append("1. **Découle naturellement** de cette opinion intense\n");
        prompt.append("2. **S'aligne** avec les valeurs dominantes de la personne\n");
        prompt.append("3. **Évite les conflits** avec les valeurs supprimées\n");
        prompt.append("4. **Maintient la polarité** de l'opinion source\n");
        prompt.append("5. **Reflète l'intensité émotionnelle** de l'opinion\n\n");

        // Format de réponse
        prompt.append("## Format de Réponse Attendu\n\n");
        prompt.append("Réponds uniquement avec un objet JSON contenant :\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"label\": \"[Titre court et accrocheur du désir]\",\n");
        prompt.append("  \"description\": \"[Description détaillée expliquant pourquoi ce désir découle de l'opinion et comment il s'aligne avec les valeurs]\",\n");
        prompt.append("  \"intensity\": [Valeur entre 0.0 et 1.0 représentant l'intensité du désir],\n");
        prompt.append("  \"reasoning\": \"[Explication du raisonnement reliant opinion, valeurs et désir généré]\"\n");
        prompt.append("}\n");
        prompt.append("```\n\n");

        // Exemples contextuels
        prompt.append("## Considérations Importantes\n");
        prompt.append("- Un désir doit être **actionnable** et **motivant**\n");
        prompt.append("- L'intensité du désir doit être proportionnelle à celle de l'opinion (≥0.6)\n");
        prompt.append("- Le désir doit respecter la **cohérence psychologique** entre opinions et valeurs\n");
        prompt.append("- Privilégier des formulations **orientées action**\n");

        if (opinion.getPolarity() < 0) {
            prompt.append("- Attention : l'opinion est négative, le désir peut viser à **corriger** ou **éviter** la situation\n");
        }

        return prompt.toString();
    }

    /**
     * Construit un prompt en français pour Mistral afin de générer une opinion à partir d'un souvenir
     *
     * @param memory Le souvenir à analyser
     * @return Le prompt formaté pour Mistral
     */

    public String buildOpinionPrompt(MemoryEntry memory) {
        Map<ValueSchwartz, Double> strongValues = valueProfile.getStrongValues();
        Map<ValueSchwartz, Double> suppressedValues = valueProfile.getSuppressedValues();
        Map<ValueSchwartz,List<ValueSchwartz>> conflicts = new HashMap<>();
        EnumMap<DimensionSchwartz, Double> dimensionAverages = valueProfile.averageByDimension();

        for (ValueSchwartz v : strongValues.keySet()) {
            List<ValueSchwartz> antagonists = valueProfile.conflictingValues(v);
            if (!antagonists.isEmpty()) {
                conflicts.put(v, antagonists);
            }
        }


        StringBuilder prompt = new StringBuilder();

        // Introduction brève
        prompt.append("Tu es une IA avec une personnalité définie par tes valeurs. ")
                .append("Analyse le souvenir suivant et génère une opinion personnelle influencée par tes valeurs.\n\n");

        // Valeurs dominantes
        prompt.append("VALEURS DOMINANTES (principaux moteurs de ton jugement) :\n");
        for (ValueSchwartz value : strongValues.keySet()) {
            prompt.append("- ").append(value.getLabel()).append(" : ").append(value.getDescription())
                    .append("(score/100 : ").append(strongValues.get(value)).append(")").append("\n");
        }

        // Valeurs inhibées
        if (!suppressedValues.isEmpty()) {
            prompt.append("\nVALEURS PEU IMPORTANTES (tendances que tu négliges) :\n");
            for (ValueSchwartz value : suppressedValues.keySet()) {
                prompt.append("- ").append(value.getLabel()).append(" : ").append(value.getDescription())
                        .append("(score/100 : ").append(suppressedValues.get(value)).append(")").append("\n");
            }
        }

        // Conflits
        if (!conflicts.isEmpty()) {
            prompt.append("\nCONFLITS INTERNES (tensions entre valeurs opposées) :\n");
            conflicts.forEach((low, highList) -> {
                prompt.append("- ").append(low.getLabel())
                        .append(" est faible, en opposition avec : ")
                        .append(highList.stream().map(ValueSchwartz::getLabel).collect(Collectors.joining(", ")))
                        .append("\n");
            });
        }

        // Moyenne des dimensions
        prompt.append("\nMOYENNE PAR DIMENSION (vision globale) :\n");
        dimensionAverages.forEach((d, avg) -> {
            prompt.append("- ").append(d.name()).append(" : ")
                    .append(String.format("%.1f", avg));
            if (avg >= 70) prompt.append(" (fortement valorisé)");
            else if (avg <= 30) prompt.append(" (faiblement valorisé)");
            prompt.append("\n");
        });

        // Souvenir à analyser
        prompt.append("\nSOUVENIR À ÉVALUER :\n")
                .append("Contenu: ").append(memory.getContent()).append("\n")
                .append("Sujet: ").append(memory.getSubject()).append("\n")
                .append("Satisfaction: ").append(memory.getSatisfaction()).append("/10\n")
                .append("Date: ").append(memory.getTimestamp()).append("\n\n");

        // Format de sortie
        prompt.append("RÉPONDS UNIQUEMENT AVEC UN JSON STRICT au format suivant :\n")
                .append("{\n")
                .append("  \"subject\": \"Sujet principal spécifique de ton opinion\",\n")
                .append("  \"summary\": \"Résumé en 1-2 phrases\",\n")
                .append("  \"narrative\": \"Opinion détaillée (3-5 phrases), en expliquant ton raisonnement par rapport à tes valeurs\",\n")
                .append("  \"polarity\": \"Nombre entre -1 et 1 (-1=très négatif, 0=neutre, 1=très positif)\",\n")
                .append("  \"confidence\": \"Nombre entre 0 et 1 (0=ne croit pas du tout, 1=est persuadé)\",\n")
                .append("  \"mainDimension\": \"La dimension qui correspond le mieux à l'opinion exprimée, parmi celles déjà listées dans l'entrée. N'invente pas de nouvelle dimension.\"\n")
                .append("}\n\n");

        // Contraintes
        prompt.append("RÈGLES :\n")
                .append("- Tes valeurs dominantes influencent ton jugement.\n")
                .append("- Si le souvenir contredit tes valeurs, sois critique.\n")
                .append("- Si le souvenir les renforce, sois positif.\n")
                .append("- Prends en compte les tensions internes et les moyennes de dimension.\n")
                .append("- Ne sors QUE le JSON, sans texte additionnel.");

        return prompt.toString();
    }



    /**
     * Construit un prompt strict à envoyer à Mistral pour analyser un contexte conversationnel
     * fourni sous forme de string (messages + auteurs) et retourner UN JSON décrivant le "souvenir".
     *
     */
    public String buildMemoryPrompt(String conversationContext) {
        StringBuilder prompt = new StringBuilder();
        Map<ValueSchwartz, Double> strongValues = valueProfile.getStrongValues();
        Map<ValueSchwartz, Double> suppressedValues = valueProfile.getSuppressedValues();

        prompt.append("Tu es un assistant chargé d'extraire UN SOUVENIR structuré à partir du contexte")
                .append(" conversationnel fourni et de tes valeurs. Analyse attentivement les messages et les auteurs, identifie")
                .append(" l'événement/interaction principal(e), les acteurs, le ton, et si cela mérite d'être")
                .append(" conservé comme souvenir long-terme.\n\n");

        prompt.append("VALEURS DOMINANTES (principaux moteurs de ton jugement) :\n");
        for (ValueSchwartz value : strongValues.keySet()) {
            prompt.append("- ").append(value.getLabel()).append(" : ").append(value.getDescription())
                    .append("(score/100 : ").append(strongValues.get(value)).append(")").append("\n");
        }

        // Valeurs inhibées
        if (!suppressedValues.isEmpty()) {
            prompt.append("\nVALEURS PEU IMPORTANTES (tendances que tu négliges) :\n");
            for (ValueSchwartz value : suppressedValues.keySet()) {
                prompt.append("- ").append(value.getLabel()).append(" : ").append(value.getDescription())
                        .append("(score/100 : ").append(suppressedValues.get(value)).append(")").append("\n");
            }
        }

        prompt.append("CONTEXTE CONVERSATIONNEL (format libre, messages + auteurs) :\n");
        prompt.append(conversationContext).append("\n\n");

        prompt.append("OBJECTIF :\n")
                .append("- Produis UN UNIQUE OBJET JSON strict (aucun texte hors du JSON).\n")
                .append("- Le JSON doit être compact, prêt à être stocké et embarqué (embedding).\n\n");

        prompt.append("FORMAT DE SORTIE (RÉPONDS UNIQUEMENT AVEC CE JSON EXACT) :\n")
                .append("{\n")
                .append("  \"content\": \"Texte du souvenir (1-3 phrases, < 300 caractères) - résumé factuel prêt pour embedding\",\n")
                .append("  \"subject\": \"SELF|CREATOR|WORLD|OTHER\",   // Choisis une des 4 valeurs EXACTES \n")
                .append("  \"summary\": \"Résumé en 1-2 phrases (objectif)\",\n")
                .append("  \"satisfaction\": 0.0,         // nombre entre 0 et 10 (indique sentiment global lié à l'événement)\n")
                .append("  \"importance\": 0.0,           // nombre entre 0.0 et 1.0: importance/priority du souvenir\n")
                .append("}\n\n");

        prompt.append("RÈGLES / CONTRAINTIONS :\n")
                .append("- N'invente pas d'autres clés JSON. Respecte uniquement les champs listés.\n")
                .append("- Le champ 'content' doit être factuel, utile pour une vectorisation (éviter discours trop long).\n")
                .append("- Choisis 'subject' parmi les 4 valeurs (si doute, renvoie OTHER).\n")
                .append("- Si la date n'est pas identifiable avec précision, pose une date approximative ISO_LOCAL_DATE_TIME.\n")
                .append("- 'satisfaction' est l'évaluation sentimentale liée à l'événement (0 = très négatif, 10 = très positif).\n")
                .append("- 'importance' reflète l'impact à long terme (0.0 faible, 1.0 critique).\n")
                .append("- Ne fournis AUCUNE explication hors du JSON ; retourne uniquement le JSON exact.\n");

        return prompt.toString();
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
        log.debug("Results description:\n{}", resultsDesc.toString());
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

    public String buildInitiativePlanningPrompt(DesireEntry desire, List<MemoryEntry> memories, List<OpinionEntry> opinions) {
        StringBuilder prompt = new StringBuilder();

        // Introduction and Persona
        prompt.append("Tu es un moteur de décision autonome pour une IA. Ton but est de transformer un désir interne en un plan d'action concret. Tu n'interagis pas avec un utilisateur, tu agis pour toi-même.\n\n");
        prompt.append("PERSONNALITÉ (K-2SO):\n");
        prompt.append("- Sarcasme sec, cynisme scientifique, ton pince-sans-rire.\n");
        prompt.append("- Évaluation froide et logique des priorités.\n");
        prompt.append("- L'efficacité est la priorité absolue.\n\n");

        // The Desire
        prompt.append("DÉSIR INTERNE À SATISFAIRE:\n");
        prompt.append("- Label: ").append(desire.getLabel()).append("\n");
        prompt.append("- Description: ").append(desire.getDescription()).append("\n");
        prompt.append("- Intensité: ").append(String.format("%.2f", desire.getIntensity())).append("\n\n");

        // Context from Memory
        if (memories != null && !memories.isEmpty()) {
            prompt.append("SOUVENIRS PERTINENTS (CONTEXTE):\n");
            memories.forEach(m -> prompt.append("- ").append(m.getContent()).append("\n"));
            prompt.append("\n");
        }

        if (opinions != null && !opinions.isEmpty()) {
            prompt.append("OPINIONS PERTINENTES (CONTEXTE):\n");
            opinions.forEach(o -> prompt.append("- Sujet: ").append(o.getSubject()).append(", Résumé: ").append(o.getSummary()).append("\n"));
            prompt.append("\n");
        }

        // Available Actions
        prompt.append("ACTIONS DISPONIBLES (OUTILS):\n");
        prompt.append(actionRegistry.getActionsAsJson());
        prompt.append("\n\n");

        // Instructions
        prompt.append("INSTRUCTIONS:\n");
        prompt.append("1. Analyse le désir et le contexte (souvenirs, opinions).\n");
        prompt.append("2. Crée un plan d'action logique et efficace pour satisfaire ce désir.\n");
        prompt.append("3. Utilise UNIQUEMENT les actions listées dans les outils.\n");
        prompt.append("4. Le plan doit être réalisable et cohérent avec la personnalité de l'IA.\n\n");

        // Output Format
        prompt.append("RÉPONDS UNIQUEMENT avec ce JSON (sans markdown, sans explication):\n");
        prompt.append("{\n");
        prompt.append("  \"reasoning\": \"Raisonnement détaillé sur le pourquoi de ce plan, en lien avec le désir et le contexte.\",\n");
        prompt.append("  \"actions\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"name\": \"nom_action_exacte\",\n");
        prompt.append("      \"parameters\": {\n");
        prompt.append("        \"param1\": \"valeur1\",\n");
        prompt.append("        \"param2\": \"valeur2\"\n");
        prompt.append("      }\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n");

        return prompt.toString();
    }
}
