package org.arcos.E2IT;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.arcos.EventBus.Events.Event;
import org.arcos.EventBus.Events.EventType;
import org.arcos.Tools.CalendarTool.model.CalendarEvent;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E — Sprint 10 E7: Dégradation gracieuse.
 *
 * Proves the circuit breaker protects ARCOS in real conditions:
 *   Phase 1: Circuit breaker forced OPEN → WAKEWORD → TTS feedback "indisponible" in < 10s
 *   Phase 2: Calendar event dispatched while CB OPEN → still produces TTS (non-LLM path)
 *   Phase 3: Circuit breaker reset to CLOSED → WAKEWORD → normal conversation
 *
 * This test manipulates the circuit breaker state programmatically via
 * CircuitBreakerRegistry — the CB itself is the real Resilience4j one,
 * not a mock. We just force its state transitions instead of waiting for
 * real failures to trip it.
 *
 * Prerequisites: Qdrant, Mistral API.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.MethodName.class)
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class GraciousDegradationE2IT extends BaseE2IT {

    // Mirrors Orchestrator.LLM_UNAVAILABLE_MESSAGE (package-private)
    private static final String EXPECTED_UNAVAILABLE_FRAGMENT = "indisponible";

    @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker getMistralCB() {
        return circuitBreakerRegistry.circuitBreaker("mistral_free");
    }

    @AfterEach
    void resetCircuitBreaker() {
        // Always restore CB to CLOSED so other tests aren't affected
        getMistralCB().transitionToClosedState();
    }

    @Test
    void t1_circuitBreakerOpenProducesFeedbackWithinTimeout() {
        getMistralCB().transitionToOpenState();

        orchestrator.dispatch(new Event<>(EventType.WAKEWORD, "Bonjour, ça va ?", "test"));

        // Awaitility enforces the 10s fail-fast bound
        Awaitility.await().atMost(Duration.ofSeconds(10))
            .until(() -> mockTTS.hasSpoken());

        String allSpoken = mockTTS.getAllSpokenLower();
        assertTrue(allSpoken.contains(EXPECTED_UNAVAILABLE_FRAGMENT),
            "TTS should contain the unavailability message, got: " + allSpoken);
    }

    @Test
    void t2_calendarEventStillWorksWhenCircuitBreakerOpen() {
        // Force circuit breaker OPEN
        getMistralCB().transitionToOpenState();

        CalendarEvent event = CalendarEvent.builder()
            .id("test-degrad")
            .title("Réunion importante")
            .description("Réunion de suivi projet")
            .startDateTime(LocalDateTime.now().plusMinutes(30))
            .endDateTime(LocalDateTime.now().plusMinutes(90))
            .allDay(false)
            .build();

        orchestrator.dispatch(new Event<>(EventType.CALENDAR_EVENT_SCHEDULER, event, "test"));

        Awaitility.await().atMost(Duration.ofSeconds(10))
            .until(() -> mockTTS.hasSpoken());

        String allSpoken = mockTTS.getAllSpokenLower();
        assertTrue(
            allSpoken.contains("réunion") || allSpoken.contains("reunion")
                || allSpoken.contains("rappel"),
            "Calendar event should still produce TTS when CB is OPEN (fallback path), got: " + allSpoken);
    }

    @Test
    void t3_recoveryFromOpenToClosedRestoresConversation() {
        // Force OPEN, then reset to CLOSED (simulates recovery)
        getMistralCB().transitionToOpenState();
        getMistralCB().transitionToClosedState();

        orchestrator.dispatch(new Event<>(EventType.WAKEWORD,
            "Réponds en un seul mot : quelle est la capitale de la France ?", "test"));

        Awaitility.await().atMost(Duration.ofSeconds(20))
            .until(() -> mockTTS.hasSpoken());

        String allSpoken = mockTTS.getAllSpokenLower();
        assertTrue(
            allSpoken.contains("paris") || allSpoken.contains("capitale"),
            "After CB recovery, LLM should respond normally, got: " + allSpoken);
    }
}
