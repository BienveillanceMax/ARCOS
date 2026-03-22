package org.arcos.UnitTests.LLM;

import org.arcos.LLM.Prompts.PromptBuilder;
import org.arcos.Memory.ConversationContext;
import org.arcos.Memory.ConversationSummaryService;
import org.arcos.Memory.LongTermMemory.Models.DesireEntry;
import org.arcos.Memory.LongTermMemory.Models.MemoryEntry;
import org.arcos.Memory.LongTermMemory.Models.OpinionEntry;
import org.arcos.Memory.LongTermMemory.Repositories.OpinionRepository;
import org.arcos.Personality.Values.ValueProfile;
import org.arcos.PlannedAction.Models.ActionType;
import org.arcos.PlannedAction.Models.PlannedActionEntry;
import org.arcos.common.utils.ObjectCreationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour PromptBuilder — section "Contexte de la Conversation" (STORY-RCS-002).
 * Vérifie que generateContextDescription() utilise le résumé roulant + N messages récents bruts.
 */
class PromptBuilderTest {

    @Mock
    private ConversationSummaryService conversationSummaryService;

    @Mock
    private OpinionRepository opinionRepository;

    private PromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // ValueProfile avec profil DEFAULT (scores à 50.0) — pas de valeurs dominantes/supprimées
        ValueProfile valueProfile = new ValueProfile();
        promptBuilder = new PromptBuilder(valueProfile, conversationSummaryService, 3, true, null, null, null);
    }

    // ===== T7 : résumé disponible + contexte non vide =====

    @Test
    void generateContextDescription_ShouldIncludeSummaryAndRecentMessages_WhenBothPresent() {
        when(conversationSummaryService.getSummary()).thenReturn("L'utilisateur aime les films d'action et a demandé la météo.");

        ConversationContext context = new ConversationContext();
        context.addUserMessage("j'aime les films d'action");
        context.addAssistantMessage("Ah oui, les films d'action.");
        context.addUserMessage("c'est quoi la météo demain");
        context.addAssistantMessage("Demain il fera 12°C et nuageux à Lyon.");

        Prompt prompt = promptBuilder.buildConversationnalPrompt(context, "test");
        String systemContent = getSystemContent(prompt);

        assertTrue(systemContent.contains("Résumé session:"),
                "Doit contenir l'en-tête du résumé");
        assertTrue(systemContent.contains("L'utilisateur aime les films d'action"),
                "Doit contenir le texte du résumé");
        assertTrue(systemContent.contains("Derniers échanges:"),
                "Doit contenir les derniers échanges");
        // Avec recentMessagesCount=3 et 4 messages, les 3 derniers doivent apparaître
        assertTrue(systemContent.contains("ASSISTANT: Ah oui, les films d'action.") ||
                        systemContent.contains("USER: c'est quoi la météo demain") ||
                        systemContent.contains("ASSISTANT: Demain il fera 12°C"),
                "Doit contenir au moins un des 3 derniers messages");
        // Le premier message (USER: j'aime les films d'action) ne doit pas apparaître
        // (il est le 4ème en partant de la fin, donc exclu avec N=3)
        assertFalse(systemContent.contains("- USER: j'aime les films d'action"),
                "Le 1er message ne doit pas figurer avec N=3");
    }

    // ===== T8 : résumé vide + contexte non vide =====

    @Test
    void generateContextDescription_ShouldShowOnlyRecentMessages_WhenSummaryIsEmpty() {
        when(conversationSummaryService.getSummary()).thenReturn("");

        ConversationContext context = new ConversationContext();
        context.addUserMessage("c'est quoi la météo");
        context.addAssistantMessage("Il fait 15°C.");

        Prompt prompt = promptBuilder.buildConversationnalPrompt(context, "test");
        String systemContent = getSystemContent(prompt);

        assertFalse(systemContent.contains("Résumé session:"),
                "Ne doit pas contenir l'en-tête du résumé si résumé vide");
        assertTrue(systemContent.contains("Derniers échanges:"),
                "Doit contenir les derniers échanges");
        assertTrue(systemContent.contains("Il fait 15°C"),
                "Doit contenir le contenu du message récent");
    }

    // ===== T9 : résumé disponible + contexte vide =====

    @Test
    void generateContextDescription_ShouldShowOnlySummary_WhenContextIsEmpty() {
        when(conversationSummaryService.getSummary()).thenReturn("L'utilisateur est intéressé par les nouvelles technologies.");

        ConversationContext context = new ConversationContext();
        // Contexte vide — pas de messages, pas de préférences

        Prompt prompt = promptBuilder.buildConversationnalPrompt(context, "test");
        String systemContent = getSystemContent(prompt);

        assertTrue(systemContent.contains("Résumé session:"),
                "Doit contenir l'en-tête du résumé même si le contexte est vide");
        assertTrue(systemContent.contains("L'utilisateur est intéressé par les nouvelles technologies."),
                "Doit contenir le texte du résumé");
        assertFalse(systemContent.contains("Derniers échanges:"),
                "Ne doit pas contenir les derniers échanges si le contexte est vide");
    }

    // ===== Opinion injection tests =====

    @Test
    void buildConversationnalPrompt_shouldIncludeOpinions_whenRepoReturnsResults() {
        when(conversationSummaryService.getSummary()).thenReturn("");

        Document doc1 = new Document("J'aime la pluie d'automne.",
                Map.of("canonicalText", "J'aime la pluie d'automne.", "polarity", 0.7));
        Document doc2 = new Document("La politique manque de vision.",
                Map.of("canonicalText", "La politique manque de vision.", "polarity", -0.4));

        when(opinionRepository.search(any(SearchRequest.class)))
                .thenReturn(List.of(doc1, doc2));

        PromptBuilder builderWithOpinions = new PromptBuilder(
                new ValueProfile(), conversationSummaryService, 3, true, null, null, opinionRepository);

        Prompt prompt = builderWithOpinions.buildConversationnalPrompt(new ConversationContext(), "test météo");
        String systemContent = getSystemContent(prompt);

        assertTrue(systemContent.contains("J'aime la pluie d'automne."),
                "Should contain first opinion");
        assertTrue(systemContent.contains("La politique manque de vision."),
                "Should contain second opinion");
        assertTrue(systemContent.contains("N'invente pas d'opinions"),
                "Should contain guard rail instruction");
    }

    @Test
    void buildConversationnalPrompt_shouldIncludeSingleOpinion_withoutSeparator() {
        when(conversationSummaryService.getSummary()).thenReturn("");

        Document doc = new Document("Le café est essentiel.",
                Map.of("canonicalText", "Le café est essentiel.", "polarity", 0.8));

        when(opinionRepository.search(any(SearchRequest.class)))
                .thenReturn(List.of(doc));

        PromptBuilder builderWithOpinions = new PromptBuilder(
                new ValueProfile(), conversationSummaryService, 3, true, null, null, opinionRepository);

        Prompt prompt = builderWithOpinions.buildConversationnalPrompt(new ConversationContext(), "café");
        String systemContent = getSystemContent(prompt);

        assertTrue(systemContent.contains("Le café est essentiel."),
                "Should contain the opinion");
        assertFalse(systemContent.contains("polarité: 0.8) | "),
                "Single opinion should not have separator after it");
    }

    @Test
    void buildConversationnalPrompt_shouldSkipOpinions_whenRepoReturnsEmpty() {
        when(conversationSummaryService.getSummary()).thenReturn("");
        when(opinionRepository.search(any(SearchRequest.class)))
                .thenReturn(Collections.emptyList());

        PromptBuilder builderWithOpinions = new PromptBuilder(
                new ValueProfile(), conversationSummaryService, 3, true, null, null, opinionRepository);

        Prompt prompt = builderWithOpinions.buildConversationnalPrompt(new ConversationContext(), "bonjour");
        String systemContent = getSystemContent(prompt);

        assertFalse(systemContent.contains("Tes opinions"),
                "Should not contain opinions block when none found");
    }

    @Test
    void buildConversationnalPrompt_shouldNotCrash_whenOpinionRepoIsNull() {
        when(conversationSummaryService.getSummary()).thenReturn("");

        PromptBuilder builderNoOpinions = new PromptBuilder(
                new ValueProfile(), conversationSummaryService, 3, true, null, null, null);

        assertDoesNotThrow(() ->
                builderNoOpinions.buildConversationnalPrompt(new ConversationContext(), "test"));
    }

    // ===== buildReWOOPlanPrompt — real construction (catches ST4 template parsing bugs) =====

    @Test
    void buildReWOOPlanPrompt_shouldSucceedWithSimpleReminder() {
        PlannedActionEntry entry = ObjectCreationUtils.createSimpleReminderEntry();

        Prompt prompt = promptBuilder.buildReWOOPlanPrompt(entry);

        assertNotNull(prompt);
        String content = getSystemContent(prompt);
        assertTrue(content.contains("Appeler le dentiste"),
                "Prompt should contain the action label");
        assertTrue(content.contains("TODO"),
                "Prompt should contain the action type");
    }

    @Test
    void buildReWOOPlanPrompt_shouldSucceedWithComplexHabit() {
        // This entry's label + JSON example in the prompt previously crashed ST4 parsing
        PlannedActionEntry entry = ObjectCreationUtils.createComplexHabitEntry();

        Prompt prompt = promptBuilder.buildReWOOPlanPrompt(entry);

        assertNotNull(prompt);
        String content = getSystemContent(prompt);
        assertTrue(content.contains("Briefing matinal"),
                "Prompt should contain the action label");
        assertTrue(content.contains("HABIT"),
                "Prompt should contain the action type");
    }

    @Test
    void buildReWOOPlanPrompt_shouldHandleFrenchApostrophesInLabel() {
        // French apostrophes in labels combined with JSON curly braces in template
        // was the exact combination that triggered the ST4 parsing crash
        PlannedActionEntry entry = new PlannedActionEntry();
        entry.setLabel("Réseau d'espoir régénératif");
        entry.setActionType(ActionType.HABIT);

        Prompt prompt = promptBuilder.buildReWOOPlanPrompt(entry);

        assertNotNull(prompt);
        String content = getSystemContent(prompt);
        assertTrue(content.contains("Réseau d'espoir régénératif"),
                "Prompt should preserve French apostrophes verbatim");
    }

    // ===== buildInitiativePrompt — real construction =====

    @Test
    void buildInitiativePrompt_shouldSucceedWithDesireAndContext() {
        DesireEntry desire = ObjectCreationUtils.createIntensePendingDesireEntry("opinion-123");
        List<MemoryEntry> memories = List.of(ObjectCreationUtils.createMemoryEntry());
        List<OpinionEntry> opinions = List.of(ObjectCreationUtils.createOpinionEntry());

        Prompt prompt = promptBuilder.buildInitiativePrompt(desire, memories, opinions);

        assertNotNull(prompt);
        String content = getSystemContent(prompt);
        assertTrue(content.contains("monologue interne"),
                "Should contain inner-monologue framing");
        assertTrue(content.contains(desire.getLabel()),
                "Should contain desire label");
        assertTrue(content.contains("Souvenirs liés"),
                "Should include memories section");
        assertTrue(content.contains("Opinions liées"),
                "Should include opinions section");
        assertTrue(content.contains("[SKIP]"),
                "Should mention the SKIP escape hatch");
    }

    @Test
    void buildInitiativePrompt_shouldSucceedWithEmptyContext() {
        DesireEntry desire = new DesireEntry();
        desire.setLabel("Test desire");
        desire.setDescription("Test description");

        Prompt prompt = promptBuilder.buildInitiativePrompt(desire, Collections.emptyList(), Collections.emptyList());

        assertNotNull(prompt);
        String content = getSystemContent(prompt);
        assertTrue(content.contains("Test desire"));
        assertFalse(content.contains("Souvenirs liés"),
                "Should not include memories section when empty");
        assertFalse(content.contains("Opinions liées"),
                "Should not include opinions section when empty");
    }

    // ===== Utilitaire =====

    private String getSystemContent(Prompt prompt) {
        return prompt.getInstructions().stream()
                .filter(msg -> msg instanceof SystemMessage)
                .map(msg -> ((SystemMessage) msg).getText())
                .findFirst()
                .orElse("");
    }
}
