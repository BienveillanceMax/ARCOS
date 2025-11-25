package Producers;

import EventBus.Events.EventPriority;
import EventBus.Events.EventType;
import EventBus.PersistentEventQueue;
import LLM.LLMClient;
import Tools.CalendarTool.CalendarService;
import com.google.api.services.calendar.model.Event;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;


import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class CalendarEventNotificationScheduler {

    private final CalendarService calendarService;
    private final PersistentEventQueue eventQueue;

    public CalendarEventNotificationScheduler(CalendarService calendarService, PersistentEventQueue eventQueue ) {
        this.calendarService = calendarService;
        this.eventQueue = eventQueue;
    }

    @Scheduled(cron = "0 0 * * * *") // Run every hour at the beginning of the hour
    public void scheduleEventNotifications() {
        LocalDateTime now = LocalDateTime.now();
        int currentHour = now.getHour();

        if (currentHour >= 23 || currentHour < 9) {
            System.out.println("It's night time. No notifications will be sent.");
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
                    EventBus.Events.Event<Event> pushedEvent =  new EventBus.Events.Event<Event>(EventType.CALENDAR_EVENT_SCHEDULER, EventPriority.HIGH,event,"Calendar event scheduler");
                    eventQueue.offer(pushedEvent);
                }
            }
        } catch (IOException | GeneralSecurityException e) {
            e.printStackTrace();
        }
    }
}

