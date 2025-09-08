package Memory.Actions.Entities.Actions;

import Memory.Actions.Entities.ActionResult;
import Orchestrator.Entities.Parameter;
import Tools.CalendarTool.CalendarService;
import com.google.api.services.calendar.model.Event;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SearchCalendarEventsAction extends Action {

    private final CalendarService calendarService;

    public SearchCalendarEventsAction(CalendarService calendarService) {
        super("Rechercher des événements dans le calendrier",
                "Recherche des événements dans l'agenda Google de l'utilisateur en fonction d'une requête.",
                Collections.singletonList(
                        new Parameter("query", String.class, true, "Texte à rechercher dans les événements.", null)
                ));
        this.calendarService = calendarService;
    }

    @Override
    public ActionResult execute(Map<String, Object> params) {
        try {
            String query = (String) params.get("query");
            List<Event> events = calendarService.searchEvents(query, 10);
            if (events.isEmpty()) {
                return ActionResult.successWithMessage("Aucun événement trouvé pour la recherche '" + query + "'.");
            }
            List<String> eventSummaries = events.stream()
                    .map(event -> {
                        String start = event.getStart().getDateTime() != null ? event.getStart().getDateTime().toString() : event.getStart().getDate().toString();
                        return String.format("%s (%s)", event.getSummary(), start);
                    })
                    .collect(Collectors.toList());
            return ActionResult.success(eventSummaries, "Voici les événements trouvés pour la recherche '" + query + "':");
        } catch (Exception e) {
            return ActionResult.failure("Erreur lors de la recherche d'événements : " + e.getMessage(), e);
        }
    }
}
