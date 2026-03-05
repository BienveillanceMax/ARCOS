package org.arcos.UnitTests.UserModel;

import org.arcos.Memory.ConversationContext;
import org.arcos.Memory.ConversationSummaryService;
import org.arcos.LLM.Prompts.PromptBuilder;
import org.arcos.Personality.Values.ValueProfile;
import org.arcos.UserModel.Models.UserProfileContext;
import org.arcos.UserModel.Retrieval.UserModelRetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PromptBuilderUserProfileTest {

    @Mock
    private ValueProfile valueProfile;
    @Mock
    private ConversationSummaryService conversationSummaryService;
    @Mock
    private UserModelRetrievalService retrievalService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(valueProfile.getStrongValues()).thenReturn(Collections.emptyMap());
        when(valueProfile.getSuppressedValues()).thenReturn(Collections.emptyMap());
        when(conversationSummaryService.getSummary()).thenReturn("");
    }

    @Test
    void buildConversationnalPrompt_WithUserProfile_ShouldInjectProfileSection() {
        when(retrievalService.retrieveUserContext(anyString()))
                .thenReturn(new UserProfileContext("Pierre, développeur", "Concis et direct", null, 5));

        PromptBuilder builder = new PromptBuilder(valueProfile, conversationSummaryService, 3, true, retrievalService);
        ConversationContext context = new ConversationContext();

        Prompt prompt = builder.buildConversationnalPrompt(context, "Salut");

        String systemContent = getSystemContent(prompt);
        assertTrue(systemContent.contains("## Profil utilisateur"));
        assertTrue(systemContent.contains("Pierre, développeur"));
        assertTrue(systemContent.contains("Concis et direct"));
        assertTrue(systemContent.contains("adaptes tes réponses"));
    }

    @Test
    void buildConversationnalPrompt_ColdStart_ShouldNotInjectProfile() {
        when(retrievalService.retrieveUserContext(anyString()))
                .thenReturn(new UserProfileContext(null, null, null, 2));

        PromptBuilder builder = new PromptBuilder(valueProfile, conversationSummaryService, 3, true, retrievalService);
        ConversationContext context = new ConversationContext();

        Prompt prompt = builder.buildConversationnalPrompt(context, "Salut");

        String systemContent = getSystemContent(prompt);
        assertFalse(systemContent.contains("## Profil utilisateur"));
    }

    @Test
    void buildConversationnalPrompt_UserModelDisabled_ShouldNotInjectProfile() {
        PromptBuilder builder = new PromptBuilder(valueProfile, conversationSummaryService, 3, false, null);
        ConversationContext context = new ConversationContext();

        Prompt prompt = builder.buildConversationnalPrompt(context, "Salut");

        String systemContent = getSystemContent(prompt);
        assertFalse(systemContent.contains("## Profil utilisateur"));
        verifyNoInteractions(retrievalService);
    }

    @Test
    void buildConversationnalPrompt_WithOnDemandLeaf_ShouldIncludeIt() {
        when(retrievalService.retrieveUserContext(anyString()))
                .thenReturn(new UserProfileContext("Pierre", null, "Mon créateur s'intéresse à l'IA", 10));

        PromptBuilder builder = new PromptBuilder(valueProfile, conversationSummaryService, 3, true, retrievalService);
        ConversationContext context = new ConversationContext();

        Prompt prompt = builder.buildConversationnalPrompt(context, "Parle-moi d'IA");

        String systemContent = getSystemContent(prompt);
        assertTrue(systemContent.contains("Mon créateur s'intéresse à l'IA"));
    }

    @Test
    void buildConversationnalPrompt_NullRetrievalService_ShouldNotFail() {
        PromptBuilder builder = new PromptBuilder(valueProfile, conversationSummaryService, 3, true, null);
        ConversationContext context = new ConversationContext();

        Prompt prompt = builder.buildConversationnalPrompt(context, "Salut");

        String systemContent = getSystemContent(prompt);
        assertFalse(systemContent.contains("## Profil utilisateur"));
    }

    private String getSystemContent(Prompt prompt) {
        return prompt.getInstructions().stream()
                .filter(msg -> msg instanceof SystemMessage)
                .map(msg -> ((SystemMessage) msg).getText())
                .findFirst().orElse("");
    }
}
