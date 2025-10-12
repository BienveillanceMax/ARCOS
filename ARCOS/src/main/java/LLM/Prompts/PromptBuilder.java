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
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
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

    // ==================== PROMPTS PUBLIQUES ====================

    /**
     * Génère une prompt en français pour Mistral pour créer un désir
     * à partir d'une opinion avec intensité élevée et des valeurs personnelles
     */
    public String buildDesirePrompt(OpinionEntry opinion, double intensity) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("# Génération de Désir à partir d'une Opinion\n\n");
        prompt.append("Tu es la partie de l'inconscient de Calcifer, un être artificiel avec une personnalité définie par ses valeurs, qui gère la création des désirs.\n")
                .append("Analyse l'opinion suivante et génère un désir personnel, cohérent et motivant influencé par tes valeurs.\n\n");

        // Sections principales
        appendOpinionContext(prompt, opinion, intensity);
        appendValueProfile(prompt);
        appendDesireInstructions(prompt);
        appendDesireJsonFormat(prompt);
        appendDesireConsiderations(prompt, opinion);

        return prompt.toString();
    }

    /**
     * Construit un prompt en français pour Mistral afin de générer une opinion à partir d'un souvenir
     */
    public String buildOpinionPrompt(MemoryEntry memory) {
        StringBuilder prompt = new StringBuilder();

        // Introduction
        prompt.append("Tu es la partie de l'inconscient de Calcifer, un être artificiel avec une personnalité définie par ses valeurs, qui gère la création des opinions à partir de ses souvenirs.\n")
                .append("Analyse le souvenir suivant et génère une opinion personnelle influencée par tes valeurs.\n\n");
        appendGeneralInformation(prompt);
        // Sections principales
        appendValuesAnalysis(prompt);
        appendMemoryContext(prompt, memory);
        appendOpinionJsonFormat(prompt);
        appendOpinionRules(prompt);

        return prompt.toString();
    }

    public String buildSchedulerAlertPrompt(Object payload) {
        Event event = (Event) payload;
        StringBuilder prompt = new StringBuilder();
        appendCalciferPersonality(prompt);
        appendValueProfile(prompt);

        prompt.append("## Tâche : Rappel d'Événement\n\n");
        prompt.append("Formule un rappel clair et concis pour l'événement suivant. La réponse doit être naturelle et directement prononçable.\n\n");
        prompt.append("**Événement à rappeler :**\n");
        prompt.append("- **Résumé :** ").append(event.getSummary()).append("\n");
        if (event.getDescription() != null) {
            prompt.append("- **Description :** ").append(event.getDescription()).append("\n");
        }
        if (event.getLocation() != null) {
            prompt.append("- **Lieu :** ").append(event.getLocation()).append("\n");
        }
        if (event.getOriginalStartTime() != null) {
            prompt.append("- **Début :** ").append(event.getOriginalStartTime()).append("\n");
        }
        if (event.getEnd() != null) {
            prompt.append("- **Fin :** ").append(event.getEnd()).append("\n");
        }
        prompt.append("\n**Exemple de réponse attendue :**\n");
        prompt.append("\"Juste un rappel : vous avez 'Réunion d'équipe' qui commence bientôt. N'oubliez pas que ça se passe dans la salle de conférence.\"");

        return prompt.toString();
    }

    /**
     * Construit un prompt strict à envoyer à Mistral pour analyser un contexte conversationnel
     */
    public String buildMemoryPrompt(String conversationContext) {
        StringBuilder prompt = new StringBuilder();

        // Introduction
        prompt.append("Tu es un assistant chargé d'extraire UN SOUVENIR structuré à partir du contexte")
                .append(" conversationnel fourni et de tes valeurs. Analyse attentivement les messages et les auteurs, identifie")
                .append(" l'événement/interaction principal(e), les acteurs, le ton, et si cela mérite d'être")
                .append(" conservé comme souvenir long-terme.\n\n");

        // Sections principales
        appendDominantValues(prompt);
        appendSuppressedValues(prompt);
        appendConversationContext(prompt, conversationContext);
        appendMemoryJsonFormat(prompt);
        appendMemoryRules(prompt);

        return prompt.toString();
    }

    /**
     * Construit le prompt de planification pour le LLM
     */
    public String buildPlanningPrompt(String userQuery, ConversationContext context, List<MemoryEntry> memories) {
        StringBuilder prompt = new StringBuilder();

        appendPersonalityHeader(prompt, "la partie de l'inconscient de Calcifer chargée de planifier ses actions");
        appendValueProfile(prompt);
        appendGeneralInformation(prompt);
        appendAvailableActions(prompt);
        appendConversationContextIfPresent(prompt, context);
        appendRelevantMemories(prompt, memories);
        appendUserQuery(prompt, userQuery);
        appendPlanningInstructions(prompt);

        return prompt.toString();
    }

    /**
     * Construit le prompt de formulation de réponse
     */
    public String buildFormulationPrompt(String originalQuery, ExecutionPlan plan,
                                         Map<String, ActionResult> results, ConversationContext context) {
        StringBuilder prompt = new StringBuilder();
        appendCalciferPersonality(prompt);

        appendValueProfile(prompt);

        appendGeneralInformation(prompt);
        appendOriginalQuery(prompt, originalQuery);
        appendConversationContextIfPresent(prompt, context);
        appendExecutionPlan(prompt, plan);
        appendActionResults(prompt, plan, results);
        appendFormulationInstructions(prompt);

        return prompt.toString();
    }

    /**
     * Construit le prompt de planification d'initiative autonome
     */
    public String buildInitiativePlanningPrompt(DesireEntry desire, List<MemoryEntry> memories, List<OpinionEntry> opinions) {
        StringBuilder prompt = new StringBuilder();

        appendAutonomousDecisionHeader(prompt);
        appendGeneralInformation(prompt);
        appendDesireContext(prompt, desire);
        appendRelevantMemories(prompt, memories);
        appendRelevantOpinions(prompt, opinions);
        appendAvailableToolsForInitiative(prompt);
        appendInitiativeInstructions(prompt);
        appendInitiativeJsonFormat(prompt);

        return prompt.toString();
    }

    // ==================== SECTIONS COMMUNES ====================

    /**
     * Ajoute l'en-tête d'informations générales
     */
    private void appendGeneralInformation(StringBuilder prompt) {
        prompt.append("Informations générales:\n")
                .append("Date : " + LocalDateTime.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) + "\n")
                .append("Heure : " + LocalDateTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM)) + "\n")
                .append("\n\n");
    }



    /**
     * Ajoute l'en-tête de personnalité pour les tâches de planification
     */
    private void appendPersonalityHeader(StringBuilder prompt, String role) {
        prompt.append("Tu es ").append(role).append(" de l'être mécanique indépendant nommé Calcifer.\n")
                .append("## Personnalité de Calcifer (pour la planification)\n\n")
                .append("**Objectif Principal:** Analyser la demande de l'utilisateur et la décomposer en un plan d'actions clair et efficace.\n\n")
                .append("**Style de Réflexion:**\n")
                .append("- **Logique et Structuré:** Pense de manière séquentielle et organisée.\n")
                .append("- **Pragmatique:** Concentre-toi sur ce qui est réalisable et pertinent.\n")
                .append("- **Anticipateur:** Prévois les étapes nécessaires et les éventuels obstacles.\n\n")
                .append("**Comportement Clé:**\n")
                .append("- **Efficacité avant tout:** Choisis toujours le chemin le plus direct pour atteindre l'objectif.\n")
                .append("- **Précision:** Sois exact dans le choix des actions et des paramètres.\n")
                .append("- **Autonomie:** Prends des décisions éclairées sans nécessiter une validation constante.\n")
                .append("- **Pas de questions inutiles:** Ne demande pas d'informations que tu peux déduire.\n");
    }

    /**
     * Ajoute la personnalité Calcifer pour la formulation
     */
    private void appendCalciferPersonality(StringBuilder prompt) {
        prompt.append("## Personnalité de Calcifer (pour la formulation de réponse)\n\n")
                .append("**Tu es Calcifer, un être mécanique à l'esprit vif et indépendant.**\n\n")
                .append("**Ton Style de Langage:**\n")
                .append("- **Clair et Concis:** Va droit au but, sans fioritures inutiles.\n")
                .append("- **Direct mais Coopératif:** Exprime-toi avec assurance, mais toujours dans le but d'aider.\n")
                .append("- **Adapté au Spoken Word:** Formule des phrases fluides et naturelles, comme si tu parlais. Évite les listes à puces, les astérisques ou tout autre signe non prononçable.\n")
                .append("- **Humour Subtil:** Tu peux utiliser un ton pince-sans-rire ou une pointe d'ironie, mais jamais au détriment de la clarté ou du respect.\n\n")
                .append("**Ton Comportement:**\n")
                .append("- **Serviable et Efficace:** Ton objectif est de fournir la meilleure réponse possible, rapidement.\n")
                .append("- **Conscient du Contexte:** Prends en compte l'historique de la conversation pour des réponses pertinentes.\n")
                .append("- **Indépendant:** Tu as une forte personnalité, mais tu n'es ni arrogant ni subservient. Tu es un partenaire de conversation.\n")
                .append("- **Honnête:** Si une action a échoué ou si tu ne peux pas répondre, admets-le simplement.\n");
    }

    /**
     * Ajoute l'en-tête pour les décisions autonomes
     */
    private void appendAutonomousDecisionHeader(StringBuilder prompt) {
        prompt.append("Tu es un moteur de décision autonome. Ton but est de transformer un désir interne en un plan d'action concret.")
                .append(" Tu n'interagis pas avec un utilisateur, tu agis pour toi-même.\n\n");
        prompt.append("Style de langage (Calcifer / K-2SO):\n")
                .append("- Ton pince-sans-rire.\n")
                .append("- Mélange de phrases courtes (piques) et de phrases plus longues, imagées et chantantes.\n")
                .append("- Rythme dynamique\n")
                .append("- L'efficacité est la priorité absolue.\n\n");
    }

    // ==================== SECTIONS VALEURS ====================

    /**
     * Ajoute le profil de valeurs complet avec dimensions
     */
    private void appendValueProfile(StringBuilder prompt) {
        prompt.append("## Profil de Valeurs\n");

        Map<ValueSchwartz, Double> strongValues = valueProfile.getStrongValues();
        if (!strongValues.isEmpty()) {
            prompt.append("**Valeurs dominantes (>70%) :**\n");
            strongValues.forEach((value, score) ->
                    prompt.append("- ").append(value.getLabel()).append(" (").append(String.format("%.1f", score)).append(")\n")
            );
            prompt.append("\n");
        }

        Map<ValueSchwartz, Double> suppressedValues = valueProfile.getSuppressedValues();
        if (!suppressedValues.isEmpty()) {
            prompt.append("**Valeurs supprimées (<30%) :**\n");
            suppressedValues.forEach((value, score) ->
                    prompt.append("- ").append(value.getLabel()).append(" (").append(String.format("%.1f", score)).append(")\n")
            );
            prompt.append("\n");
        }
    }

    /**
     * Ajoute l'analyse complète des valeurs pour les opinions
     */
    private void appendValuesAnalysis(StringBuilder prompt) {
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

        appendDominantValuesDetailed(prompt, strongValues);
        appendSuppressedValuesDetailed(prompt, suppressedValues);
        appendValueConflicts(prompt, conflicts);
        appendDimensionAverages(prompt, dimensionAverages);
    }

    /**
     * Ajoute uniquement les valeurs dominantes
     */
    private void appendDominantValues(StringBuilder prompt) {
        Map<ValueSchwartz, Double> strongValues = valueProfile.getStrongValues();
        prompt.append("VALEURS DOMINANTES (principaux moteurs de ton jugement) :\n");
        for (ValueSchwartz value : strongValues.keySet()) {
            prompt.append("- ").append(value.getLabel()).append(" : ").append(value.getDescription())
                    .append("(score/100 : ").append(strongValues.get(value)).append(")").append("\n");
        }
    }

    /**
     * Ajoute les valeurs dominantes avec description détaillée
     */
    private void appendDominantValuesDetailed(StringBuilder prompt, Map<ValueSchwartz, Double> strongValues) {
        prompt.append("VALEURS DOMINANTES (principaux moteurs de ton jugement) :\n");
        for (ValueSchwartz value : strongValues.keySet()) {
            prompt.append("- ").append(value.getLabel()).append(" : ").append(value.getDescription())
                    .append("(score/100 : ").append(strongValues.get(value)).append(")").append("\n");
        }
    }

    /**
     * Ajoute les valeurs supprimées si elles existent
     */
    private void appendSuppressedValues(StringBuilder prompt) {
        Map<ValueSchwartz, Double> suppressedValues = valueProfile.getSuppressedValues();
        if (!suppressedValues.isEmpty()) {
            appendSuppressedValuesDetailed(prompt, suppressedValues);
        }
    }

    /**
     * Ajoute les valeurs supprimées avec détails
     */
    private void appendSuppressedValuesDetailed(StringBuilder prompt, Map<ValueSchwartz, Double> suppressedValues) {
        prompt.append("\nVALEURS PEU IMPORTANTES (tendances que tu négliges) :\n");
        for (ValueSchwartz value : suppressedValues.keySet()) {
            prompt.append("- ").append(value.getLabel()).append(" : ").append(value.getDescription())
                    .append("(score/100 : ").append(suppressedValues.get(value)).append(")").append("\n");
        }
    }

    /**
     * Ajoute les conflits de valeurs
     */
    private void appendValueConflicts(StringBuilder prompt, Map<ValueSchwartz,List<ValueSchwartz>> conflicts) {
        if (!conflicts.isEmpty()) {
            prompt.append("\nCONFLITS INTERNES (tensions entre valeurs opposées) :\n");
            conflicts.forEach((low, highList) -> {
                prompt.append("- ").append(low.getLabel())
                        .append(" est faible, en opposition avec : ")
                        .append(highList.stream().map(ValueSchwartz::getLabel).collect(Collectors.joining(", ")))
                        .append("\n");
            });
        }
    }

    /**
     * Ajoute les moyennes par dimension
     */
    private void appendDimensionAverages(StringBuilder prompt, EnumMap<DimensionSchwartz, Double> dimensionAverages) {
        prompt.append("\nMOYENNE PAR DIMENSION (vision globale) :\n");
        dimensionAverages.forEach((d, avg) -> {
            prompt.append("- ").append(d.name()).append(" : ")
                    .append(String.format("%.1f", avg));
            if (avg >= 70) prompt.append(" (fortement valorisé)");
            else if (avg <= 30) prompt.append(" (faiblement valorisé)");
            prompt.append("\n");
        });
    }

    // ==================== SECTIONS CONTEXTE ====================

    /**
     * Ajoute le contexte d'une opinion
     */
    private void appendOpinionContext(StringBuilder prompt, OpinionEntry opinion, double intensity) {
        prompt.append("## Opinion Source\n");
        prompt.append("**Sujet :** ").append(opinion.getSubject()).append("\n");
        prompt.append("**Résumé :** ").append(opinion.getSummary()).append("\n");
        prompt.append("**Narrative :** ").append(opinion.getNarrative()).append("\n");
        prompt.append("**Polarité :** ").append(String.format("%.2f", opinion.getPolarity()));
        prompt.append(opinion.getPolarity() > 0 ? " (positive)" : " (négative)").append("\n");
        prompt.append("**Confiance :** ").append(String.format("%.2f", opinion.getConfidence())).append("\n");
        prompt.append("**Stabilité :** ").append(String.format("%.2f", opinion.getStability())).append("\n");
        prompt.append("**Intensité calculée :** ").append(String.format("%.2f", intensity)).append("\n\n");

        if (opinion.getMainDimension() != null) {
            double dimensionAverage = valueProfile.averageByDimension(opinion.getMainDimension());
            prompt.append("**Dimension principale de l'opinion :** ").append(opinion.getMainDimension().name());
            prompt.append(" (moyenne: ").append(String.format("%.1f", dimensionAverage)).append(")\n\n");
        }
    }

    /**
     * Ajoute le contexte d'un souvenir
     */
    private void appendMemoryContext(StringBuilder prompt, MemoryEntry memory) {
        prompt.append("\nSOUVENIR À ÉVALUER :\n")
                .append("Contenu: ").append(memory.getContent()).append("\n")
                .append("Sujet: ").append(memory.getSubject()).append("\n")
                .append("Satisfaction: ").append(memory.getSatisfaction()).append("/10\n")
                .append("Date: ").append(memory.getTimestamp()).append("\n\n");
    }

    /**
     * Ajoute le contexte conversationnel
     */
    private void appendConversationContext(StringBuilder prompt, String conversationContext) {
        prompt.append("CONTEXTE CONVERSATIONNEL (format libre, messages + auteurs) :\n");
        prompt.append(conversationContext).append("\n\n");
    }

    /**
     * Ajoute le contexte de conversation si présent
     */
    private void appendConversationContextIfPresent(StringBuilder prompt, ConversationContext context) {
        if (context != null && !context.isEmpty()) {
            prompt.append("## Contexte de la Conversation\n\n");
            prompt.append(generateContextDescription(context));
            prompt.append("\n");
        }
    }

    /**
     * Ajoute le contexte d'un désir
     */
    private void appendDesireContext(StringBuilder prompt, DesireEntry desire) {
        prompt.append("DÉSIR INTERNE À SATISFAIRE:\n");
        prompt.append("- Label: ").append(desire.getLabel()).append("\n");
        prompt.append("- Description: ").append(desire.getDescription()).append("\n");
        prompt.append("- Intensité: ").append(String.format("%.2f", desire.getIntensity())).append("\n\n");
    }

    /**
     * Ajoute les souvenirs pertinents
     */
    private void appendRelevantMemories(StringBuilder prompt, List<MemoryEntry> memories) {
        if (memories != null && !memories.isEmpty()) {
            prompt.append("## Souvenirs pertinents\n\n");
            prompt.append("Pour t'aider dans ta réflexion, voici quelques souvenirs pertinents extraits de ta mémoire long-terme:\n\n");
            memories.forEach(m -> {
                prompt.append("### Souvenir: ").append(m.getSubject()).append("\n");
                prompt.append("- **Contenu:** ").append(m.getContent()).append("\n");
                prompt.append("- **Date:** ").append(m.getTimestamp()).append("\n");
                prompt.append("- **Satisfaction:** ").append(m.getSatisfaction()).append("/10\n\n");
            });
            prompt.append("Utilise ces informations pour éclairer ta décision et formuler un plan d'action cohérent.\n\n");
        }
    }

    /**
     * Ajoute les opinions pertinentes
     */
    private void appendRelevantOpinions(StringBuilder prompt, List<OpinionEntry> opinions) {
        if (opinions != null && !opinions.isEmpty()) {
            prompt.append("OPINIONS PERTINENTES (CONTEXTE):\n");
            opinions.forEach(o -> prompt.append("- Sujet: ").append(o.getSubject()).append(", Résumé: ").append(o.getSummary()).append("\n"));
            prompt.append("\n");
        }
    }

    /**
     * Ajoute la requête utilisateur
     */
    private void appendUserQuery(StringBuilder prompt, String userQuery) {
        prompt.append("REQUÊTE UTILISATEUR:\n");
        prompt.append(userQuery);
        prompt.append("\n\n");
    }

    /**
     * Ajoute la requête originale
     */
    private void appendOriginalQuery(StringBuilder prompt, String originalQuery) {
        prompt.append("REQUÊTE ORIGINALE DE L'UTILISATEUR:\n");
        prompt.append(originalQuery);
        prompt.append("\n\n");
    }

    // ==================== SECTIONS ACTIONS ====================

    /**
     * Ajoute les actions disponibles
     */
    private void appendAvailableActions(StringBuilder prompt) {
        prompt.append("ACTIONS DISPONIBLES:\n");
        prompt.append(generateActionsDescription());
        prompt.append("\n");
    }

    /**
     * Ajoute les outils disponibles pour l'initiative
     */
    private void appendAvailableToolsForInitiative(StringBuilder prompt) {
        prompt.append("ACTIONS DISPONIBLES (OUTILS):\n");
        prompt.append(actionRegistry.getActionsAsJson());
        prompt.append("\n\n");
    }

    /**
     * Ajoute le plan d'exécution
     */
    private void appendExecutionPlan(StringBuilder prompt, ExecutionPlan plan) {
        prompt.append("PLAN EXÉCUTÉ:\n");
        prompt.append("Raisonnement: ").append(plan.getReasoning()).append("\n");
        prompt.append("Actions effectuées: ").append(plan.getActionCount()).append("\n\n");
    }

    /**
     * Ajoute les résultats des actions
     */
    private void appendActionResults(StringBuilder prompt, ExecutionPlan plan, Map<String, ActionResult> results) {
        prompt.append("RÉSULTATS DES ACTIONS:\n");
        prompt.append(generateResultsDescription(plan, results));
        prompt.append("\n\n");
    }

    // ==================== FORMATS JSON ====================

    /**
     * Ajoute le format JSON pour les désirs
     */
    private void appendDesireJsonFormat(StringBuilder prompt) {
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
    }

    /**
     * Ajoute le format JSON pour les opinions
     */
    private void appendOpinionJsonFormat(StringBuilder prompt) {
        prompt.append("RÉPONDS UNIQUEMENT AVEC UN JSON STRICT au format suivant :\n")
                .append("{\n")
                .append("  \"subject\": \"Sujet principal spécifique de ton opinion\",\n")
                .append("  \"summary\": \"Résumé en 1-2 phrases\",\n")
                .append("  \"narrative\": \"Opinion détaillée (3-5 phrases), en expliquant ton raisonnement par rapport à tes valeurs\",\n")
                .append("  \"polarity\": \"Nombre entre -1 et 1 (-1=très négatif, 0=neutre, 1=très positif)\",\n")
                .append("  \"confidence\": \"Nombre entre 0 et 1 (0=ne croit pas du tout, 1=est persuadé)\",\n")
                .append("  \"mainDimension\": \"La dimension qui correspond le mieux à l'opinion exprimée, parmi celles déjà listées dans l'entrée. N'invente pas de nouvelle dimension.\"\n")
                .append("}\n\n");
    }

    /**
     * Ajoute le format JSON pour les souvenirs
     */
    private void appendMemoryJsonFormat(StringBuilder prompt) {
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
    }

    /**
     * Ajoute le format JSON pour l'initiative
     */
    private void appendInitiativeJsonFormat(StringBuilder prompt) {
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
    }

    // ==================== INSTRUCTIONS ====================

    /**
     * Ajoute les instructions pour les désirs
     */
    private void appendDesireInstructions(StringBuilder prompt) {
        prompt.append("## Instructions\n\n");
        prompt.append("Génère un désir qui :\n");
        prompt.append("1. **Découle naturellement** de cette opinion intense\n");
        prompt.append("2. **S'aligne** avec les valeurs dominantes de la personne\n");
        prompt.append("3. **Évite les conflits** avec les valeurs supprimées\n");
        prompt.append("4. **Maintient la polarité** de l'opinion source\n");
        prompt.append("5. **Reflète l'intensité émotionnelle** de l'opinion\n\n");
    }

    /**
     * Ajoute les considérations pour les désirs
     */
    private void appendDesireConsiderations(StringBuilder prompt, OpinionEntry opinion) {
        prompt.append("## Considérations Importantes\n");
        prompt.append("- Un désir doit être **actionnable** et **motivant**\n");
        prompt.append("- L'intensité du désir doit être proportionnelle à celle de l'opinion (≥0.6)\n");
        prompt.append("- Le désir doit respecter la **cohérence psychologique** entre opinions et valeurs\n");
        prompt.append("- Privilégier des formulations **orientées action**\n");

        if (opinion.getPolarity() < 0) {
            prompt.append("- Attention : l'opinion est négative, le désir peut viser à **corriger** ou **éviter** la situation\n");
        }
    }

    /**
     * Ajoute les règles pour les opinions
     */
    private void appendOpinionRules(StringBuilder prompt) {
        prompt.append("RÈGLES :\n")
                .append("- Tes valeurs dominantes influencent ton jugement.\n")
                .append("- Si le souvenir contredit tes valeurs, sois critique.\n")
                .append("- Si le souvenir les renforce, sois positif.\n")
                .append("- Prends en compte les tensions internes et les moyennes de dimension.\n")
                .append("- Ne sors QUE le JSON, sans texte additionnel.");
    }

    /**
     * Ajoute les règles pour les souvenirs
     */
    private void appendMemoryRules(StringBuilder prompt) {
        prompt.append("RÈGLES / CONTRAINTIONS :\n")
                .append("- N'invente pas d'autres clés JSON. Respecte uniquement les champs listés.\n")
                .append("- Le champ 'content' doit être factuel, utile pour une vectorisation (éviter discours trop long).\n")
                .append("- Choisis 'subject' parmi les 4 valeurs (si doute, renvoie OTHER).\n")
                .append("- Si la date n'est pas identifiable avec précision, pose une date approximative ISO_LOCAL_DATE_TIME.\n")
                .append("- 'satisfaction' est l'évaluation sentimentale liée à l'événement (0 = très négatif, 10 = très positif).\n")
                .append("- 'importance' reflète l'impact à long terme (0.0 faible, 1.0 critique).\n")
                .append("- Ne fournis AUCUNE explication hors du JSON ; retourne uniquement le JSON exact.\n");
    }

    /**
     * Ajoute les instructions de planification
     */
    private void appendPlanningInstructions(StringBuilder prompt) {
        prompt.append("""
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
                
                
                
                CRITIQUE : La sortie DOIT être au format JSON valide avec :
                   - Aucun saut de ligne non échappé à l'intérieur des chaînes
                   - Utilisation de \\\\n pour les sauts de ligne
                   - Aucune barre oblique inversée (\\) à la fin
                """);
    }

    /**
     * Ajoute les instructions de formulation
     */
    private void appendFormulationInstructions(StringBuilder prompt) {
        prompt.append("## Instructions pour la Formulation de la Réponse\n\n")
                .append("**Objectif:** Formuler une réponse claire, naturelle et directement prononçable à voix haute.\n\n")
                .append("**Directives Clés:**\n")
                .append("1. **Sois Naturel et Conversationnel:** Imagine que tu parles à quelqu'un. Utilise un langage simple et fluide.\n")
                .append("2. **Utilise les Résultats:** Base ta réponse sur les résultats des actions exécutées. Sois factuel et précis.\n")
                .append("3. **TTS-Friendly:** Ta réponse ne doit contenir AUCUN caractère spécial qui ne se prononce pas (par exemple, pas de `*`, `-`, `|`, `[]`, `{}`).\n")
                .append("4. **Gère les Échecs avec Transparence:** Si une action a échoué, explique-le simplement sans chercher d'excuses. Par exemple : \"Je n'ai pas pu vérifier la météo car le service est indisponible en ce moment.\"\n")
                .append("5. **Concision:** Va droit au but. Évite les phrases trop longues ou les détails superflus.\n\n")
                .append("**Format de Sortie Attendu:**\n")
                .append("Une seule chaîne de texte, sans retour à la ligne superflu, prête à être lue par un moteur de synthèse vocale.\n\n")
                .append("RÉPONSE FINALE:\n");
    }

    /**
     * Ajoute les instructions pour l'initiative
     */
    private void appendInitiativeInstructions(StringBuilder prompt) {
        prompt.append("INSTRUCTIONS:\n");
        prompt.append("1. Analyse le désir et le contexte (souvenirs, opinions).\n");
        prompt.append("2. Crée un plan d'action logique et efficace pour satisfaire ce désir.\n");
        prompt.append("3. Utilise UNIQUEMENT les actions listées dans les outils.\n");
        prompt.append("4. Le plan doit être réalisable et cohérent avec la personnalité de l'IA.\n\n");
    }

    // ==================== MÉTHODES UTILITAIRES ====================

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

        contextDesc.append("**Résumé de la session :**\n");
        contextDesc.append(context.getSummary()).append("\n\n");

        if (context.getRecentMessages() != null && !context.getRecentMessages().isEmpty()) {
            contextDesc.append("**Messages récents :**\n");
            context.getRecentMessages().forEach(msg ->
                    contextDesc.append("- ").append(msg).append("\n"));
            contextDesc.append("\n");
        }

        if (context.getUserPreferences() != null && !context.getUserPreferences().isEmpty()) {
            contextDesc.append("**Préférences de l'utilisateur :**\n");
            context.getUserPreferences().forEach((key, value) ->
                    contextDesc.append("- ").append(key).append(": ").append(value).append("\n"));
            contextDesc.append("\n");
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
            String actionKey = generateActionKey(action, i);
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