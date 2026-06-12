package org.arcos.UnitTests.Producers;

import org.arcos.EventBus.EventQueue;
import org.arcos.Producers.CalendarEventNotificationScheduler;
import org.arcos.Tools.CalendarTool.CalDavCalendarService;
import org.arcos.Tools.CalendarTool.model.CalendarEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalendarEventNotificationSchedulerTest {

    @Mock
    private CalDavCalendarService calendarService;

    @Mock
    private EventQueue eventQueue;

    @InjectMocks
    private CalendarEventNotificationScheduler scheduler;

    @Test
    void scheduleEventNotifications_WhenHourIs23_ShouldNotCallCalendarService() throws Exception {
        // Given
        LocalDateTime nightTime = LocalDateTime.now().withHour(23).withMinute(0).withSecond(0).withNano(0);

        try (MockedStatic<LocalDateTime> mockedLocalDateTime = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            mockedLocalDateTime.when(LocalDateTime::now).thenReturn(nightTime);

            // When
            scheduler.scheduleEventNotifications();

            // Then
            verify(calendarService, never()).listUpcomingEvents(anyInt());
        }
    }

    @Test
    void scheduleEventNotifications_WhenHourIs0_ShouldNotCallCalendarService() throws Exception {
        // Given
        LocalDateTime midnight = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);

        try (MockedStatic<LocalDateTime> mockedLocalDateTime = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            mockedLocalDateTime.when(LocalDateTime::now).thenReturn(midnight);

            // When
            scheduler.scheduleEventNotifications();

            // Then
            verify(calendarService, never()).listUpcomingEvents(anyInt());
        }
    }

    @Test
    void scheduleEventNotifications_WhenHourIs8_ShouldNotCallCalendarService() throws Exception {
        // Given
        LocalDateTime earlyMorning = LocalDateTime.now().withHour(8).withMinute(0).withSecond(0).withNano(0);

        try (MockedStatic<LocalDateTime> mockedLocalDateTime = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            mockedLocalDateTime.when(LocalDateTime::now).thenReturn(earlyMorning);

            // When
            scheduler.scheduleEventNotifications();

            // Then
            verify(calendarService, never()).listUpcomingEvents(anyInt());
        }
    }

    @Test
    void scheduleEventNotifications_SameEventOnConsecutivePolls_ShouldEmitOnlyOnce() {
        // Given — an event sitting in the 1-hour window across two hourly polls
        LocalDateTime pollTime = LocalDateTime.now().withHour(10).withMinute(0).withSecond(0).withNano(0);
        CalendarEvent event = CalendarEvent.builder()
                .id("evt-1").title("Réunion")
                .startDateTime(pollTime.plusMinutes(30))
                .build();
        when(calendarService.isAvailable()).thenReturn(true);
        when(calendarService.listUpcomingEvents(10)).thenReturn(List.of(event));

        try (MockedStatic<LocalDateTime> mockedLocalDateTime = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            mockedLocalDateTime.when(LocalDateTime::now).thenReturn(pollTime);

            // When — two polls see the same upcoming event
            scheduler.scheduleEventNotifications();
            scheduler.scheduleEventNotifications();

            // Then — only one notification is emitted
            verify(eventQueue, times(1)).offer(any());
        }
    }

    @Test
    void scheduleEventNotifications_RescheduledEvent_ShouldNotifyAgain() {
        // Given — the same event id comes back with a NEW start time (rescheduled)
        LocalDateTime pollTime = LocalDateTime.now().withHour(10).withMinute(0).withSecond(0).withNano(0);
        CalendarEvent original = CalendarEvent.builder()
                .id("evt-1").title("Réunion")
                .startDateTime(pollTime.plusMinutes(20))
                .build();
        CalendarEvent rescheduled = CalendarEvent.builder()
                .id("evt-1").title("Réunion")
                .startDateTime(pollTime.plusMinutes(50))
                .build();
        when(calendarService.isAvailable()).thenReturn(true);
        when(calendarService.listUpcomingEvents(10))
                .thenReturn(List.of(original))
                .thenReturn(List.of(rescheduled));

        try (MockedStatic<LocalDateTime> mockedLocalDateTime = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            mockedLocalDateTime.when(LocalDateTime::now).thenReturn(pollTime);

            // When
            scheduler.scheduleEventNotifications();
            scheduler.scheduleEventNotifications();

            // Then — both the original and the rescheduled slot are announced
            verify(eventQueue, times(2)).offer(any());
        }
    }

    @Test
    void scheduleEventNotifications_WhenHourIs9_ShouldCallCalendarService() throws Exception {
        // Given
        LocalDateTime morningTime = LocalDateTime.now().withHour(9).withMinute(0).withSecond(0).withNano(0);
        when(calendarService.isAvailable()).thenReturn(true);
        when(calendarService.listUpcomingEvents(10)).thenReturn(java.util.Collections.emptyList());

        try (MockedStatic<LocalDateTime> mockedLocalDateTime = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            mockedLocalDateTime.when(LocalDateTime::now).thenReturn(morningTime);

            // When
            scheduler.scheduleEventNotifications();

            // Then
            verify(calendarService).listUpcomingEvents(10);
        }
    }
}
