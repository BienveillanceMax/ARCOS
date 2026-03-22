package org.arcos.Producers;

import org.arcos.EventBus.EventQueue;
import org.arcos.EventBus.Events.EventPriority;
import org.arcos.EventBus.Events.EventType;
import org.arcos.Tools.CalendarTool.CalDavCalendarService;
import org.arcos.Tools.CalendarTool.model.CalendarEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class CalendarEventNotificationScheduler {

    private final CalDavCalendarService calendarService;
    private final EventQueue eventQueue;

    public CalendarEventNotificationScheduler(CalDavCalendarService calendarService, EventQueue eventQueue) {
        this.calendarService = calendarService;
        this.eventQueue = eventQueue;
    }

    @Scheduled(cron = "0 0 * * * *") // Run every hour at the beginning of the hour
    public void scheduleEventNotifications() {
        if (!calendarService.isAvailable()) {
            log.debug("CalDavCalendarService désactivé, notifications ignorées.");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int currentHour = now.getHour();

        if (currentHour >= 23 || currentHour < 9) {
            log.debug("Nuit ({}h) : aucune notification calendrier envoyée.", currentHour);
            return;
        }

        try {
            List<CalendarEvent> upcomingEvents = calendarService.listUpcomingEvents(10);
            for (CalendarEvent event : upcomingEvents) {
                LocalDateTime eventStartTime = event.getStartDateTime();
                if (eventStartTime == null) continue;

                if (eventStartTime.isAfter(now) && eventStartTime.isBefore(now.plusHours(1))) {
                    org.arcos.EventBus.Events.Event<CalendarEvent> pushedEvent = new org.arcos.EventBus.Events.Event<>(
                            EventType.CALENDAR_EVENT_SCHEDULER, EventPriority.HIGH, event, "Calendar event scheduler");
                    eventQueue.offer(pushedEvent);
                }
            }
        } catch (Exception e) {
            log.error("Erreur lors de la vérification des événements calendrier", e);
        }
    }
}
