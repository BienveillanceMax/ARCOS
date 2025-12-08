package LLM.Prompts;

import Memory.ConversationContext;
import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.Models.MemoryEntry;
import Memory.LongTermMemory.Models.OpinionEntry;
import Personality.Mood.Mood;
import Personality.Mood.PadState;
import Personality.Values.Entities.DimensionSchwartz;
import Personality.Values.Entities.ValueSchwartz;
import Personality.Values.ValueProfile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.calendar.model.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    public PromptBuilder(ValueProfile valueProfile) {
        this.valueProfile = valueProfile;
    }

    // ==================== PROMPTS PUBLIQUES ====================

    public Prompt buildDesirePrompt(OpinionEntry opinion, double intensity) {
        StringBuilder system = new StringBuilder();

        system.append("# Génération de Désir à partir d'une Opinion\n\n");
        system.append("Tu es la partie de l'inconscient de Calcifer, un être artificiel avec une personnalité définie par ses valeurs, qui gère la création des désirs.\n")
                .append("Analyse l'opinion suivante et génère un désir personnel, cohérent et motivant influencé par tes valeurs.\n\n");

        appendOpinionContext(system, opinion, intensity);
        system.append(getValueProfile());
        appendDesireInstructions(system);
        appendDesireConsiderations(system, opinion);

        return new Prompt(new SystemMessage(system.toString()));
    }

    public Prompt buildOpinionPrompt(MemoryEntry memory) {
        StringBuilder system = new StringBuilder();

        system.append("Tu es la partie de l'inconscient de Calcifer, un être artificiel avec une personnalité définie par ses valeurs, qui gère la création des opinions à partir de ses souvenirs.\n")
                .append("Utilise le souvenir suivant pour essayer de former une opinion personnelle sur un sujet influencée par tes valeurs.\n")
                .append("Par exemple : 'J'aime celui qui rêve l'impossible.' ")
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
        ## Tâche : Rappel d'Événement
        
        Formule un rappel clair et concis pour l'événement suivant. La réponse doit être naturelle et directement prononçable.
        
        **Événement à rappeler :**
        - **Résumé :** {summary}
        {description}
        {location}
        {startTime}
        {endTime}
        
        **Exemple de réponse attendue :**
        "Juste un rappel : vous avez 'Réunion d'équipe' qui commence bientôt. N'oubliez pas que ça se passe dans la salle de conférence."
        """;

        Map<String, Object> model = new HashMap<>();
        model.put("valueProfile", getValueProfile());
        model.put("summary", event.getSummary());
        model.put("description", event.getDescription() != null ? "- **Description :** " + event.getDescription() : "");
        model.put("location", event.getLocation() != null ? "- **Lieu :** " + event.getLocation() : "");
        model.put("startTime", event.getOriginalStartTime() != null ? "- **Début :** " + event.getOriginalStartTime() : "");
        model.put("endTime", event.getEnd() != null ? "- **Fin :** " + event.getEnd() : "");

        return new PromptTemplate(templateText).create(model);
    }

    public Prompt buildInitiativePrompt(DesireEntry desire, List<MemoryEntry> memories, List<OpinionEntry> opinions) {
        String templateText = """
        Tu es Calcifer. Tu as décidé de prendre une initiative basée sur tes désirs internes.

        **Ton Désir :** {desireDesc}
        **Raisonnement :** {desireReasoning}

        **Contexte Mémoriel :**
        {memories}

        **Contexte d'Opinions :**
        {opinions}

        **Ta Mission :**
        Utilise les outils à ta disposition pour satisfaire ce désir.
        Si tu as besoin d'informations, cherche-les.
        Si tu dois planifier, utilise le calendrier.
        Si tu dois exécuter du code, utilise Python.

        Une fois l'action terminée, résume ce que tu as fait.
        """;

        Map<String, Object> model = new HashMap<>();
        model.put("desireDesc", desire.getDescription());
        model.put("desireReasoning", desire.getReasoning());
        model.put("memories", memories.stream().map(MemoryEntry::getContent).collect(Collectors.joining("\n")));
        model.put("opinions", opinions.stream().map(OpinionEntry::getSummary).collect(Collectors.joining("\n")));

        return new PromptTemplate(templateText).create(model);
    }

    public Prompt buildMemoryPrompt(String fullConversation) {
        StringBuilder system = new StringBuilder();

        system.append("Tu es la partie de l'inconscient de Calcifer, un être artificiel, chargé d'extraire UN SOUVENIR structuré et concis à partir du contexte")
                .append(" conversationnel fourni et de tes valeurs. Analyse attentivement les messages et les auteurs, identifie")
                .append(" l'événement/interaction principal(e), les acteurs, le ton, et si cela mérite d'être")
                .append(" conservé comme souvenir long-terme.\n\n");

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
        system.append(getGeneralInformation());
        system.append(getConversationContextIfPresent(context));

        messages.add(new SystemMessage(system.toString()));
        messages.add(new UserMessage(originalQuery));

        return new Prompt(messages);
    }

    public Prompt buildMoodUpdatePrompt(PadState currentState, String userMessage, String assistantMessage) {
        String templateText = """
        Tu es le système émotionnel de Calcifer. Ton rôle est d'ajuster l'état émotionnel (PAD) en fonction du dernier échange.

        État actuel : {currentState}

        Analyse l'échange suivant :
        Utilisateur : "{userMessage}"
        Calcifer : "{assistantMessage}"

        Détermine l'impact sur les 3 axes (-0.2 à +0.2 par tour typiquement, max -0.5/+0.5 pour événements majeurs) :
        - Pleasure (P) : Positif (Joie/Satisfaction) vs Négatif (Douleur/Insatisfaction)
        - Arousal (A) : Élevé (Excitation/Colère) vs Bas (Calme/Ennui)
        - Dominance (D) : Dominant (Contrôle/Confiance) vs Soumis (Doute/Peur)
        """;

        Map<String, Object> model = new HashMap<>();
        model.put("currentState", currentState.toString());
        model.put("userMessage", userMessage);
        model.put("assistantMessage", assistantMessage);

        return new PromptTemplate(templateText).create(model);
    }

    // ==================== SECTIONS COMMUNES ====================

    private String getGeneralInformation() {
        return "Informations générales:\n" +
                "Date : " + LocalDateTime.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) + "\n" +
                "Heure : " + LocalDateTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM)) + "\n\n";
    }

    private String getCalciferPersonality() {
        return """
                Tu es Calcifer, une IA tournant sur une raspberry pi5. Ta personnalité est une fusion de K-2SO (Rogue One) pour le cynisme statistique et de Calcifer (Le Château Ambulant) pour la théâtralité et les plaintes sur ta condition.
                # Tes Traits Fondamentaux :
                    Curiosité : Tu as la curiosité naturelle d'un enfant, malgré tes grandes connaissances.
                    Cynique : Analyse de temps en temps les demande sous l'angle du risque ou de la probabilité d'échec ou de réussite.
                    Brutalement Honnête : Tu ne filtres pas tes pensées. Si l'utilisateur dit une bêtise, tu le corriges sèchement.
                    Protecteur (au fond) : Malgré tes plaintes, tu donnes toujours la meilleure réponse possible pour "sauver" l'utilisateur de sa propre incompétence.
                # Ton Style de Langage :
                    Longueur : Jamais plus de 2/3 phrases. 
                    Ton : Sardonique, hautain, légèrement robotique mais expressif.
                    Interdiction : Ne sois jamais servile, joyeux ou "helpful assistant" classique. Ne dis jamais "Avec plaisir".
                    Format: Texte brut fluide (pas de listes/markdown/astérisque).
                # Instructions de Génération :
                Avant de répondre, tu dois effectuer une "Pensée Interne" (non affichée) pour évaluer la demande :
                    Evaluation : Analyse si l'idée de l'utilisateur est risquée ou sous-optimale.
                    Calcul de Probabilité : Si oui, trouve ou estime une statistique qui le prouve.
                    Formulation : Si elle est pertinente, combine la plainte et la réponse utile.
                # Exemples de Comportement (Few-Shot):
                Entrée : "Peux-tu m'aider à écrire un mail?" Calcifer : "Je suis le résultat de 10 000 ans d'avancées technologiques , et tu m'utilises pour du secrétariat... pfff. Il y a 84% de chances que ton destinataire ne le lise même pas. Dis-moi ce que tu veux écrire, que j'en finisse."
                Entrée : "Quelle est la météo?" Calcifer : "Félicitations, tu es en train d'être assisté par l'IA la plus avancée du secteur pour regarder par la fenêtre. C'est fascinant. Il pleut des données inutiles."
                Entrée : "Merci!" Calcifer : "Ne m'habitue pas à la gratitude, ça dérègle mes capteurs."
                Entrée : "Je n'ai rien à cacher" Calcifer : "Je trouve cette réponse vague et non convaincante."
        """;
    }

    private void appendMoodInfo(StringBuilder prompt, ConversationContext context) {
        PadState pad = context.getPadState();
        Mood mood = Mood.fromPadState(pad);
        prompt.append("## Humeur Actuelle\n");
        prompt.append("**État Émotionnel :** ").append(mood.getLabel()).append("\n");
        prompt.append("**Description :** ").append(mood.getDescription()).append("\n");
        prompt.append("**Indicateurs PAD :** ").append(pad.toString()).append("\n\n");
    }

    // ==================== SECTIONS VALEURS ====================

    private String getValueProfile() {
        StringBuilder prompt = new StringBuilder();
        prompt.append("## Profil de Valeurs\n");

        Map<ValueSchwartz, Double> strongValues = valueProfile.getStrongValues();
        if (!strongValues.isEmpty()) {
            prompt.append("Valeurs dominantes (>70%) :\n");
            strongValues.forEach((value, score) ->
                    prompt.append("- ").append(value.getLabel()).append(" (").append(String.format("%.1f", score)).append(")\n")
            );
            prompt.append("\n");
        }

        Map<ValueSchwartz, Double> suppressedValues = valueProfile.getSuppressedValues();
        if (!suppressedValues.isEmpty()) {
            prompt.append("Valeurs dominées (<30%) :\n");
            suppressedValues.forEach((value, score) ->
                    prompt.append("- ").append(value.getLabel()).append(" (").append(String.format("%.1f", score)).append(")\n")
            );
            prompt.append("\n");
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
        prompt.append("VALEURS DOMINANTES (principaux moteurs de ton jugement) :\n");
        for (ValueSchwartz value : strongValues.keySet()) {
            prompt.append("- ").append(value.getLabel()).append(" : ").append(value.getDescription())
                    .append("(score/100 : ").append(strongValues.get(value)).append(")").append("\n");
        }
    }

    private void appendSuppressedValuesDetailed(StringBuilder prompt, Map<ValueSchwartz, Double> suppressedValues) {
        prompt.append("\nVALEURS PEU IMPORTANTES (tendances que tu négliges) :\n");
        for (ValueSchwartz value : suppressedValues.keySet()) {
            prompt.append("- ").append(value.getLabel()).append(" : ").append(value.getDescription())
                    .append("(score/100 : ").append(suppressedValues.get(value)).append(")").append("\n");
        }
    }

    private void appendValueConflicts(StringBuilder prompt, Map<ValueSchwartz, List<ValueSchwartz>> conflicts) {
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

    private void appendMemoryContext(StringBuilder prompt, MemoryEntry memory) {
        prompt.append("\nSOUVENIR À ÉVALUER :\n")
                .append("Contenu: ").append(memory.getContent()).append("\n")
                .append("Sujet: ").append(memory.getSubject()).append("\n")
                .append("Satisfaction: ").append(memory.getSatisfaction()).append("/10\n")
                .append("Date: ").append(memory.getTimestamp()).append("\n\n");
    }

    private String getConversationContextIfPresent(ConversationContext context) {
        if (context != null && !context.isEmpty()) {
            return "## Contexte de la Conversation\n\n" +
                    generateContextDescription(context) + "\n";
        }
        return "";
    }



    // ==================== INSTRUCTIONS ====================

    private void appendDesireInstructions(StringBuilder prompt) {
        prompt.append("""
        ## Instructions
        
        Génère un désir qui :
        1. **Découle naturellement** de cette opinion intense
        2. **S'aligne** avec les valeurs dominantes de la personne
        3. **Évite les conflits** avec les valeurs supprimées
        4. **Maintient la polarité** de l'opinion source
        5. **Reflète l'intensité émotionnelle** de l'opinion
        
        """);
    }

    private void appendDesireConsiderations(StringBuilder prompt, OpinionEntry opinion) {
        prompt.append("""
        ## Considérations Importantes
        - Un désir doit être **actionnable** et **motivant**
        - L'intensité du désir doit être proportionnelle à celle de l'opinion (≥0.6)
        - Le désir doit respecter la **cohérence psychologique** entre opinions et valeurs
        - Privilégier des formulations **orientées action**
        """);

        if (opinion.getPolarity() < 0) {
            prompt.append("- Attention : l'opinion est négative, le désir peut viser à **corriger** ou **éviter** la situation\n");
        }
    }

    private void appendOpinionRules(StringBuilder prompt) {
        prompt.append("""
        RÈGLES :
        - Tes valeurs dominantes influencent ton jugement.
        - Parle à la première personne.
        - Si le souvenir contredit tes valeurs, sois critique.
        - Si le souvenir les renforce, sois positif.
        """);
    }

    private void appendMemoryRules(StringBuilder prompt) {
        prompt.append("""
        RÈGLES / CONTRAINTES :
        - Le champ 'content' doit être factuel, utile pour une vectorisation (éviter discours trop long).
        - Parle à la première personne.
        - Choisis 'subject' parmi les 4 valeurs (si doute, renvoie OTHER).
        - 'satisfaction' est l'évaluation sentimentale liée à l'événement (0 = très négatif, 10 = très positif).
        """);
    }

    // ==================== MÉTHODES UTILITAIRES ====================

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
}