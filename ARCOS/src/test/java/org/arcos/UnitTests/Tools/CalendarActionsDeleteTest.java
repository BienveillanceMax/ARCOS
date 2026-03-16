package org.arcos.UnitTests.Tools;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import org.arcos.Tools.Actions.ActionResult;
import org.arcos.Tools.Actions.CalendarActions;
import org.arcos.Tools.CalendarTool.CalendarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalendarActionsDeleteTest {

    @Mock
    private CalendarService calendarService;

    private CalendarActions calendarActions;

    @BeforeEach
    void setUp() {
        calendarActions = new CalendarActions(calendarService);
    }

    // --- Helper to build a Google Calendar Event ---

    private Event makeTimedEvent(String id, String summary, LocalDate date, int hour, int minute) {
        long epochMillis = date.atTime(hour, minute)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        Event event = new Event();
        event.setId(id);
        event.setSummary(summary);
        event.setStart(new EventDateTime().setDateTime(new DateTime(epochMillis)));
        return event;
    }

    private Event makeAllDayEvent(String id, String summary, LocalDate date) {
        Event event = new Event();
        event.setId(id);
        event.setSummary(summary);
        event.setStart(new EventDateTime().setDate(new DateTime(date.toString())));
        return event;
    }

    private Event makeNullSummaryEvent(String id, LocalDate date, int hour, int minute) {
        long epochMillis = date.atTime(hour, minute)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        Event event = new Event();
        event.setId(id);
        event.setSummary(null);
        event.setStart(new EventDateTime().setDateTime(new DateTime(epochMillis)));
        return event;
    }

    // ==================== Service unavailable ====================

    @Test
    void delete_WhenServiceUnavailable_ShouldReturnFailure() {
        // Given
        when(calendarService.isAvailable()).thenReturn(false);

        // When
        ActionResult result = calendarActions.deleteCalendarEvent("réunion", null);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("autorisation Google");
    }

    // ==================== Exact title match (backward compat) ====================

    @Test
    void delete_ExactTitleMatchToday_ShouldDeleteAndSucceed() throws Exception {
        // Given
        when(calendarService.isAvailable()).thenReturn(true);
        LocalDate today = LocalDate.now();
        Event event = makeTimedEvent("ev1", "Réunion d'équipe", today, 10, 0);
        when(calendarService.searchEvents("Réunion d'équipe", 20)).thenReturn(List.of(event));

        // When
        ActionResult result = calendarActions.deleteCalendarEvent("Réunion d'équipe", null);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("Réunion d'équipe");
        verify(calendarService).deleteEvent("ev1");
    }

    // ==================== Partial / fuzzy title match ====================

    @Test
    void delete_PartialTitleMatch_ShouldFindAndDelete() throws Exception {
        // Given
        when(calendarService.isAvailable()).thenReturn(true);
        LocalDate today = LocalDate.now();
        Event event = makeTimedEvent("ev2", "Réunion d'équipe hebdomadaire", today, 10, 0);
        when(calendarService.searchEvents("réunion", 20)).thenReturn(List.of(event));

        // When
        ActionResult result = calendarActions.deleteCalendarEvent("réunion", null);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("Réunion d'équipe hebdomadaire");
        verify(calendarService).deleteEvent("ev2");
    }

    @Test
    void delete_CaseInsensitiveMatch_ShouldFindAndDelete() throws Exception {
        // Given
        when(calendarService.isAvailable()).thenReturn(true);
        Event event = makeTimedEvent("ev3", "DÉJEUNER IMPORTANT", LocalDate.now(), 12, 30);
        when(calendarService.searchEvents("déjeuner", 20)).thenReturn(List.of(event));

        // When
        ActionResult result = calendarActions.deleteCalendarEvent("déjeuner", null);

        // Then
        assertThat(result.isSuccess()).isTrue();
        verify(calendarService).deleteEvent("ev3");
    }

    // ==================== Date filtering ====================

    @Test
    void delete_WithFutureDate_ShouldFilterByDate() throws Exception {
        // Given
        when(calendarService.isAvailable()).thenReturn(true);
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDate dayAfter = LocalDate.now().plusDays(2);
        Event tomorrowEvent = makeTimedEvent("ev4", "Réunion demain", tomorrow, 14, 0);
        Event dayAfterEvent = makeTimedEvent("ev5", "Réunion après-demain", dayAfter, 14, 0);
        when(calendarService.searchEvents("Réunion", 20)).thenReturn(List.of(tomorrowEvent, dayAfterEvent));

        // When
        ActionResult result = calendarActions.deleteCalendarEvent("Réunion", tomorrow.toString());

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("Réunion demain");
        verify(calendarService).deleteEvent("ev4");
        verify(calendarService, never()).deleteEvent("ev5");
    }

    @Test
    void delete_WithDateNoMatch_ShouldReturnFailureWithEventList() throws Exception {
        // Given
        when(calendarService.isAvailable()).thenReturn(true);
        LocalDate targetDate = LocalDate.of(2026, 3, 20);
        // searchEvents returns events on a different date
        Event wrongDateEvent = makeTimedEvent("ev6", "Réunion", LocalDate.of(2026, 3, 21), 10, 0);
        when(calendarService.searchEvents("standup", 20)).thenReturn(List.of(wrongDateEvent));
        // Fallback: listEventsBetweenDates returns events for that day
        Event dayEvent = makeTimedEvent("ev7", "Déjeuner client", targetDate, 12, 30);
        when(calendarService.listEventsBetweenDates(
                eq(targetDate.atStartOfDay()), eq(targetDate.atTime(LocalTime.MAX)), eq(20)))
                .thenReturn(List.of(dayEvent));

        // When
        ActionResult result = calendarActions.deleteCalendarEvent("standup", "2026-03-20");

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("standup");
        assertThat(result.getMessage()).contains("20/03/2026");
        assertThat(result.getMessage()).contains("Déjeuner client");
    }

    @Test
    void delete_WithDateNoEventsAtAll_ShouldReturnNoEventsMessage() throws Exception {
        // Given
        when(calendarService.isAvailable()).thenReturn(true);
        LocalDate targetDate = LocalDate.of(2026, 3, 20);
        when(calendarService.searchEvents("réunion", 20)).thenReturn(Collections.emptyList());
        when(calendarService.listEventsBetweenDates(any(), any(), eq(20)))
                .thenReturn(Collections.emptyList());

        // When
        ActionResult result = calendarActions.deleteCalendarEvent("réunion", "2026-03-20");

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Aucun événement ce jour");
    }

    // ==================== No date provided → no date filter ====================

    @Test
    void delete_NoDate_ShouldSearchWithoutDateFilter() throws Exception {
        // Given
        when(calendarService.isAvailable()).thenReturn(true);
        Event futureEvent = makeTimedEvent("ev8", "Dentiste", LocalDate.now().plusDays(5), 9, 0);
        when(calendarService.searchEvents("Dentiste", 20)).thenReturn(List.of(futureEvent));

        // When
        ActionResult result = calendarActions.deleteCalendarEvent("Dentiste", null);

        // Then
        assertThat(result.isSuccess()).isTrue();
        verify(calendarService).deleteEvent("ev8");
    }

    @Test
    void delete_EmptyDateStr_ShouldSearchWithoutDateFilter() throws Exception {
        // Given
        when(calendarService.isAvailable()).thenReturn(true);
        Event event = makeTimedEvent("ev9", "Yoga", LocalDate.now().plusDays(2), 18, 0);
        when(calendarService.searchEvents("Yoga", 20)).thenReturn(List.of(event));

        // When
        ActionResult result = calendarActions.deleteCalendarEvent("Yoga", "  ");

        // Then
        assertThat(result.isSuccess()).isTrue();
        verify(calendarService).deleteEvent("ev9");
    }

    @Test
    void delete_NoDateNoMatch_ShouldReturnGenericFailure() throws Exception {
        // Given
        when(calendarService.isAvailable()).thenReturn(true);
        when(calendarService.searchEvents("inexistant", 20)).thenReturn(Collections.emptyList());

        // When
        ActionResult result = calendarActions.deleteCalendarEvent("inexistant", null);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("inexistant");
        assertThat(result.getMessage()).doesNotContain("Événements ce jour");
    }

    // ==================== Null summary guard ====================

    @Test
    void delete_EventWithNullSummary_ShouldNotThrowNPE() throws Exception {
        // Given
        when(calendarService.isAvailable()).thenReturn(true);
        Event nullSummaryEvent = makeNullSummaryEvent("ev10", LocalDate.now(), 10, 0);
        Event validEvent = makeTimedEvent("ev11", "Ma réunion", LocalDate.now(), 11, 0);
        when(calendarService.searchEvents("réunion", 20)).thenReturn(List.of(nullSummaryEvent, validEvent));

        // When
        ActionResult result = calendarActions.deleteCalendarEvent("réunion", null);

        // Then — should skip null-summary event and match the valid one
        assertThat(result.isSuccess()).isTrue();
        verify(calendarService).deleteEvent("ev11");
    }

    @Test
    void delete_OnlyNullSummaryEvents_ShouldReturnFailureNotNPE() throws Exception {
        // Given
        when(calendarService.isAvailable()).thenReturn(true);
        Event nullSummaryEvent = makeNullSummaryEvent("ev12", LocalDate.now(), 10, 0);
        when(calendarService.searchEvents("test", 20)).thenReturn(List.of(nullSummaryEvent));

        // When
        ActionResult result = calendarActions.deleteCalendarEvent("test", null);

        // Then
        assertThat(result.isSuccess()).isFalse();
        verify(calendarService, never()).deleteEvent(anyString());
    }

    // ==================== All-day events ====================

    @Test
    void delete_AllDayEvent_ShouldMatchByDate() throws Exception {
        // Given
        when(calendarService.isAvailable()).thenReturn(true);
        LocalDate targetDate = LocalDate.of(2026, 3, 20);
        Event allDayEvent = makeAllDayEvent("ev13", "Vacances", targetDate);
        when(calendarService.searchEvents("Vacances", 20)).thenReturn(List.of(allDayEvent));

        // When
        ActionResult result = calendarActions.deleteCalendarEvent("Vacances", "2026-03-20");

        // Then
        assertThat(result.isSuccess()).isTrue();
        verify(calendarService).deleteEvent("ev13");
    }

    // ==================== Invalid date format ====================

    @Test
    void delete_InvalidDateFormat_ShouldFallbackToNoDateFilter() throws Exception {
        // Given
        when(calendarService.isAvailable()).thenReturn(true);
        Event event = makeTimedEvent("ev14", "Cours", LocalDate.now(), 16, 0);
        when(calendarService.searchEvents("Cours", 20)).thenReturn(List.of(event));

        // When
        ActionResult result = calendarActions.deleteCalendarEvent("Cours", "not-a-date");

        // Then — should not fail, just search without date filter
        assertThat(result.isSuccess()).isTrue();
        verify(calendarService).deleteEvent("ev14");
    }

    // ==================== Failure message lists events with time ====================

    @Test
    void delete_NoMatchWithDate_ShouldListEventsWithFormattedTime() throws Exception {
        // Given
        when(calendarService.isAvailable()).thenReturn(true);
        LocalDate targetDate = LocalDate.of(2026, 3, 17);
        when(calendarService.searchEvents("xyz", 20)).thenReturn(Collections.emptyList());
        Event event1 = makeTimedEvent("ev15", "Réunion d'équipe hebdomadaire", targetDate, 10, 0);
        Event event2 = makeTimedEvent("ev16", "Déjeuner", targetDate, 12, 30);
        when(calendarService.listEventsBetweenDates(any(), any(), eq(20)))
                .thenReturn(List.of(event1, event2));

        // When
        ActionResult result = calendarActions.deleteCalendarEvent("xyz", "2026-03-17");

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Réunion d'équipe hebdomadaire");
        assertThat(result.getMessage()).contains("Déjeuner");
        assertThat(result.getMessage()).contains("10h00");
        assertThat(result.getMessage()).contains("12h30");
    }

    @Test
    void delete_NoMatchWithDate_NullSummaryInList_ShouldShowSansTitre() throws Exception {
        // Given
        when(calendarService.isAvailable()).thenReturn(true);
        LocalDate targetDate = LocalDate.of(2026, 3, 17);
        when(calendarService.searchEvents("xyz", 20)).thenReturn(Collections.emptyList());
        Event nullEvent = makeNullSummaryEvent("ev17", targetDate, 14, 0);
        when(calendarService.listEventsBetweenDates(any(), any(), eq(20)))
                .thenReturn(List.of(nullEvent));

        // When
        ActionResult result = calendarActions.deleteCalendarEvent("xyz", "2026-03-17");

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("(sans titre)");
    }

    // ==================== CalendarService exception ====================

    @Test
    void delete_WhenSearchThrows_ShouldReturnFailure() throws Exception {
        // Given
        when(calendarService.isAvailable()).thenReturn(true);
        when(calendarService.searchEvents(anyString(), anyInt()))
                .thenThrow(new RuntimeException("API error"));

        // When
        ActionResult result = calendarActions.deleteCalendarEvent("test", null);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("API error");
    }

    // ==================== Success message uses actual title ====================

    @Test
    void delete_ShouldReturnActualEventTitle_NotSearchTerm() throws Exception {
        // Given
        when(calendarService.isAvailable()).thenReturn(true);
        Event event = makeTimedEvent("ev18", "Réunion d'équipe hebdomadaire", LocalDate.now(), 10, 0);
        when(calendarService.searchEvents("réunion", 20)).thenReturn(List.of(event));

        // When
        ActionResult result = calendarActions.deleteCalendarEvent("réunion", null);

        // Then — message should contain the full title, not the search term
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("Réunion d'équipe hebdomadaire");
    }
}
