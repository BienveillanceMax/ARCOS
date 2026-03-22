package org.arcos.UnitTests.Tools;

import org.arcos.Tools.Actions.ActionResult;
import org.arcos.Tools.Actions.CalendarActions;
import org.arcos.Tools.CalendarTool.CalDavCalendarService;
import org.arcos.Tools.CalendarTool.model.CalendarEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalendarActionsTest {

    @Mock
    private CalDavCalendarService calendarService;

    private CalendarActions calendarActions;

    @BeforeEach
    void setUp() {
        calendarActions = new CalendarActions(calendarService);
    }

    // ── Service indisponible ─────────────────────────────────────────────────

    @Test
    void addCalendarEvent_WhenServiceNotAvailable_ShouldReturnFailure() {
        // Given
        when(calendarService.isAvailable()).thenReturn(false);

        // When
        ActionResult result = calendarActions.AddCalendarEvent(
                "Test", "Description", "Paris", "2026-04-01T10:00:00", "2026-04-01T11:00:00");

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("non disponible");
    }

    @Test
    void listCalendarEvents_WhenServiceNotAvailable_ShouldReturnFailure() {
        // Given
        when(calendarService.isAvailable()).thenReturn(false);

        // When
        ActionResult result = calendarActions.listCalendarEvents(10);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("non disponible");
    }

    @Test
    void deleteCalendarEvent_WhenServiceNotAvailable_ShouldReturnFailure() {
        // Given
        when(calendarService.isAvailable()).thenReturn(false);

        // When
        ActionResult result = calendarActions.deleteCalendarEvent("Meeting", null);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("non disponible");
    }

    // ── Ajout d'événements ───────────────────────────────────────────────────

    @Test
    void addCalendarEvent_WithValidIsoDateTime_ShouldCreateEvent() throws Exception {
        // Given
        when(calendarService.isAvailable()).thenReturn(true);
        CalendarEvent mockEvent = CalendarEvent.builder()
                .id("uid-123")
                .title("Meeting")
                .description("Desc")
                .build();
        when(calendarService.createEvent(anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(mockEvent);

        // When
        ActionResult result = calendarActions.AddCalendarEvent(
                "Meeting", "Desc", "Paris", "2026-04-01T10:00:00", "2026-04-01T11:00:00");

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("succès");
        verify(calendarService).createEvent(eq("Meeting"), eq("Desc"), any(), any(), eq("Paris"));
    }

    @Test
    void addCalendarEvent_WithEndBeforeStart_ShouldReturnFailure() {
        // Given
        when(calendarService.isAvailable()).thenReturn(true);

        // When
        ActionResult result = calendarActions.AddCalendarEvent(
                "Meeting", "Desc", "Paris", "2026-04-01T14:00:00", "2026-04-01T10:00:00");

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("postérieure");
    }

    @Test
    void addCalendarEvent_WithEqualStartAndEnd_ShouldReturnFailure() {
        // Given
        when(calendarService.isAvailable()).thenReturn(true);

        // When
        ActionResult result = calendarActions.AddCalendarEvent(
                "Meeting", "Desc", "Paris", "2026-04-01T10:00:00", "2026-04-01T10:00:00");

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("postérieure");
    }

    @Test
    void addCalendarEvent_WithNullStartDate_ShouldReturnFailure() {
        // Given
        when(calendarService.isAvailable()).thenReturn(true);

        // When
        ActionResult result = calendarActions.AddCalendarEvent(
                "Meeting", "Desc", "Paris", null, "2026-04-01T11:00:00");

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("invalide");
    }

    @Test
    void addCalendarEvent_WithFrenchDateFormat_ShouldParseAndCreate() throws Exception {
        // Given
        when(calendarService.isAvailable()).thenReturn(true);
        CalendarEvent mockEvent = CalendarEvent.builder()
                .id("uid-456")
                .title("RDV")
                .description("Desc")
                .build();
        when(calendarService.createEvent(anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(mockEvent);

        // When
        ActionResult result = calendarActions.AddCalendarEvent(
                "RDV", "Desc", "Lyon", "01/04/2026 10:00", "01/04/2026 11:00");

        // Then
        assertThat(result.isSuccess()).isTrue();
        verify(calendarService).createEvent(eq("RDV"), eq("Desc"), any(), any(), eq("Lyon"));
    }

    @Test
    void addCalendarEvent_WithInvalidDateFormat_ShouldReturnFailure() {
        // Given
        when(calendarService.isAvailable()).thenReturn(true);

        // When
        ActionResult result = calendarActions.AddCalendarEvent(
                "Meeting", "Desc", "Paris", "pas-une-date", "2026-04-01T11:00:00");

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("invalide");
    }

    // ── Liste d'événements ──────────────────────────────────────────────────

    @Test
    void listCalendarEvents_WhenNoEvents_ShouldReturnSuccessMessage() throws Exception {
        // Given
        when(calendarService.isAvailable()).thenReturn(true);
        when(calendarService.listUpcomingEvents(10)).thenReturn(Collections.emptyList());

        // When
        ActionResult result = calendarActions.listCalendarEvents(10);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("Aucun événement");
    }

    @Test
    void listCalendarEvents_WithEvents_ShouldReturnFormattedList() throws Exception {
        // Given
        when(calendarService.isAvailable()).thenReturn(true);
        CalendarEvent event = CalendarEvent.builder()
                .title("Réunion")
                .startDateTime(java.time.LocalDateTime.of(2026, 4, 1, 10, 0))
                .build();
        when(calendarService.listUpcomingEvents(5)).thenReturn(List.of(event));

        // When
        ActionResult result = calendarActions.listCalendarEvents(5);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("prochains événements");
    }

    // ── Suppression d'événements ────────────────────────────────────────────

    @Test
    void deleteCalendarEvent_WithMatchingTitle_ShouldDeleteEvent() throws Exception {
        // Given
        when(calendarService.isAvailable()).thenReturn(true);
        CalendarEvent event = CalendarEvent.builder()
                .id("event-123")
                .title("Réunion équipe")
                .build();
        when(calendarService.searchEvents("Réunion", 20)).thenReturn(List.of(event));

        // When
        ActionResult result = calendarActions.deleteCalendarEvent("Réunion", null);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("supprimé");
        verify(calendarService).deleteEvent("event-123");
    }

    @Test
    void deleteCalendarEvent_WithNoMatch_ShouldReturnFailure() throws Exception {
        // Given
        when(calendarService.isAvailable()).thenReturn(true);
        when(calendarService.searchEvents("Inexistant", 20)).thenReturn(Collections.emptyList());

        // When
        ActionResult result = calendarActions.deleteCalendarEvent("Inexistant", null);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Aucun événement");
    }

    @Test
    void deleteCalendarEvent_WithInvalidDateFilter_ShouldSearchWithoutDateFilter() throws Exception {
        // Given
        when(calendarService.isAvailable()).thenReturn(true);
        CalendarEvent event = CalendarEvent.builder()
                .id("event-456")
                .title("Meeting")
                .build();
        when(calendarService.searchEvents("Meeting", 20)).thenReturn(List.of(event));

        // When
        ActionResult result = calendarActions.deleteCalendarEvent("Meeting", "not-a-date");

        // Then
        assertThat(result.isSuccess()).isTrue();
        verify(calendarService).deleteEvent("event-456");
    }
}
