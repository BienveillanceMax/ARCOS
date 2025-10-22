package Memory.Actions.Entities.Actions;

import Memory.Actions.Entities.ActionResult;
import Memory.Actions.Entities.Parameter;
import Tools.CalendarTool.CalendarService;
import com.google.api.services.calendar.model.Event;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ListCalendarEventsAction extends Action {

    private final CalendarService calendarService;

    public ListCalendarEventsAction(CalendarService calendarService) {
        super("Lister les événements du calendrier",
                "Liste les événements à venir de l'agenda Google de l'utilisateur.",
                Collections.singletonList(
                        new Parameter("maxResults", Integer.class, false, "Nombre maximum d'événements à retourner.", 10)
                ));
        this.calendarService = calendarService;
    }

    @Override
    public ActionResult execute(Map<String, Object> params) {
        try {
            int maxResults = (int) params.getOrDefault("maxResults", 10);
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
}
