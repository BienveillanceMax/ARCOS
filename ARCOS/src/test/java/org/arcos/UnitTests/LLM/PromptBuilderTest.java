package org.arcos.UnitTests.LLM;

import org.arcos.LLM.Prompts.PromptBuilder;
import org.arcos.Memory.ConversationContext;
import org.arcos.Memory.ConversationSummaryService;
import org.arcos.Personality.Values.ValueProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour PromptBuilder — section "Contexte de la Conversation" (STORY-RCS-002).
 * Vérifie que generateContextDescription() utilise le résumé roulant + N messages récents bruts.
 */
class PromptBuilderTest {

    @Mock
    private ConversationSummaryService conversationSummaryService;

    private PromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // ValueProfile avec profil DEFAULT (scores à 50.0) — pas de valeurs dominantes/supprimées
        ValueProfile valueProfile = new ValueProfile();
        promptBuilder = new PromptBuilder(valueProfile, conversationSummaryService, 3, true, null);
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

        assertTrue(systemContent.contains("**Résumé de la session :**"),
                "Doit contenir l'en-tête du résumé");
        assertTrue(systemContent.contains("L'utilisateur aime les films d'action"),
                "Doit contenir le texte du résumé");
        assertTrue(systemContent.contains("**Derniers échanges :**"),
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

        assertFalse(systemContent.contains("**Résumé de la session :**"),
                "Ne doit pas contenir l'en-tête du résumé si résumé vide");
        assertTrue(systemContent.contains("**Derniers échanges :**"),
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

        assertTrue(systemContent.contains("**Résumé de la session :**"),
                "Doit contenir l'en-tête du résumé même si le contexte est vide");
        assertTrue(systemContent.contains("L'utilisateur est intéressé par les nouvelles technologies."),
                "Doit contenir le texte du résumé");
        assertFalse(systemContent.contains("**Derniers échanges :**"),
                "Ne doit pas contenir les derniers échanges si le contexte est vide");
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
