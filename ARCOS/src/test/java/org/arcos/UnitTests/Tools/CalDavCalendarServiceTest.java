package org.arcos.UnitTests.Tools;

import org.arcos.Configuration.CalDavProperties;
import org.arcos.Tools.CalendarTool.CalDavCalendarService;
import org.arcos.Tools.CalendarTool.model.CalendarEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CalDavCalendarServiceTest {

    // ======================== iCal Parsing Tests ========================

    @Test
    void parseICalResponse_shouldParseSimpleVEvent() {
        // Given
        CalDavProperties props = createDefaultProperties();
        CalDavCalendarService service = createServiceForParsingTests(props);

        String ical = """
                BEGIN:VCALENDAR
                VERSION:2.0
                PRODID:-//Test//Test//FR
                BEGIN:VEVENT
                UID:test-uid-123
                DTSTART:20260401T100000
                DTEND:20260401T110000
                SUMMARY:Réunion d'équipe
                DESCRIPTION:Discussion projet ARCOS
                LOCATION:Salle 42
                END:VEVENT
                END:VCALENDAR
                """;

        // When
        List<CalendarEvent> events = service.parseICalResponse(ical);

        // Then
        assertThat(events).hasSize(1);
        CalendarEvent event = events.getFirst();
        assertThat(event.getId()).isEqualTo("test-uid-123");
        assertThat(event.getTitle()).isEqualTo("Réunion d'équipe");
        assertThat(event.getDescription()).isEqualTo("Discussion projet ARCOS");
        assertThat(event.getLocation()).isEqualTo("Salle 42");
        assertThat(event.getStartDateTime()).isEqualTo(LocalDateTime.of(2026, 4, 1, 10, 0));
        assertThat(event.getEndDateTime()).isEqualTo(LocalDateTime.of(2026, 4, 1, 11, 0));
        assertThat(event.isAllDay()).isFalse();
    }

    @Test
    void parseICalResponse_shouldParseAllDayEvent() {
        // Given
        CalDavProperties props = createDefaultProperties();
        CalDavCalendarService service = createServiceForParsingTests(props);

        String ical = """
                BEGIN:VCALENDAR
                VERSION:2.0
                PRODID:-//Test//Test//FR
                BEGIN:VEVENT
                UID:allday-uid-456
                DTSTART;VALUE=DATE:20260315
                DTEND;VALUE=DATE:20260316
                SUMMARY:Vacances
                END:VEVENT
                END:VCALENDAR
                """;

        // When
        List<CalendarEvent> events = service.parseICalResponse(ical);

        // Then
        assertThat(events).hasSize(1);
        CalendarEvent event = events.getFirst();
        assertThat(event.getId()).isEqualTo("allday-uid-456");
        assertThat(event.getTitle()).isEqualTo("Vacances");
        assertThat(event.isAllDay()).isTrue();
        assertThat(event.getStartDateTime()).isEqualTo(LocalDate.of(2026, 3, 15).atStartOfDay());
    }

    @Test
    void parseICalResponse_shouldHandleMultipleEvents() {
        // Given
        CalDavProperties props = createDefaultProperties();
        CalDavCalendarService service = createServiceForParsingTests(props);

        String ical = """
                BEGIN:VCALENDAR
                VERSION:2.0
                PRODID:-//Test//Test//FR
                BEGIN:VEVENT
                UID:uid-1
                DTSTART:20260401T090000
                DTEND:20260401T100000
                SUMMARY:Premier
                END:VEVENT
                BEGIN:VEVENT
                UID:uid-2
                DTSTART:20260401T140000
                DTEND:20260401T150000
                SUMMARY:Deuxième
                END:VEVENT
                END:VCALENDAR
                """;

        // When
        List<CalendarEvent> events = service.parseICalResponse(ical);

        // Then
        assertThat(events).hasSize(2);
        assertThat(events.get(0).getTitle()).isEqualTo("Premier");
        assertThat(events.get(1).getTitle()).isEqualTo("Deuxième");
    }

    @Test
    void parseICalResponse_shouldHandleMinimalEvent() {
        // Given — event with only UID and DTSTART
        CalDavProperties props = createDefaultProperties();
        CalDavCalendarService service = createServiceForParsingTests(props);

        String ical = """
                BEGIN:VCALENDAR
                VERSION:2.0
                PRODID:-//Test//Test//FR
                BEGIN:VEVENT
                UID:minimal-uid
                DTSTART:20260501T080000
                END:VEVENT
                END:VCALENDAR
                """;

        // When
        List<CalendarEvent> events = service.parseICalResponse(ical);

        // Then
        assertThat(events).hasSize(1);
        CalendarEvent event = events.getFirst();
        assertThat(event.getId()).isEqualTo("minimal-uid");
        assertThat(event.getTitle()).isNull();
        assertThat(event.getDescription()).isNull();
        assertThat(event.getLocation()).isNull();
        assertThat(event.getStartDateTime()).isEqualTo(LocalDateTime.of(2026, 5, 1, 8, 0));
    }

    @Test
    void parseICalResponse_shouldHandleInvalidIcal() {
        // Given
        CalDavProperties props = createDefaultProperties();
        CalDavCalendarService service = createServiceForParsingTests(props);

        String invalidIcal = "not valid ical data at all";

        // When
        List<CalendarEvent> events = service.parseICalResponse(invalidIcal);

        // Then — should return empty, not throw
        assertThat(events).isEmpty();
    }

    @Test
    void parseICalResponse_shouldHandleEscapedCharacters() {
        // Given
        CalDavProperties props = createDefaultProperties();
        CalDavCalendarService service = createServiceForParsingTests(props);

        String ical = """
                BEGIN:VCALENDAR
                VERSION:2.0
                PRODID:-//Test//Test//FR
                BEGIN:VEVENT
                UID:escape-uid
                DTSTART:20260601T100000
                DTEND:20260601T110000
                SUMMARY:Réunion\\, important
                DESCRIPTION:Ligne 1\\nLigne 2
                END:VEVENT
                END:VCALENDAR
                """;

        // When
        List<CalendarEvent> events = service.parseICalResponse(ical);

        // Then
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getTitle()).contains("important");
    }

    // ======================== isAvailable Tests ========================

    @Test
    void isAvailable_shouldReturnFalse_whenRadicaleNotReachable() {
        // Given — URL points to a non-existent host
        CalDavProperties props = new CalDavProperties();
        props.setUrl("http://localhost:59999");

        // When
        CalDavCalendarService service = new CalDavCalendarService(props);

        // Then
        assertThat(service.isAvailable()).isFalse();
    }

    // ======================== CalendarEvent Model Tests ========================

    @Test
    void calendarEvent_builderShouldWorkCorrectly() {
        // Given/When
        CalendarEvent event = CalendarEvent.builder()
                .id("test-id")
                .title("Test Title")
                .description("Test Description")
                .location("Test Location")
                .startDateTime(LocalDateTime.of(2026, 4, 1, 10, 0))
                .endDateTime(LocalDateTime.of(2026, 4, 1, 11, 0))
                .allDay(false)
                .build();

        // Then
        assertThat(event.getId()).isEqualTo("test-id");
        assertThat(event.getTitle()).isEqualTo("Test Title");
        assertThat(event.getDescription()).isEqualTo("Test Description");
        assertThat(event.getLocation()).isEqualTo("Test Location");
        assertThat(event.getStartDateTime()).isEqualTo(LocalDateTime.of(2026, 4, 1, 10, 0));
        assertThat(event.getEndDateTime()).isEqualTo(LocalDateTime.of(2026, 4, 1, 11, 0));
        assertThat(event.isAllDay()).isFalse();
    }

    // ======================== Helpers ========================

    private CalDavProperties createDefaultProperties() {
        CalDavProperties props = new CalDavProperties();
        props.setUrl("http://localhost:5232");
        props.setUsername("arcos");
        props.setPassword("arcos");
        props.setCalendarName("calendar");
        return props;
    }

    /**
     * Creates a CalDavCalendarService that won't try to connect to Radicale at construction time.
     * Uses a non-reachable URL so isAvailable() returns false, but iCal parsing methods work.
     */
    private CalDavCalendarService createServiceForParsingTests(CalDavProperties props) {
        // Service will set available=false since no Radicale is running
        return new CalDavCalendarService(props);
    }
}
