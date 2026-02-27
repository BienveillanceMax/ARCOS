package org.arcos.Producers;

import org.arcos.EventBus.EventQueue;
import org.arcos.EventBus.Events.EventPriority;
import org.arcos.EventBus.Events.EventType;
import org.arcos.Tools.CalendarTool.CalendarService;
import com.google.api.services.calendar.model.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
public class CalendarEventNotificationScheduler {

    private final CalendarService calendarService;
    private final EventQueue eventQueue;

    public CalendarEventNotificationScheduler(CalendarService calendarService, EventQueue eventQueue ) {
        this.calendarService = calendarService;
        this.eventQueue = eventQueue;
    }

    @Scheduled(cron = "0 0 * * * *") // Run every hour at the beginning of the hour
    public void scheduleEventNotifications() {
        if (!calendarService.isAvailable()) {
            log.debug("CalendarService désactivé, notifications ignorées.");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int currentHour = now.getHour();

        if (currentHour >= 23 || currentHour < 9) {
            log.debug("Nuit ({}h) : aucune notification calendrier envoyée.", currentHour);
            return;
        }

        try {
            List<Event> upcomingEvents = calendarService.listUpcomingEvents(10);
            for (Event event : upcomingEvents) {
                LocalDateTime eventStartTime = LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(event.getStart().getDateTime().getValue()),
                        ZoneId.systemDefault()
                );

                if (eventStartTime.isAfter(now) && eventStartTime.isBefore(now.plusHours(1))) {
                    org.arcos.EventBus.Events.Event<Event> pushedEvent =  new org.arcos.EventBus.Events.Event<Event>(EventType.CALENDAR_EVENT_SCHEDULER, EventPriority.HIGH,event,"Calendar event scheduler");
                    eventQueue.offer(pushedEvent);
                }
            }
        } catch (IOException | GeneralSecurityException e) {
            log.error("Erreur lors de la vérification des événements calendrier", e);
        }
    }
}
