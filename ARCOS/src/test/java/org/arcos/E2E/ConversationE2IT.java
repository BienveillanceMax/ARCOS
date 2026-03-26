package org.arcos.E2E;

import org.arcos.EventBus.Events.Event;
import org.arcos.EventBus.Events.EventType;
import org.arcos.EventBus.Events.WakeWordEvent;
import org.arcos.Memory.ConversationContext;
import org.arcos.Personality.Mood.MoodStateHolder;
import org.arcos.Personality.Mood.PadState;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ConversationE2IT extends BaseE2IT {

    @Autowired private ConversationContext context;
    @Autowired private MoodStateHolder moodStateHolder;

    @Test
    @Tag("requires-llm")
    void t1_basicConversationResponse() {
        orchestrator.dispatch(new Event<>(EventType.WAKEWORD, "Bonjour, comment vas-tu ?", "test"));

        Awaitility.await().atMost(Duration.ofSeconds(15))
            .until(() -> mockTTS.hasSpoken());

        assertTrue(mockTTS.hasSpoken(), "TTS should have been called");
        mockTTS.getSpokenTexts().forEach(t -> assertFalse(t.isBlank(), "Spoken text must not be blank"));
        // getRecentMessages(int) returns List<ConversationMessage>
        assertTrue(context.getRecentMessages(10).stream()
            .anyMatch(m -> m.getContent().contains("Bonjour")), "Context should contain user message");
        assertTrue(context.getRecentMessages(10).stream()
            .anyMatch(m -> m.getType().name().equals("ASSISTANT")), "Context should contain assistant reply");
    }

    @Test
    @Tag("requires-llm")
    void t2_contextAccumulatesAcrossTurns() {
        // Turn 1
        orchestrator.dispatch(new Event<>(EventType.WAKEWORD, "Bonjour, comment vas-tu ?", "test"));
        Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> mockTTS.hasSpoken());
        mockTTS.clear();

        // Turn 2 — multi-turn (WakeWordEvent(payload, source, multiTurn))
        orchestrator.dispatch(new WakeWordEvent("Et la météo aujourd'hui ?", "test", true));
        Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> mockTTS.hasSpoken());

        long userCount = context.getRecentMessages(50).stream()
            .filter(m -> m.getType().name().equals("USER")).count();
        long assistantCount = context.getRecentMessages(50).stream()
            .filter(m -> m.getType().name().equals("ASSISTANT")).count();
        assertTrue(userCount >= 2, "Should have at least 2 user messages after 2 turns, got: " + userCount);
        assertTrue(assistantCount >= 2, "Should have at least 2 assistant messages after 2 turns, got: " + assistantCount);
    }

    @Test
    @Tag("requires-llm")
    void t3_asyncMoodUpdateChangesPadState() {
        PadState before = moodStateHolder.getPadState();
        double initialPleasure = before.getPleasure();
        double initialArousal = before.getArousal();

        orchestrator.dispatch(new Event<>(EventType.WAKEWORD, "J'adore vraiment la musique jazz !", "test"));

        Awaitility.await().atMost(Duration.ofSeconds(10))
            .until(() -> mockTTS.hasSpoken());

        // Wait for async mood update
        Awaitility.await().atMost(Duration.ofSeconds(8))
            .until(() -> {
                PadState current = moodStateHolder.getPadState();
                return current.getPleasure() != initialPleasure
                    || current.getArousal() != initialArousal;
            });

        PadState after = moodStateHolder.getPadState();
        assertNotEquals(initialPleasure, after.getPleasure(),
            "PAD state should have changed after mood update");
    }

    @Test
    @Tag("requires-llm")
    void t4_ttsOutputContainsNoMarkdown() {
        orchestrator.dispatch(new Event<>(EventType.WAKEWORD,
            "Donne-moi un exemple avec des astérisques et du gras en markdown", "test"));

        Awaitility.await().atMost(Duration.ofSeconds(15))
            .until(() -> mockTTS.hasSpoken());

        // cleanForTTS() in Orchestrator strips * and # before speaking
        mockTTS.getSpokenTexts().forEach(text -> {
            assertFalse(text.contains("*"),  "Spoken text must not contain asterisks: " + text);
            assertFalse(text.contains("##"), "Spoken text must not contain ## headers: " + text);
        });
    }
}
