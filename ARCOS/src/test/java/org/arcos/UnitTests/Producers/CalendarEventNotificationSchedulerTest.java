package org.arcos.UnitTests.Producers;

import org.arcos.EventBus.EventQueue;
import org.arcos.Producers.CalendarEventNotificationScheduler;
import org.arcos.Tools.CalendarTool.CalendarService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalendarEventNotificationSchedulerTest {

    @Mock
    private CalendarService calendarService;

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
    void scheduleEventNotifications_WhenHourIs9_ShouldCallCalendarService() throws Exception {
        // Given
        LocalDateTime morningTime = LocalDateTime.now().withHour(9).withMinute(0).withSecond(0).withNano(0);
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
