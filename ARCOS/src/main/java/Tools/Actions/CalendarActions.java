package Tools.Actions;
import Memory.Actions.Entities.ActionResult;
import Memory.Actions.Entities.Actions.AddCalendarEventAction;
import Orchestrator.Entities.Parameter;
import Tools.CalendarTool.CalendarService;
import com.google.api.services.calendar.model.Event;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@Component
public class CalendarActions
{

    private static final Logger LOGGER = Logger.getLogger(AddCalendarEventAction.class.getName());
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

    @Tool(name = "Ajouter un évenement au calendrier", description = "ajoute un évenement au calendrier")
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
