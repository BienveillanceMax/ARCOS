package Integration;

import LLM.LLMClient;
import LLM.Prompts.PromptBuilder;
import Memory.ConversationContext;
import Personality.Mood.Mood;
import Personality.Mood.MoodService;
import Personality.Mood.MoodUpdate;
import Personality.Mood.PadState;
import Personality.Values.ValueProfile;
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
    ValueProfile valueProfile;
    PromptBuilder promptBuilder;
    ConversationContext context;
    MoodService moodService;

    @BeforeEach
    void setUp() {
        valueProfile = new ValueProfile(); // Real object
        promptBuilder = new PromptBuilder(valueProfile); // Real object
        context = new ConversationContext(); // Real object
        moodService = new MoodService(context); // Service under test
    }

    @Test
    void testMoodPromptGeneration() {
        // 1. Verify Prompt Builder includes JSON instructions
        Prompt prompt = promptBuilder.buildConversationnalPrompt(context, "Hello");
        String promptContent = prompt.toString();

        assertTrue(promptContent.contains("Tu dois répondre UNIQUEMENT au format JSON"));
        assertTrue(promptContent.contains("mood_update"));
        assertTrue(promptContent.contains("delta_pleasure"));
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
