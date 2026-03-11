package org.arcos.UnitTests.UserModel;

import org.arcos.LLM.Prompts.PromptBuilder;
import org.arcos.Memory.ConversationSummaryService;
import org.arcos.Personality.Values.ValueProfile;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour PromptBuilder — Channel B (UM-004).
 * Verifie que buildMemoryPrompt() inclut les instructions d'extraction
 * d'observations utilisateur quand userModelEnabled=true, et les exclut sinon.
 */
class PromptBuilderChannelBTest {

    @Mock
    private ConversationSummaryService conversationSummaryService;

    // ===== T1 : userModelEnabled=true -> instructions presentes =====

    @Test
    void buildMemoryPrompt_ShouldIncludeObservationInstructions_WhenUserModelEnabled() {
        MockitoAnnotations.openMocks(this);
        when(conversationSummaryService.getSummary()).thenReturn("");

        ValueProfile valueProfile = new ValueProfile();
        PromptBuilder promptBuilder = new PromptBuilder(valueProfile, conversationSummaryService, 3, true, null);

        Prompt prompt = promptBuilder.buildMemoryPrompt("USER: Je suis developpeur. ASSISTANT: Interessant!");
        String systemContent = getSystemContent(prompt);

        assertTrue(systemContent.contains("## Observations utilisateur"),
                "Doit contenir l'en-tete des observations utilisateur");
        assertTrue(systemContent.contains("userObservations"),
                "Doit mentionner le champ userObservations");
        assertTrue(systemContent.contains("Mon createur") || systemContent.contains("Mon créateur"),
                "Doit contenir la regle 'Mon createur...'");
        assertTrue(systemContent.contains("IDENTITE"),
                "Doit lister les branches dont IDENTITE");
        assertTrue(systemContent.contains("COMMUNICATION"),
                "Doit lister les branches dont COMMUNICATION");
        assertTrue(systemContent.contains("HABITUDES"),
                "Doit lister les branches dont HABITUDES");
        assertTrue(systemContent.contains("OBJECTIFS"),
                "Doit lister les branches dont OBJECTIFS");
        assertTrue(systemContent.contains("EMOTIONS"),
                "Doit lister les branches dont EMOTIONS");
        assertTrue(systemContent.contains("INTERETS"),
                "Doit lister les branches dont INTERETS");
        assertTrue(systemContent.contains("explicite"),
                "Doit mentionner le champ explicite");
        assertTrue(systemContent.contains("remplace"),
                "Doit mentionner le champ remplace");
    }

    // ===== T2 : userModelEnabled=false -> instructions absentes =====

    @Test
    void buildMemoryPrompt_ShouldNotIncludeObservationInstructions_WhenUserModelDisabled() {
        MockitoAnnotations.openMocks(this);
        when(conversationSummaryService.getSummary()).thenReturn("");

        ValueProfile valueProfile = new ValueProfile();
        PromptBuilder promptBuilder = new PromptBuilder(valueProfile, conversationSummaryService, 3, false, null);

        Prompt prompt = promptBuilder.buildMemoryPrompt("USER: Bonjour. ASSISTANT: Salut!");
        String systemContent = getSystemContent(prompt);

        assertFalse(systemContent.contains("## Observations utilisateur"),
                "Ne doit pas contenir les instructions d'observation quand desactive");
        assertFalse(systemContent.contains("userObservations"),
                "Ne doit pas mentionner userObservations quand desactive");
    }

    // ===== T3 : les regles memoire standard sont toujours presentes =====

    @Test
    void buildMemoryPrompt_ShouldAlwaysIncludeMemoryRules_RegardlessOfUserModelEnabled() {
        MockitoAnnotations.openMocks(this);
        when(conversationSummaryService.getSummary()).thenReturn("");

        ValueProfile valueProfile = new ValueProfile();

        // Avec userModelEnabled=true
        PromptBuilder enabledBuilder = new PromptBuilder(valueProfile, conversationSummaryService, 3, true, null);
        Prompt enabledPrompt = enabledBuilder.buildMemoryPrompt("USER: test ASSISTANT: test");
        String enabledContent = getSystemContent(enabledPrompt);

        // Avec userModelEnabled=false
        PromptBuilder disabledBuilder = new PromptBuilder(valueProfile, conversationSummaryService, 3, false, null);
        Prompt disabledPrompt = disabledBuilder.buildMemoryPrompt("USER: test ASSISTANT: test");
        String disabledContent = getSystemContent(disabledPrompt);

        // Les deux doivent contenir les regles memoire standard
        assertTrue(enabledContent.contains("RÈGLES:"),
                "Les regles memoire doivent etre presentes quand userModel active");
        assertTrue(disabledContent.contains("RÈGLES:"),
                "Les regles memoire doivent etre presentes quand userModel desactive");
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
