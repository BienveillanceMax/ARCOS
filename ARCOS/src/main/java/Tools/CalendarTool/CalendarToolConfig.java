package Tools.CalendarTool;

import com.google.api.services.calendar.model.Event;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

@Configuration
public class CalendarToolConfig {

    private final CalendarService calendarService;

    public CalendarToolConfig(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @Bean
    @Description("Get a list of upcoming calendar events")
    public Function<Integer, List<Event>> listUpcomingEvents() {
        return maxResults -> {
            try {
                return calendarService.listUpcomingEvents(maxResults);
            } catch (IOException | GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        };
    }

    @Bean
    @Description("Create a new calendar event")
    public Function<EventCreationRequest, Event> createEvent() {
        return request -> {
            try {
                return calendarService.createEvent(request.title(), request.description(), request.startDateTime(), request.endDateTime(), request.location());
            } catch (IOException | GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        };
    }

    @Bean
    @Description("Create a new all-day calendar event")
    public Function<AllDayEventCreationRequest, Event> createAllDayEvent() {
        return request -> {
            try {
                return calendarService.createAllDayEvent(request.title(), request.description(), request.date(), request.location());
            } catch (IOException | GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        };
    }

    @Bean
    @Description("Update an existing calendar event")
    public Function<EventUpdateRequest, Event> updateEvent() {
        return request -> {
            try {
                return calendarService.updateEvent(request.eventId(), request.title(), request.description(), request.startDateTime(), request.endDateTime(), request.location());
            } catch (IOException | GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        };
    }

    @Bean
    @Description("Delete a calendar event")
    public Function<String, Void> deleteEvent() {
        return eventId -> {
            try {
                calendarService.deleteEvent(eventId);
            } catch (IOException | GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
            return null;
        };
    }

    @Bean
    @Description("Get a specific calendar event by its ID")
    public Function<String, Event> getEvent() {
        return eventId -> {
            try {
                return calendarService.getEvent(eventId);
            } catch (IOException | GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        };
    }

    @Bean
    @Description("Search for calendar events by title")
    public Function<EventSearchRequest, List<Event>> searchEvents() {
        return request -> {
            try {
                return calendarService.searchEvents(request.searchQuery(), request.maxResults());
            } catch (IOException | GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        };
    }

    @Bean
    @Description("List calendar events between two dates")
    public Function<DateRangeSearchRequest, List<Event>> listEventsBetweenDates() {
        return request -> {
            try {
                return calendarService.listEventsBetweenDates(request.startDate(), request.endDate(), request.maxResults());
            } catch (IOException | GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        };
    }

    public record EventCreationRequest(String title, String description, LocalDateTime startDateTime, LocalDateTime endDateTime, String location) {}
    public record AllDayEventCreationRequest(String title, String description, LocalDate date, String location) {}
    public record EventUpdateRequest(String eventId, String title, String description, LocalDateTime startDateTime, LocalDateTime endDateTime, String location) {}
    public record EventSearchRequest(String searchQuery, int maxResults) {}
    public record DateRangeSearchRequest(LocalDateTime startDate, LocalDateTime endDate, int maxResults) {}
}
