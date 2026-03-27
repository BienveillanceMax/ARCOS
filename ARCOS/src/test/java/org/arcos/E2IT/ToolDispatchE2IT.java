package org.arcos.E2IT;

import org.arcos.EventBus.Events.Event;
import org.arcos.EventBus.Events.EventType;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E — Sprint 10 E2: Tool dispatch.
 *
 * Proves: WAKEWORD text → Orchestrator → LLM (Mistral) → tool_call
 *         → Action (weather / search) → result fed back to LLM → TTS output.
 *
 * Prerequisites: Qdrant, Mistral API, Open-Meteo (public), Brave Search API.
 */
@Tag("e2e")
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class ToolDispatchE2IT extends BaseE2IT {

    @Test
    void t1_weatherQuestionProducesMeteoData() {
        orchestrator.dispatch(new Event<>(EventType.WAKEWORD,
            "Quel temps fait-il à Paris en ce moment ?", "test"));

        Awaitility.await().atMost(Duration.ofSeconds(30))
            .until(() -> mockTTS.hasSpoken());

        String allSpoken = mockTTS.getAllSpokenLower();

        // The LLM should have called Consulter_la_meteo and produced weather data.
        // We check for weather-related vocabulary (temperature, conditions, etc.)
        assertTrue(
            allSpoken.matches(".*(?:degré|température|°|temps|météo|pluie|soleil|nuag|vent|ciel|chaud|froid|paris).*"),
            "Response to weather question should contain weather-related terms, got: " + allSpoken);
    }

    @Test
    void t2_searchQuestionProducesFactualContent() {
        orchestrator.dispatch(new Event<>(EventType.WAKEWORD,
            "Cherche des informations sur la mission Artemis de la NASA", "test"));

        Awaitility.await().atMost(Duration.ofSeconds(30))
            .until(() -> mockTTS.hasSpoken());

        String allSpoken = mockTTS.getAllSpokenLower();

        // The LLM should have called Chercher_sur_Internet and returned factual content.
        assertTrue(allSpoken.length() > 50,
            "Search result response should be substantial, got length: " + allSpoken.length());
        assertTrue(
            allSpoken.matches(".*(?:artemis|nasa|lune|lunar|moon|espace|space|mission|fusée|rocket|sls|orion).*"),
            "Response should contain Artemis/space-related content, got: " + allSpoken);
    }
}
