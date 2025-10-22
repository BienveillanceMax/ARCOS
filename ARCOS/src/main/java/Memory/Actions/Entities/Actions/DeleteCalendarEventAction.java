package Memory.Actions.Entities.Actions;

import Memory.Actions.Entities.ActionResult;
import Memory.Actions.Entities.Parameter;
import Tools.CalendarTool.CalendarService;
import com.google.api.services.calendar.model.Event;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DeleteCalendarEventAction extends Action {

    private final CalendarService calendarService;

    public DeleteCalendarEventAction(CalendarService calendarService) {
        super("Supprimer un événement du calendrier",
                "Supprime un événement de l'agenda Google de l'utilisateur en fonction de son titre et de sa date.",
                Collections.singletonList(
                        new Parameter("title", String.class, true, "Titre de l'événement à supprimer.", null)
                ));
        this.calendarService = calendarService;
    }

    @Override
    public ActionResult execute(Map<String, Object> params) {
        try {
            String title = (String) params.get("title");

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
}
