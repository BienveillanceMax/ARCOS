package org.arcos.Tools.Actions;
import org.arcos.Tools.CalendarTool.CalDavCalendarService;
import org.arcos.Tools.CalendarTool.model.CalendarEvent;
import lombok.extern.slf4j.Slf4j;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CalendarActions
{

    private final CalDavCalendarService calendarService;

    private static final String CALENDAR_UNAVAILABLE_MESSAGE =
            "Calendrier non disponible : serveur Radicale inaccessible.";
    private static final DateTimeFormatter FRENCH_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FRENCH_TIME_FORMAT = DateTimeFormatter.ofPattern("HH'h'mm");

    @Autowired
    public CalendarActions(CalDavCalendarService calendarService) {
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
        if (!calendarService.isAvailable()) {
            log.warn("Ajout calendrier demandé mais service non disponible.");
            return ActionResult.failure(CALENDAR_UNAVAILABLE_MESSAGE, null);
        }
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

            log.info("Parsed dates - Start: {}, End: {}", isoStartDateTime, isoEndDateTime);

            CalendarEvent createdEvent = calendarService.createEvent(title, description, startDateTime, endDateTime, location);
            log.info("Event created: {}", createdEvent.getId());
            return ActionResult.success(
                    List.of(createdEvent.getTitle(), createdEvent.getDescription()),
                    "L'événement a été créé avec succès."
            );

        } catch (IllegalArgumentException e) {
            log.warn("Invalid date format: {}", e.getMessage());
            return ActionResult.failure("Format de date invalide : " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error creating event: {}", e.getMessage());
            return ActionResult.failure("Erreur lors de la création de l'événement : " + e.getMessage(), e);
        }
    }

    @Tool(name = "Lister_les_evenements_a_venir", description = "Liste les évenements à venir du calendrier de l'utilisateur")
    public ActionResult listCalendarEvents(int maxResults) {
        if (!calendarService.isAvailable()) {
            log.warn("Liste calendrier demandée mais service non disponible.");
            return ActionResult.failure(CALENDAR_UNAVAILABLE_MESSAGE, null);
        }
        try {
            List<CalendarEvent> events = calendarService.listUpcomingEvents(maxResults);
            if (events.isEmpty()) {
                return ActionResult.successWithMessage("Aucun événement à venir trouvé.");
            }
            List<String> eventSummaries = events.stream()
                    .map(event -> {
                        String start = event.getStartDateTime() != null
                                ? event.getStartDateTime().toString()
                                : "journée";
                        return String.format("%s (%s)", event.getTitle(), start);
                    })
                    .collect(Collectors.toList());
            return ActionResult.success(eventSummaries, "Voici les prochains événements de votre calendrier.");
        } catch (Exception e) {
            return ActionResult.failure("Erreur lors de la récupération des événements du calendrier: " + e.getMessage(), e);
        }
    }

    @Tool(name = "Supprimer_un_evenement", description = "Supprime un événement du calendrier par titre (recherche partielle) et date optionnelle (format yyyy-MM-dd, défaut: aujourd'hui)")
    public ActionResult deleteCalendarEvent(String title, String dateStr) {
        if (!calendarService.isAvailable()) {
            log.warn("Suppression calendrier demandée mais service non disponible.");
            return ActionResult.failure(CALENDAR_UNAVAILABLE_MESSAGE, null);
        }
        try {
            LocalDate targetDate = parseOptionalDate(dateStr);

            // Search events by title (client-side fuzzy search)
            List<CalendarEvent> searchResults = calendarService.searchEvents(title, 20);

            // Filter by target date if provided
            List<CalendarEvent> candidates;
            if (targetDate != null) {
                candidates = searchResults.stream()
                        .filter(event -> matchesDate(event, targetDate))
                        .collect(Collectors.toList());
            } else {
                candidates = searchResults;
            }

            // Find best match: case-insensitive contains on title
            String lowerTitle = title.toLowerCase();
            CalendarEvent eventToDelete = candidates.stream()
                    .filter(event -> event.getTitle() != null
                            && event.getTitle().toLowerCase().contains(lowerTitle))
                    .findFirst()
                    .orElse(null);

            if (eventToDelete != null) {
                String deletedTitle = eventToDelete.getTitle();
                calendarService.deleteEvent(eventToDelete.getId());
                return ActionResult.successWithMessage("L'événement '" + deletedTitle + "' a été supprimé avec succès.");
            }

            // No match found — build helpful failure message listing events for context
            return buildNoMatchMessage(title, targetDate, searchResults);
        } catch (Exception e) {
            return ActionResult.failure("Erreur lors de la suppression de l'événement : " + e.getMessage(), e);
        }
    }

    private LocalDate parseOptionalDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            log.warn("Format de date invalide pour suppression: '{}', recherche sans filtre de date", dateStr);
            return null;
        }
    }

    private boolean matchesDate(CalendarEvent event, LocalDate date) {
        if (event.getStartDateTime() == null) return false;
        if (event.isAllDay()) {
            return event.getStartDateTime().toLocalDate().equals(date);
        }
        return event.getStartDateTime().toLocalDate().equals(date);
    }

    private ActionResult buildNoMatchMessage(String title, LocalDate targetDate, List<CalendarEvent> searchResults) {
        try {
            if (targetDate == null) {
                return ActionResult.failure(
                        "Aucun événement correspondant à '" + title + "' trouvé.", null);
            }
            // Reuse already-fetched results filtered by date instead of an extra API call
            List<CalendarEvent> dayEvents = searchResults.stream()
                    .filter(event -> matchesDate(event, targetDate))
                    .collect(Collectors.toList());

            // If search returned nothing for this date, fetch all events for the day
            if (dayEvents.isEmpty()) {
                dayEvents = calendarService.listEventsBetweenDates(
                        targetDate.atStartOfDay(), targetDate.atTime(LocalTime.MAX), 20);
            }

            if (dayEvents.isEmpty()) {
                return ActionResult.failure(
                        "Aucun événement correspondant à '" + title + "' trouvé pour le "
                                + targetDate.format(FRENCH_DATE_FORMAT)
                                + ". Aucun événement ce jour.", null);
            }
            String eventList = dayEvents.stream()
                    .map(e -> {
                        String summary = e.getTitle() != null ? e.getTitle() : "(sans titre)";
                        String time = !e.isAllDay() && e.getStartDateTime() != null
                                ? e.getStartDateTime().format(FRENCH_TIME_FORMAT)
                                : "journée";
                        return "'" + summary + " (" + time + ")'";
                    })
                    .collect(Collectors.joining(", "));
            return ActionResult.failure(
                    "Aucun événement correspondant à '" + title + "' trouvé pour le "
                            + targetDate.format(FRENCH_DATE_FORMAT)
                            + ". Événements ce jour : " + eventList + ".", null);
        } catch (Exception e) {
            return ActionResult.failure(
                    "Aucun événement correspondant à '" + title + "' trouvé.", null);
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
                log.info("Converted {} from '{}' to ISO 8601: {}", fieldName, trimmedDateTime,
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
