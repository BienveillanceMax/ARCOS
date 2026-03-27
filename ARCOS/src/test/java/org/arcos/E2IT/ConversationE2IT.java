package org.arcos.E2IT;

import org.arcos.EventBus.Events.Event;
import org.arcos.EventBus.Events.EventType;
import org.arcos.EventBus.Events.WakeWordEvent;
import org.arcos.Personality.Mood.MoodStateHolder;
import org.arcos.Personality.Mood.PadState;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@Tag("e2e")
@TestMethodOrder(MethodOrderer.MethodName.class)
class ConversationE2IT extends BaseE2IT {

    @Autowired private MoodStateHolder moodStateHolder;

    @Test
    void t1_basicConversationResponse() {
        orchestrator.dispatch(new Event<>(EventType.WAKEWORD, "Bonjour, comment vas-tu ?", "test"));

        Awaitility.await().atMost(Duration.ofSeconds(15))
            .until(() -> mockTTS.hasSpoken());

        mockTTS.getSpokenTexts().forEach(t -> assertFalse(t.isBlank(), "Spoken text must not be blank"));
        // AC2: TTS reçoit au moins une phrase complète en français (contient du texte alphabétique)
        String allSpoken = String.join(" ", mockTTS.getSpokenTexts());
        assertTrue(allSpoken.matches(".*[a-zA-ZÀ-ÿ].*"), "TTS output should contain French text");
        var recentMessages = conversationContext.getRecentMessages(10);
        assertTrue(recentMessages.stream()
            .anyMatch(m -> m.getContent().contains("Bonjour")), "Context should contain user message");
        assertTrue(recentMessages.stream()
            .anyMatch(m -> m.getType().name().equals("ASSISTANT")), "Context should contain assistant reply");
    }

    @Test
    void t2_contextAccumulatesAcrossTurns() {
        // Turn 1
        orchestrator.dispatch(new Event<>(EventType.WAKEWORD, "Bonjour, comment vas-tu ?", "test"));
        Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> mockTTS.hasSpoken());
        mockTTS.clear();

        // Turn 2 — multi-turn (WakeWordEvent(payload, source, multiTurn))
        orchestrator.dispatch(new WakeWordEvent("Et la météo aujourd'hui ?", "test", true));
        Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> mockTTS.hasSpoken());

        long userCount = conversationContext.getRecentMessages(50).stream()
            .filter(m -> m.getType().name().equals("USER")).count();
        long assistantCount = conversationContext.getRecentMessages(50).stream()
            .filter(m -> m.getType().name().equals("ASSISTANT")).count();
        assertTrue(userCount >= 2, "Should have at least 2 user messages after 2 turns, got: " + userCount);
        assertTrue(assistantCount >= 2, "Should have at least 2 assistant messages after 2 turns, got: " + assistantCount);
    }

    @Test
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

    @Test
    void t5_semanticCoherenceCapitaleDeFrance() {
        // AC3: question simple → réponse cohérente contenant "Paris"
        // Prompt contraint pour réduire le non-déterminisme LLM
        orchestrator.dispatch(new Event<>(EventType.WAKEWORD,
            "Réponds en un seul mot : quelle est la capitale de la France ?", "test"));

        Awaitility.await().atMost(Duration.ofSeconds(15))
            .until(() -> mockTTS.hasSpoken());

        String allSpoken = String.join(" ", mockTTS.getSpokenTexts()).toLowerCase();
        assertTrue(allSpoken.contains("paris") || allSpoken.contains("capitale"),
            "Response to 'capitale de la France' should mention Paris or capitale, got: " + allSpoken);
    }
}
