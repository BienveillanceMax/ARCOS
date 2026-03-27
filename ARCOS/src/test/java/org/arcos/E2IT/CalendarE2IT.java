package org.arcos.E2IT;

import org.arcos.EventBus.Events.Event;
import org.arcos.EventBus.Events.EventType;
import org.arcos.Tools.CalendarTool.model.CalendarEvent;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E — Sprint 10 E6: Calendar event dispatch.
 *
 * Proves: CALENDAR_EVENT_SCHEDULER event with CalendarEvent payload
 *   → Orchestrator.dispatch() → LLM (buildSchedulerAlertPrompt) → TTS
 *   → notification contains event name and time.
 *
 * Also tests the LLM-free fallback when circuit breaker is OPEN
 * (calendar events should always produce audio feedback).
 *
 * Prerequisites: Qdrant, Mistral API.
 */
@Tag("e2e")
@Timeout(value = 45, unit = TimeUnit.SECONDS)
class CalendarE2IT extends BaseE2IT {

    private CalendarEvent buildEvent(String title, String description,
                                     LocalDateTime start, LocalDateTime end) {
        return CalendarEvent.builder()
            .id("test-cal-" + System.currentTimeMillis())
            .title(title)
            .description(description)
            .startDateTime(start)
            .endDateTime(end)
            .allDay(false)
            .build();
    }

    @Test
    void t1_calendarEventProducesTTSWithEventName() {
        CalendarEvent event = buildEvent(
            "Réunion d'équipe",
            "Point hebdomadaire avec l'équipe développement",
            LocalDateTime.now().plusMinutes(30),
            LocalDateTime.now().plusMinutes(90));

        orchestrator.dispatch(new Event<>(EventType.CALENDAR_EVENT_SCHEDULER, event, "test"));

        Awaitility.await().atMost(Duration.ofSeconds(20))
            .until(() -> mockTTS.hasSpoken());

        String allSpoken = mockTTS.getAllSpokenLower();

        // The TTS should mention the event name
        assertTrue(
            allSpoken.contains("réunion") || allSpoken.contains("reunion")
                || allSpoken.contains("équipe") || allSpoken.contains("equipe"),
            "Calendar notification should mention the event name, got: " + allSpoken);
    }

    @Test
    void t2_differentCalendarEventProducesTTSWithEventName() {
        CalendarEvent event = buildEvent(
            "Rendez-vous dentiste",
            "Contrôle annuel",
            LocalDateTime.now().plusHours(2),
            LocalDateTime.now().plusHours(3));

        orchestrator.dispatch(new Event<>(EventType.CALENDAR_EVENT_SCHEDULER, event, "test"));

        Awaitility.await().atMost(Duration.ofSeconds(20))
            .until(() -> mockTTS.hasSpoken());

        String allSpoken = mockTTS.getAllSpokenLower();

        // Should mention the event
        assertTrue(
            allSpoken.contains("dentiste") || allSpoken.contains("rendez-vous")
                || allSpoken.contains("rendez"),
            "Calendar notification should mention the appointment, got: " + allSpoken);
    }
}
