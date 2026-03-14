package org.arcos.Integration;

import org.arcos.Configuration.PersonalityProperties;
import org.arcos.LLM.Client.LLMClient;
import org.arcos.LLM.Prompts.PromptBuilder;
import org.arcos.Memory.ConversationContext;
import org.arcos.Memory.ConversationSummaryService;
import org.arcos.Personality.Mood.Mood;
import org.arcos.Personality.Mood.MoodService;
import org.arcos.Personality.Mood.MoodUpdate;
import org.arcos.Personality.Values.ValueProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.prompt.Prompt;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class MoodIntegrationTest {

    @Mock LLMClient llmClient;
    @Mock ConversationSummaryService conversationSummaryService;
    ValueProfile valueProfile;
    PromptBuilder promptBuilder;
    ConversationContext context;
    MoodService moodService;
    PersonalityProperties personalityProperties;

    @BeforeEach
    void setUp() {
        valueProfile = new ValueProfile(); // Real object
        promptBuilder = new PromptBuilder(valueProfile, conversationSummaryService, 3, true, null, null, null); // Real object
        context = new ConversationContext(); // Real object
        personalityProperties = new PersonalityProperties(); // Real object with defaults
        moodService = new MoodService(context, personalityProperties); // Service under test
    }

    @Test
    void testMoodPromptGeneration() {
        // Verify Prompt Builder includes mood section in the conversational prompt
        Prompt prompt = promptBuilder.buildConversationnalPrompt(context, "Hello");
        String promptContent = prompt.toString();

        assertTrue(promptContent.contains("Humeur:"),
                "Le prompt conversationnel doit contenir la section humeur courante");
        assertTrue(promptContent.contains(Mood.fromPadState(context.getPadState()).getLabel()),
                "Le prompt conversationnel doit contenir l'état émotionnel");
        assertTrue(promptContent.contains("Calcifer"),
                "Le prompt conversationnel doit contenir la personnalité Calcifer");
    }

    @Test
    void testMoodUpdateApplication() {
        // 2. Verify MoodService applies update correctly
        MoodUpdate update = new MoodUpdate();
        update.deltaPleasure = 0.8;
        update.deltaArousal = 0.6;
        update.deltaDominance = 0.5;

        moodService.applyMoodUpdate(update);

        assertEquals(Mood.JOIE, moodService.getCurrentMood());
    }
}
