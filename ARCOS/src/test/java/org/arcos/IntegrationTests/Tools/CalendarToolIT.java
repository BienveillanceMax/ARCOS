package org.arcos.IntegrationTests.Tools;

import org.arcos.Tools.Actions.ActionResult;
import org.arcos.Tools.Actions.CalendarActions;
import org.arcos.Tools.CalendarTool.CalDavCalendarService;
import org.arcos.Tools.CalendarTool.model.CalendarEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests pour CalendarActions (Ajouter/Lister/Supprimer evenements).
 *
 * Valide :
 * - AC2 : CalDAV add/list/delete => events created/listed/deleted
 * - AC6 : Radicale indisponible => degradation gracieuse
 */
@ExtendWith(MockitoExtension.class)
class CalendarToolIT {

    @Mock
    private CalDavCalendarService calendarService;

    private CalendarActions calendarActions;

    @BeforeEach
    void setUp() {
        calendarActions = new CalendarActions(calendarService);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AC2 : Ajouter_un_evenement_au_calendrier
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Ajouter_un_evenement_au_calendrier — creation")
    class AddEventTests {

        @Test
        @DisplayName("Given valid ISO dates, When adding event, Then event is created and returned")
        void addCalendarEvent_WithValidDates_ShouldCreateEvent() {
            // Given
            when(calendarService.isAvailable()).thenReturn(true);
            CalendarEvent created = CalendarEvent.builder()
                    .id("uid-abc-123")
                    .title("Rendez-vous dentiste")
                    .description("Controle annuel")
                    .location("Cabinet Dr. Martin")
                    .startDateTime(LocalDateTime.of(2026, 4, 15, 14, 0))
                    .endDateTime(LocalDateTime.of(2026, 4, 15, 15, 0))
                    .allDay(false)
                    .build();
            when(calendarService.createEvent(anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(created);

            // When
            ActionResult result = calendarActions.AddCalendarEvent(
                    "Rendez-vous dentiste", "Controle annuel", "Cabinet Dr. Martin",
                    "2026-04-15T14:00:00", "2026-04-15T15:00:00");

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).contains("succès");
            List<String> data = (List<String>) result.getData();
            assertThat(data).contains("Rendez-vous dentiste");
            verify(calendarService).createEvent(
                    eq("Rendez-vous dentiste"), eq("Controle annuel"),
                    any(LocalDateTime.class), any(LocalDateTime.class),
                    eq("Cabinet Dr. Martin"));
        }

        @Test
        @DisplayName("Given multiple date formats, When adding event, Then all formats are parsed")
        void addCalendarEvent_WithVariousDateFormats_ShouldParseAll() {
            // Given
            when(calendarService.isAvailable()).thenReturn(true);
            CalendarEvent mock = CalendarEvent.builder().id("uid").title("T").description("D").build();
            when(calendarService.createEvent(anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(mock);

            // When — format dd/MM/yyyy HH:mm
            ActionResult r1 = calendarActions.AddCalendarEvent(
                    "T", "D", "L", "15/04/2026 14:00", "15/04/2026 15:00");
            // When — format yyyy-MM-dd HH:mm
            ActionResult r2 = calendarActions.AddCalendarEvent(
                    "T", "D", "L", "2026-04-15 14:00", "2026-04-15 15:00");

            // Then
            assertThat(r1.isSuccess()).isTrue();
            assertThat(r2.isSuccess()).isTrue();
            verify(calendarService, times(2)).createEvent(anyString(), anyString(), any(), any(), anyString());
        }

        @Test
        @DisplayName("Given CalDAV throws exception during creation, When adding event, Then failure with error message")
        void addCalendarEvent_WhenCalDavThrows_ShouldReturnFailureGracefully() {
            // Given
            when(calendarService.isAvailable()).thenReturn(true);
            when(calendarService.createEvent(anyString(), anyString(), any(), any(), anyString()))
                    .thenThrow(new ResourceAccessException("Connection refused: localhost:5232"));

            // When
            ActionResult result = calendarActions.AddCalendarEvent(
                    "Test", "Desc", "Paris", "2026-04-15T14:00:00", "2026-04-15T15:00:00");

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("Erreur lors de la création");
            assertThat(result.getMessage()).contains("Connection refused");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AC2 : Lister_les_evenements_a_venir
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Lister_les_evenements_a_venir — listing")
    class ListEventsTests {

        @Test
        @DisplayName("Given upcoming events exist, When listing, Then events are returned with summaries")
        void listCalendarEvents_WithMultipleEvents_ShouldReturnFormattedList() {
            // Given
            when(calendarService.isAvailable()).thenReturn(true);
            CalendarEvent event1 = CalendarEvent.builder()
                    .title("Reunion equipe")
                    .startDateTime(LocalDateTime.of(2026, 4, 1, 10, 0))
                    .build();
            CalendarEvent event2 = CalendarEvent.builder()
                    .title("Dejeuner client")
                    .startDateTime(LocalDateTime.of(2026, 4, 2, 12, 30))
                    .build();
            when(calendarService.listUpcomingEvents(10)).thenReturn(List.of(event1, event2));

            // When
            ActionResult result = calendarActions.listCalendarEvents(10);

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).contains("prochains événements");
            List<String> data = (List<String>) result.getData();
            assertThat(data).hasSize(2);
            assertThat(data.get(0)).contains("Reunion equipe");
            assertThat(data.get(1)).contains("Dejeuner client");
        }

        @Test
        @DisplayName("Given CalDAV throws during listing, When listing events, Then failure with error message")
        void listCalendarEvents_WhenCalDavThrows_ShouldReturnFailureGracefully() {
            // Given
            when(calendarService.isAvailable()).thenReturn(true);
            when(calendarService.listUpcomingEvents(10))
                    .thenThrow(new ResourceAccessException("Radicale not responding"));

            // When
            ActionResult result = calendarActions.listCalendarEvents(10);

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("Erreur lors de la récupération");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AC2 : Supprimer_un_evenement
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Supprimer_un_evenement — deletion")
    class DeleteEventTests {

        @Test
        @DisplayName("Given matching event with date filter, When deleting, Then correct event is deleted")
        void deleteCalendarEvent_WithDateFilter_ShouldDeleteMatchingEvent() {
            // Given
            when(calendarService.isAvailable()).thenReturn(true);
            CalendarEvent event = CalendarEvent.builder()
                    .id("evt-789")
                    .title("Reunion equipe")
                    .startDateTime(LocalDateTime.of(2026, 4, 1, 10, 0))
                    .allDay(false)
                    .build();
            when(calendarService.searchEvents("Reunion", 20)).thenReturn(List.of(event));

            // When
            ActionResult result = calendarActions.deleteCalendarEvent("Reunion", "2026-04-01");

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).contains("supprimé");
            verify(calendarService).deleteEvent("evt-789");
        }

        @Test
        @DisplayName("Given CalDAV throws during deletion, When deleting, Then failure with error message")
        void deleteCalendarEvent_WhenDeleteThrows_ShouldReturnFailureGracefully() {
            // Given
            when(calendarService.isAvailable()).thenReturn(true);
            CalendarEvent event = CalendarEvent.builder()
                    .id("evt-fail")
                    .title("Problematic event")
                    .build();
            when(calendarService.searchEvents("Problematic", 20)).thenReturn(List.of(event));
            doThrow(new ResourceAccessException("Write failed"))
                    .when(calendarService).deleteEvent("evt-fail");

            // When
            ActionResult result = calendarActions.deleteCalendarEvent("Problematic", null);

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("Erreur lors de la suppression");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AC6 : all calendar operations when Radicale is down
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Degradation gracieuse — Radicale indisponible")
    class RadicaleUnavailableTests {

        @Test
        @DisplayName("Given Radicale down, When add event, Then explicit error, no crash")
        void addEvent_RadicaleDown_ShouldReturnFailureNoCrash() {
            // Given
            when(calendarService.isAvailable()).thenReturn(false);

            // When
            ActionResult result = calendarActions.AddCalendarEvent(
                    "Test", "D", "L", "2026-04-15T14:00:00", "2026-04-15T15:00:00");

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("Radicale inaccessible");
            verify(calendarService, never()).createEvent(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Given Radicale down, When list events, Then explicit error, no crash")
        void listEvents_RadicaleDown_ShouldReturnFailureNoCrash() {
            // Given
            when(calendarService.isAvailable()).thenReturn(false);

            // When
            ActionResult result = calendarActions.listCalendarEvents(10);

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("Radicale inaccessible");
            verify(calendarService, never()).listUpcomingEvents(anyInt());
        }

        @Test
        @DisplayName("Given Radicale down, When delete event, Then explicit error, no crash")
        void deleteEvent_RadicaleDown_ShouldReturnFailureNoCrash() {
            // Given
            when(calendarService.isAvailable()).thenReturn(false);

            // When
            ActionResult result = calendarActions.deleteCalendarEvent("Test", null);

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("Radicale inaccessible");
            verify(calendarService, never()).searchEvents(any(), anyInt());
        }
    }
}
