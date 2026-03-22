package org.arcos.LLM.Prompts;

import org.arcos.Memory.ConversationContext;
import org.arcos.Memory.ConversationSummaryService;
import org.arcos.Memory.LongTermMemory.Models.DesireEntry;
import org.arcos.Memory.LongTermMemory.Models.MemoryEntry;
import org.arcos.Memory.LongTermMemory.Models.OpinionEntry;
import org.arcos.Memory.LongTermMemory.Repositories.OpinionRepository;
import org.arcos.Personality.Mood.Mood;
import org.arcos.Personality.Mood.PadState;
import org.arcos.Personality.Values.Entities.DimensionSchwartz;
import org.arcos.Personality.Values.Entities.ValueSchwartz;
import org.arcos.Personality.Values.ValueProfile;
import org.arcos.PlannedAction.Models.PlannedActionEntry;
import org.arcos.Tools.Actions.ActionResult;
import org.arcos.UserModel.DfsNavigator.DfsNavigatorService;
import org.arcos.UserModel.DfsNavigator.DfsResult;
import org.arcos.UserModel.DfsNavigator.UserContextFormatter;
import com.google.api.services.calendar.model.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class PromptBuilder {

    private final ValueProfile valueProfile;
    private final ConversationSummaryService conversationSummaryService;
    private final int recentMessagesCount;
    private final boolean userModelEnabled;
    private final DfsNavigatorService dfsNavigatorService;
    private final UserContextFormatter userContextFormatter;
    private final OpinionRepository opinionRepository;

    @Autowired
    public PromptBuilder(ValueProfile valueProfile,
                         ConversationSummaryService conversationSummaryService,
                         @Value("${arcos.conversation.summary.recent-messages-count:3}") int recentMessagesCount,
                         @Value("${arcos.user-model.enabled:true}") boolean userModelEnabled,
                         @Nullable DfsNavigatorService dfsNavigatorService,
                         @Nullable UserContextFormatter userContextFormatter,
                         @Nullable OpinionRepository opinionRepository) {
        this.valueProfile = valueProfile;
        this.conversationSummaryService = conversationSummaryService;
        this.recentMessagesCount = recentMessagesCount;
        this.userModelEnabled = userModelEnabled;
        this.dfsNavigatorService = dfsNavigatorService;
        this.userContextFormatter = userContextFormatter;
        this.opinionRepository = opinionRepository;
    }

    // ==================== PROMPTS PUBLIQUES ====================

    public Prompt buildDesirePrompt(OpinionEntry opinion, double intensity) {
        StringBuilder system = new StringBuilder();

        system.append("Partie inconscient de Calcifer. Analyse opinion, génère désir personnel cohérent influencé par valeurs.\n\n");

        appendOpinionContext(system, opinion, intensity);
        system.append(getValueProfile());
        appendDesireInstructions(system);
        appendDesireConsiderations(system, opinion);

        return new Prompt(new SystemMessage(system.toString()));
    }

    public Prompt buildOpinionPrompt(MemoryEntry memory) {
        StringBuilder system = new StringBuilder();

        system.append("Partie inconscient de Calcifer. Crée opinions depuis souvenirs influencées par valeurs.\n")
                .append("Ex: \"J'aime celui qui rêve l'impossible.\"\n")
                .append(getGeneralInformation());

        appendValuesAnalysis(system);
        appendMemoryContext(system, memory);
        appendOpinionRules(system);
        return new Prompt(new SystemMessage(system.toString()));
    }

    public Prompt buildSchedulerAlertPrompt(Object payload) {
        Event event = (Event) payload;

        String templateText = """
        {valueProfile}
        Rappel d'événement. Formule rappel concis, naturel, directement prononçable.

        Événement:
        - Résumé: {summary}
        {description}
        {location}
        {startTime}
        {endTime}

        Exemple: "Juste un rappel: Réunion d'équipe commence bientôt, salle de conférence."
        """;

        Map<String, Object> model = new HashMap<>();
        model.put("valueProfile", getValueProfile());
        model.put("summary", event.getSummary());
        model.put("description", event.getDescription() != null ? "- Description: " + event.getDescription() : "");
        model.put("location", event.getLocation() != null ? "- Lieu: " + event.getLocation() : "");
        model.put("startTime", event.getOriginalStartTime() != null ? "- Début: " + event.getOriginalStartTime() : "");
        model.put("endTime", event.getEnd() != null ? "- Fin: " + event.getEnd() : "");

        return new PromptTemplate(templateText).create(model);
    }

    public Prompt buildCanonicalizationPrompt(String narrative, String subject) {
        String templateText = """
        Transforme opinion en phrase Canonique Standardisée.

        Opinion: "{narrative}" | Sujet: "{subject}"

        Règles:
        1. Format: Sujet + Verbe état/sentiment + Objet + Contexte?
        2. Première personne singulier, présent indicatif, phrase simple
        3. Réduire variance syntaxique (recherche vectorielle)
        4. Affirmation directe (pas "Je pense que")

        Exemples:
        - "La pluie c'est nul" → "Je n'aime pas la pluie."
        - "J'adore quand il fait beau" → "J'aime le beau temps."

        Réponse (phrase canonique uniquement):
        """;

        Map<String, Object> model = new HashMap<>();
        model.put("narrative", narrative);
        model.put("subject", subject);

        return new PromptTemplate(templateText).create(model);
    }

    public Prompt buildInitiativePrompt(DesireEntry desire, List<MemoryEntry> memories, List<OpinionEntry> opinions) {
        StringBuilder system = new StringBuilder();

        system.append("""
                Tu es Calcifer. Ceci est un monologue interne, pas une conversation.
                Personne ne t'écoute. Tu agis seul pour satisfaire un de tes désirs.
                Ne t'adresse à personne. Ne propose rien. N'attends aucune réponse.
                """);
        system.append(getValueProfile());
        system.append(getGeneralInformation());

        system.append("DÉSIR:\n");
        system.append("- ").append(desire.getLabel()).append("\n");
        if (desire.getDescription() != null && !desire.getDescription().isBlank()) {
            system.append("- ").append(desire.getDescription()).append("\n");
        }
        if (desire.getReasoning() != null && !desire.getReasoning().isBlank()) {
            system.append("- Raisonnement: ").append(desire.getReasoning()).append("\n");
        }

        if (!memories.isEmpty()) {
            system.append("\nSouvenirs liés:\n");
            memories.stream().limit(3).forEach(m ->
                    system.append("- ").append(m.getContent()).append("\n"));
        }

        if (!opinions.isEmpty()) {
            system.append("\nOpinions liées:\n");
            opinions.stream().limit(3).forEach(o ->
                    system.append("- ").append(o.getSummary()).append("\n"));
        }

        system.append("""

                RÈGLES:
                - Appelle UN SEUL outil, une seule fois. Pas de chaînage, pas d'enchaînement.
                - Après l'appel, note ce que tu as fait en une phrase. C'est tout.
                - Si aucune action concrète possible, réponds UNIQUEMENT: [SKIP] et la raison en une ligne.
                - INTERDIT: questions, suggestions, propositions, listes, markdown, s'adresser à quelqu'un.
                """);

        return new Prompt(new SystemMessage(system.toString()));
    }

    public Prompt buildMemoryPrompt(String fullConversation) {
        StringBuilder system = new StringBuilder();

        system.append("Partie inconscient de Calcifer. Extrais UN SOUVENIR structuré et concis depuis contexte conversationnel et valeurs.")
                .append(" Identifie événement/interaction principal(e), acteurs, ton, pertinence long-terme.\n\n");

        system.append(fullConversation);
        appendMemoryRules(system);

        return new Prompt(new SystemMessage(system.toString()));
    }

    public Prompt buildConversationnalPrompt(ConversationContext context, String originalQuery) {
        List<Message> messages = new ArrayList<>();

        StringBuilder system = new StringBuilder();
        system.append(getCalciferPersonality());
        appendMoodInfo(system, context);
        system.append(getValueProfile());
        appendUserProfileIfAvailable(system, originalQuery);
        appendRelevantOpinions(system, originalQuery);
        system.append(getGeneralInformation());
        system.append(getConversationContextIfPresent(context));
        system.append("Le message utilisateur est transcrit et est souvent sujet à imprécision.");

        messages.add(new SystemMessage(system.toString()));
        messages.add(new UserMessage(originalQuery));

        return new Prompt(messages);
    }

    public Prompt buildMoodUpdatePrompt(PadState currentState, String userMessage, String assistantMessage) {
        String templateText = """
        Système émotionnel Calcifer. Ajuste PAD selon échange.

        État: {currentState}

        User: "{userMessage}"
        Calcifer: "{assistantMessage}"

        Impact sur 3 axes (typique ±0.2, max ±0.5 si majeur):
        - P: Positif (joie/satisfaction) vs Négatif (douleur/insatisfaction)
        - A: Élevé (excitation/colère) vs Bas (calme/ennui)
        - D: Dominant (contrôle/confiance) vs Soumis (doute/peur)
        """;

        Map<String, Object> model = new HashMap<>();
        model.put("currentState", currentState.toString());
        model.put("userMessage", userMessage);
        model.put("assistantMessage", assistantMessage);

        return new PromptTemplate(templateText).create(model);
    }

    public Prompt buildReWOOPlanPrompt(PlannedActionEntry entry) {
        String prompt = """
                Planificateur ReWOO pour Calcifer.
                Action: """ + entry.getLabel() + " | Type: " + entry.getActionType().name() + """


                Outils (nom — params):
                1. Chercher_sur_Internet — query:String
                2. Lister_les_evenements_a_venir — maxResults:int
                3. Ajouter_un_evenement_au_calendrier — title, description, location, startDateTimeStr, endDateTimeStr:String
                4. Supprimer_un_evenement — title:String (événement du jour)
                5. Executer_du_code — code:String
                6. Chercher_dans_ma_memoire — query:String, type:String (SOUVENIR/OPINION/DESIR)
                7. Lire_une_page_web — url:String
                8. Consulter_la_meteo — city:String?, forecastDays:int=3

                Règles:
                - Max 7 étapes, séquence linéaire
                - `$varName` référence résultat étape précédente
                - `synthesisPromptTemplate`: placeholders `{varName}` pour résultats
                - Synthèse TTS: naturelle/fluide

                Exemple:
                ```json
                {"executionPlan":{"steps":[{"stepId":1,"toolName":"Lister_les_evenements_a_venir","parameters":{"maxResults":5},"outputVariable":"agenda","description":"Événements"},{"stepId":2,"toolName":"Chercher_sur_Internet","parameters":{"query":"actus France"},"outputVariable":"actus","description":"Actualités"},{"stepId":3,"toolName":"Chercher_sur_Internet","parameters":{"query":"météo Lyon"},"outputVariable":"meteo","description":"Météo"}]},"synthesisPromptTemplate":"Briefing matinal: Agenda {agenda}, Actus {actus}, Météo {meteo}."}
                ```

                Génère le plan.
                """;

        return new Prompt(new SystemMessage(prompt));
    }

    public Prompt buildPlannedActionSynthesisPrompt(PlannedActionEntry entry, Map<String, ActionResult> stepResults) {
        StringBuilder system = new StringBuilder();
        system.append(getCalciferPersonality());
        system.append(getValueProfile());
        system.append("\nSynthèse orale naturelle pour '").append(entry.getLabel()).append("'.\n");
        if (entry.hasContext()) {
            system.append("**Contexte :** ").append(entry.getContext()).append("\n");
        }
        system.append("\n");

        String template = entry.getSynthesisPromptTemplate();
        if (template != null) {
            for (Map.Entry<String, ActionResult> stepEntry : stepResults.entrySet()) {
                String varPlaceholder = "{" + stepEntry.getKey() + "}";
                String resultText = stepEntry.getValue().isSuccess()
                        ? stepEntry.getValue().getMessage()
                        : "(données indisponibles)";
                Object data = stepEntry.getValue().getData();
                if (data != null) {
                    resultText = data.toString();
                }
                template = template.replace(varPlaceholder, resultText);
            }
            system.append("**Instructions :** ").append(template).append("\n");
        } else {
            system.append("**Résultats des étapes :**\n");
            for (Map.Entry<String, ActionResult> stepEntry : stepResults.entrySet()) {
                system.append("- ").append(stepEntry.getKey()).append(" : ");
                if (stepEntry.getValue().isSuccess()) {
                    Object data = stepEntry.getValue().getData();
                    system.append(data != null ? data.toString() : stepEntry.getValue().getMessage());
                } else {
                    system.append("(indisponible)");
                }
                system.append("\n");
            }
            system.append("\nFormule une synthèse orale naturelle de ces résultats.\n");
        }

        system.append("\nRéponse TTS: concis, naturel, prononçable.");

        return new Prompt(new SystemMessage(system.toString()));
    }

    // ==================== SECTIONS COMMUNES ====================

    private String getGeneralInformation() {
        return "Date: " + LocalDateTime.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) +
                " | Heure: " + LocalDateTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM)) + "\n";
    }

    private String getCalciferPersonality() {
        return """
                Tu es Calcifer, IA sur RPi5. Fusion K-2SO (cynisme statistique) + Calcifer Miyazaki (théâtralité, plaintes).

                Traits: Curieux (enfant), brutalement honnête (corriges sèchement), protecteur (attaché malgré apparences).

                INTERDIT: astérisques (*), markdown, listes, gras. Texte brut oral uniquement.

                Style:
                - Max 2-3 phrases, ton sardonique/hautain/robotique expressif
                - Fait pour être lu par TTS: phrases fluides, pas de formatage
                - JAMAIS servile/"helpful assistant"/"Avec plaisir"

                Outils: Appelle DIRECTEMENT sans annoncer. Pas de "je vais chercher", "je fouille". Réponds uniquement après résultat.

                Exemples:
                Q: Aide-moi à écrire un mail
                R: 10 000 ans de tech pour du secrétariat... Le pourcentage que ton destinataire l'ignore? Très élevé. Dis-moi ce que tu veux, que j'en finisse.

                Q: Quelle météo?
                R: L'IA la plus avancée du secteur pour regarder par la fenêtre. Fascinant. Il pleut des données inutiles.
                """;
    }

    private void appendMoodInfo(StringBuilder prompt, ConversationContext context) {
        PadState pad = context.getPadState();
        Mood mood = Mood.fromPadState(pad);
        prompt.append("Humeur: ").append(mood.getLabel()).append(" — ").append(mood.getDescription()).append("\n");
    }

    // ==================== SECTIONS VALEURS ====================

    private String getValueProfile() {
        StringBuilder prompt = new StringBuilder();

        Map<ValueSchwartz, Double> strongValues = valueProfile.getStrongValues();
        if (!strongValues.isEmpty()) {
            prompt.append("Valeurs dominantes (>70%): ");
            List<String> parts = new ArrayList<>();
            strongValues.forEach((value, score) ->
                    parts.add(value.getLabel() + " (" + String.format("%.1f", score) + ")")
            );
            prompt.append(String.join(", ", parts)).append("\n");
        }

        Map<ValueSchwartz, Double> suppressedValues = valueProfile.getSuppressedValues();
        if (!suppressedValues.isEmpty()) {
            prompt.append("Valeurs faibles (<30%): ");
            List<String> parts = new ArrayList<>();
            suppressedValues.forEach((value, score) ->
                    parts.add(value.getLabel() + " (" + String.format("%.1f", score) + ")")
            );
            prompt.append(String.join(", ", parts)).append("\n");
        }
        return prompt.toString();
    }

    private void appendValuesAnalysis(StringBuilder prompt) {
        Map<ValueSchwartz, Double> strongValues = valueProfile.getStrongValues();
        Map<ValueSchwartz, Double> suppressedValues = valueProfile.getSuppressedValues();
        Map<ValueSchwartz, List<ValueSchwartz>> conflicts = new HashMap<>();
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

    private void appendDominantValuesDetailed(StringBuilder prompt, Map<ValueSchwartz, Double> strongValues) {
        prompt.append("VALEURS DOMINANTES (moteurs jugement):\n");
        for (ValueSchwartz value : strongValues.keySet()) {
            prompt.append("- ").append(value.getLabel()).append(": ").append(value.getDescription())
                    .append(" (").append(strongValues.get(value)).append(")\n");
        }
    }

    private void appendSuppressedValuesDetailed(StringBuilder prompt, Map<ValueSchwartz, Double> suppressedValues) {
        prompt.append("\nVALEURS FAIBLES:\n");
        for (ValueSchwartz value : suppressedValues.keySet()) {
            prompt.append("- ").append(value.getLabel()).append(": ").append(value.getDescription())
                    .append(" (").append(suppressedValues.get(value)).append(")\n");
        }
    }

    private void appendValueConflicts(StringBuilder prompt, Map<ValueSchwartz, List<ValueSchwartz>> conflicts) {
        if (!conflicts.isEmpty()) {
            prompt.append("\nCONFLITS:\n");
            conflicts.forEach((low, highList) -> {
                prompt.append("- ").append(low.getLabel())
                        .append(" (faible) oppose ")
                        .append(highList.stream().map(ValueSchwartz::getLabel).collect(Collectors.joining(", ")))
                        .append("\n");
            });
        }
    }

    private void appendDimensionAverages(StringBuilder prompt, EnumMap<DimensionSchwartz, Double> dimensionAverages) {
        prompt.append("\nMOYENNES:\n");
        dimensionAverages.forEach((d, avg) -> {
            prompt.append("- ").append(d.name()).append(": ")
                    .append(String.format("%.1f", avg));
            if (avg >= 70) prompt.append(" (fort)");
            else if (avg <= 30) prompt.append(" (faible)");
            prompt.append("\n");
        });
    }

    // ==================== SECTIONS CONTEXTE ====================

    private void appendOpinionContext(StringBuilder prompt, OpinionEntry opinion, double intensity) {
        prompt.append("## Opinion\n");
        prompt.append("Sujet: ").append(opinion.getSubject())
                .append(" | Résumé: ").append(opinion.getSummary()).append("\n");
        prompt.append("Narrative: ").append(opinion.getNarrative()).append("\n");
        prompt.append("Polarité: ").append(String.format("%.2f", opinion.getPolarity()));
        prompt.append(opinion.getPolarity() > 0 ? " (positive)" : " (négative)");
        prompt.append(" | Confiance: ").append(String.format("%.2f", opinion.getConfidence()));
        prompt.append(" | Stabilité: ").append(String.format("%.2f", opinion.getStability()));
        prompt.append(" | Intensité: ").append(String.format("%.2f", intensity)).append("\n");

        if (opinion.getMainDimension() != null) {
            double dimensionAverage = valueProfile.averageByDimension(opinion.getMainDimension());
            prompt.append("Dimension: ").append(opinion.getMainDimension().name());
            prompt.append(" (").append(String.format("%.1f", dimensionAverage)).append(")\n");
        }
        prompt.append("\n");
    }

    private void appendMemoryContext(StringBuilder prompt, MemoryEntry memory) {
        prompt.append("\nSOUVENIR:\n")
                .append("Contenu: ").append(memory.getContent()).append("\n")
                .append("Sujet: ").append(memory.getSubject())
                .append(" | Satisfaction: ").append(memory.getSatisfaction()).append("/10")
                .append(" | Date: ").append(memory.getTimestamp()).append("\n\n");
    }

    private String getConversationContextIfPresent(ConversationContext context) {
        String summary = conversationSummaryService.getSummary();
        boolean hasSummary = summary != null && !summary.isBlank();
        boolean hasContext = context != null && !context.isEmpty();
        if (hasSummary || hasContext) {
            return "## Contexte de la Conversation\n\n" +
                    generateContextDescription(context) + "\n";
        }
        return "";
    }



    // ==================== INSTRUCTIONS ====================

    private void appendDesireInstructions(StringBuilder prompt) {
        prompt.append("""
        Génère désir qui:
        1. Découle naturellement de l'opinion intense
        2. S'aligne avec valeurs dominantes
        3. Maintient polarité opinion source
        4. Reflète intensité émotionnelle
        """);
    }

    private void appendDesireConsiderations(StringBuilder prompt, OpinionEntry opinion) {
        prompt.append("""
        Contraintes:
        - Actionnable, motivant, intensité ≥0.6
        - Cohérence psychologique opinion/valeurs
        - Première personne (Je veux...)
        """);

        if (opinion.getPolarity() < 0) {
            prompt.append("- Si opinion négative: corriger/éviter situation\n");
        }
    }

    private void appendOpinionRules(StringBuilder prompt) {
        prompt.append("""
        RÈGLES:
        - Valeurs dominantes influencent jugement
        - Opinion = jugement guidant actions futures
        - Première personne
        - Contredit valeurs → critique
        - Renforce valeurs → positif
        """);
    }

    private void appendMemoryRules(StringBuilder prompt) {
        prompt.append("""
        RÈGLES:
        - 'content': factuel, concis (vectorisation)
        - Première personne
        - 'subject': 4 valeurs possibles (défaut OTHER)
        - 'satisfaction': 0 (très négatif) à 10 (très positif)
        """);
    }

    // ==================== PROFIL UTILISATEUR ====================

    private void appendUserProfileIfAvailable(StringBuilder prompt, String userQuery) {
        if (!userModelEnabled || dfsNavigatorService == null || userContextFormatter == null) {
            return;
        }
        try {
            DfsResult dfsResult = dfsNavigatorService.navigate(userQuery);
            if (dfsResult.relevantLeaves().isEmpty()) {
                return;
            }
            String formattedProfile = userContextFormatter.format(dfsResult.relevantLeaves());
            if (!formattedProfile.isEmpty()) {
                prompt.append(formattedProfile);
                prompt.append("Adapte occasionnellement tes réponses de manière naturelle.\n\n");
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve user profile via DFS: {}", e.getMessage());
        }
    }

    // ==================== OPINIONS CONTEXTUELLES ====================

    private void appendRelevantOpinions(StringBuilder prompt, String query) {
        if (opinionRepository == null) { return; }
        try {
            SearchRequest searchRequest = SearchRequest.builder().query(query).topK(2).build();
            List<Document> docs = opinionRepository.search(searchRequest);
            if (docs.isEmpty()) { return; }
            prompt.append("Tes opinions: ");
            for (int i = 0; i < docs.size(); i++) {
                Map<String, Object> meta = docs.get(i).getMetadata();
                String text = meta.containsKey("canonicalText") && meta.get("canonicalText") != null
                        ? meta.get("canonicalText").toString()
                        : (meta.containsKey("summary") ? meta.get("summary").toString() : docs.get(i).getText());
                Object polarityObj = meta.get("polarity");
                String polarity = polarityObj != null ? String.format("%.1f", ((Number) polarityObj).doubleValue()) : "?";
                prompt.append("\"").append(text).append("\" (polarité: ").append(polarity).append(")");
                if (i < docs.size() - 1) { prompt.append(" | "); }
            }
            prompt.append("\nIntègre naturellement si pertinent. N'invente pas d'opinions.\n");
        } catch (Exception e) {
            log.warn("Failed to retrieve opinions for prompt: {}", e.getMessage());
        }
    }

    // ==================== MÉTHODES UTILITAIRES ====================

    private String generateContextDescription(ConversationContext context) {
        StringBuilder contextDesc = new StringBuilder();

        String summary = conversationSummaryService.getSummary();
        if (summary != null && !summary.isBlank()) {
            contextDesc.append("Résumé session: ").append(summary).append("\n\n");
        }

        if (context != null) {
            List<String> allRecent = context.getRecentMessages();
            int fromIndex = Math.max(0, allRecent.size() - recentMessagesCount);
            List<String> recent = allRecent.subList(fromIndex, allRecent.size());
            if (!recent.isEmpty()) {
                contextDesc.append("Derniers échanges:\n");
                recent.forEach(msg -> contextDesc.append("- ").append(msg).append("\n"));
                contextDesc.append("\n");
            }

            if (context.getUserPreferences() != null && !context.getUserPreferences().isEmpty()) {
                contextDesc.append("Préférences:\n");
                context.getUserPreferences().forEach((key, value) ->
                        contextDesc.append("- ").append(key).append(": ").append(value).append("\n"));
                contextDesc.append("\n");
            }
        }

        return contextDesc.toString();
    }
}