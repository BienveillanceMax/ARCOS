package org.arcos.Tools.Actions;
import org.arcos.Tools.CalendarTool.CalendarService;
import com.google.api.services.calendar.model.Event;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class CalendarActions
{

    private static final Logger LOGGER = Logger.getLogger(CalendarActions.class.getName());
    private final CalendarService calendarService;


    @Autowired
    public CalendarActions(CalendarService calendarService) {
        this.calendarService = calendarService;
    }


    // Common date/time patterns for validation and parsing
    private static final List<DateTimeFormatter> DATE_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,           // 2023-12-25T14:30:00
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),     // 2023-12-25 14:30:00
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),     // 2023/12/25 14:30:00
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),     // 25/12/2023 14:30:00
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),     // 25-12-2023 14:30:00
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"),     // 12/25/2023 14:30:00
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),      // 2023-12-25T14:30
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),        // 2023-12-25 14:30
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),        // 2023/12/25 14:30
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),        // 25/12/2023 14:30
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"),        // 25-12-2023 14:30
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm")         // 12/25/2023 14:30
    );

    // ISO 8601 pattern for validation
    private static final Pattern ISO_8601_PATTERN = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?([+-]\\d{2}:\\d{2}|Z)?$"
    );

    @Tool(name = "Ajouter_un_evenement_au_calendrier", description = "Ajoute_un_évenement_au_calendrier")
    public ActionResult AddCalendarEvent(String title, String description, String location, String startDateTimeStr, String endDateTimeStr) {
        try {


            // Validate and convert dates to ISO 8601 format
            LocalDateTime startDateTime = parseAndValidateDateTime(startDateTimeStr, "startDateTime");
            LocalDateTime endDateTime = parseAndValidateDateTime(endDateTimeStr, "endDateTime");

            // Validate that end time is after start time
            if (endDateTime.isBefore(startDateTime) || endDateTime.isEqual(startDateTime)) {
                return ActionResult.failure("La date de fin doit être postérieure à la date de début.", null);
            }

            // Convert to ISO 8601 format strings for logging
            String isoStartDateTime = startDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            String isoEndDateTime = endDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            LOGGER.info("Parsed dates - Start: " + isoStartDateTime + ", End: " + isoEndDateTime);

            Event createdEvent = calendarService.createEvent(title, description, startDateTime, endDateTime, location);
            LOGGER.info("Event created: " + createdEvent.getHtmlLink());
            return ActionResult.success(
                    List.of(createdEvent.getSummary(), createdEvent.getDescription()),
                    "L'événement a été créé avec succès."
            );

        } catch (IllegalArgumentException e) {
            LOGGER.warning("Invalid date format: " + e.getMessage());
            return ActionResult.failure("Format de date invalide : " + e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.severe("Error creating event: " + e.getMessage());
            return ActionResult.failure("Erreur lors de la création de l'événement : " + e.getMessage(), e);
        }
    }

    @Tool(name = "Lister_les_evenements_a_venir", description = "Liste les évenements à venir du calendrier de l'utilisateur")
    public ActionResult listCalendarEvents(int maxResults) {
        try {
            List<Event> events = calendarService.listUpcomingEvents(maxResults);
            if (events.isEmpty()) {
                return ActionResult.successWithMessage("Aucun événement à venir trouvé.");
            }
            List<String> eventSummaries = events.stream()
                    .map(event -> {
                        String start = event.getStart().getDateTime() != null ? event.getStart().getDateTime().toString() : event.getStart().getDate().toString();
                        return String.format("%s (%s)", event.getSummary(), start);
                    })
                    .collect(Collectors.toList());
            return ActionResult.success(eventSummaries, "Voici les prochains événements de votre calendrier.");
        } catch (Exception e) {
            return ActionResult.failure("Erreur lors de la récupération des événements du calendrier: " + e.getMessage(), e);
        }
    }

    @Tool(name = "Supprimer_un_evenement", description = "Supprime un évenement aujourd'hui")
    public ActionResult deleteCalendarEvent(String title) {
        try {
            // We need to search for the event to get its ID.
            // We will search for today's events. A better implementation would be to ask the user for a date.
            LocalDate today = LocalDate.now();
            LocalDateTime startOfDay = today.atStartOfDay();
            LocalDateTime endOfDay = today.atTime(LocalTime.MAX);

            List<Event> events = calendarService.listEventsBetweenDates(startOfDay, endOfDay, 100);

            Event eventToDelete = null;
            for (Event event : events) {
                if (event.getSummary().equalsIgnoreCase(title)) {
                    eventToDelete = event;
                    break;
                }
            }

            if (eventToDelete != null) {
                calendarService.deleteEvent(eventToDelete.getId());
                return ActionResult.successWithMessage("L'événement '" + title + "' a été supprimé avec succès.");
            } else {
                return ActionResult.failure("Aucun événement trouvé avec le titre '" + title + "' pour aujourd'hui.", null);
            }
        } catch (Exception e) {
            return ActionResult.failure("Erreur lors de la suppression de l'événement : " + e.getMessage(), e);
        }
    }




    /**
     * Parses and validates a date/time string, converting it to LocalDateTime.
     * If the input is already in ISO 8601 format, it validates the format.
     * If not, it attempts to parse using common date formats and converts to ISO 8601.
     *
     * @param dateTimeStr The date/time string to parse
     * @param fieldName The name of the field for error messages
     * @return LocalDateTime object
     * @throws IllegalArgumentException if the date cannot be parsed
     */
    private LocalDateTime parseAndValidateDateTime(String dateTimeStr, String fieldName) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Le champ " + fieldName + " ne peut pas être vide.");
        }

        String trimmedDateTime = dateTimeStr.trim();

        // Check if it's already in ISO 8601 format
        if (isIso8601Format(trimmedDateTime)) {
            try {
                return LocalDateTime.parse(trimmedDateTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException(
                        "Format ISO 8601 invalide pour " + fieldName + ": " + trimmedDateTime +
                                ". Format attendu: yyyy-MM-dd'T'HH:mm:ss"
                );
            }
        }

        // Try to parse with various common formats
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                LocalDateTime parsedDateTime = LocalDateTime.parse(trimmedDateTime, formatter);
                LOGGER.info("Converted " + fieldName + " from '" + trimmedDateTime + "' to ISO 8601: " +
                        parsedDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                return parsedDateTime;
            } catch (DateTimeParseException e) {
                // Continue to next formatter
            }
        }

        // If no formatter worked, throw an exception with helpful message
        throw new IllegalArgumentException(
                "Impossible de parser la date " + fieldName + ": '" + trimmedDateTime + "'. " +
                        "Formats supportés: yyyy-MM-dd'T'HH:mm:ss, yyyy-MM-dd HH:mm:ss, dd/MM/yyyy HH:mm:ss, etc."
        );
    }

    /**
     * Checks if a date string follows ISO 8601 format pattern.
     *
     * @param dateTimeStr The date string to check
     * @return true if it matches ISO 8601 pattern, false otherwise
     */
    private boolean isIso8601Format(String dateTimeStr) {
        return ISO_8601_PATTERN.matcher(dateTimeStr).matches();
    }
}
