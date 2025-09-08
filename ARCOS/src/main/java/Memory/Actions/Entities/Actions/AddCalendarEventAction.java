package Memory.Actions.Entities.Actions;

import Memory.Actions.Entities.ActionResult;
import Orchestrator.Entities.Parameter;
import Tools.CalendarTool.CalendarService;
import com.google.api.services.calendar.model.Event;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class AddCalendarEventAction extends Action {

    private final CalendarService calendarService;

    public AddCalendarEventAction(CalendarService calendarService) {
        super("Ajouter un événement au calendrier",
                "Ajoute un nouvel événement à l'agenda Google de l'utilisateur.",
                Arrays.asList(
                        new Parameter("title", String.class, true, "Titre de l'événement.", null),
                        new Parameter("description", String.class, false, "Description de l'événement.", null),
                        new Parameter("startDateTime", String.class, true, "Date et heure de début (format : yyyy-MM-dd'T'HH:mm:ss).", null),
                        new Parameter("endDateTime", String.class, true, "Date et heure de fin (format : yyyy-MM-dd'T'HH:mm:ss).", null),
                        new Parameter("location", String.class, false, "Lieu de l'événement.", null)
                ));
        this.calendarService = calendarService;
    }

    @Override
    public ActionResult execute(Map<String, Object> params) {
        try {
            String title = (String) params.get("title");
            String description = (String) params.get("description");
            String startDateTimeStr = (String) params.get("startDateTime");
            String endDateTimeStr = (String) params.get("endDateTime");
            String location = (String) params.get("location");

            DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
            LocalDateTime startDateTime = LocalDateTime.parse(startDateTimeStr, formatter);
            LocalDateTime endDateTime = LocalDateTime.parse(endDateTimeStr, formatter);

            Event createdEvent = calendarService.createEvent(title, description, startDateTime, endDateTime, location);

            return ActionResult.success(List.of(createdEvent.getSummary(), createdEvent.getDescription()), "L'événement a été créé avec succès.");
        } catch (Exception e) {
            return ActionResult.failure("Erreur lors de la création de l'événement : " + e.getMessage(), e);
        }
    }
}
