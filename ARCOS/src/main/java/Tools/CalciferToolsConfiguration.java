package Tools;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

/**
 * Clean configuration class that registers all Calcifer tools.
 * Delegates implementation to specialized service classes.
 */
@Configuration
public class CalciferToolsConfiguration {

    // ===== SEARCH TOOLS =====

    @Bean
    @Description("Recherche des informations sur le web. Retourne les métadonnées des résultats (titres, URLs, descriptions).")
    public Function<SearchRequest, ToolResponse> rechercherInternet(SearchToolService searchToolService) {
        return searchToolService::search;
    }

    @Bean
    @Description("Effectue une recherche web approfondie et extrait le contenu textuel complet de la première page de résultat.")
    public Function<SearchRequest, ToolResponse> rechercheApprofondieInternet(SearchToolService searchToolService) {
        return searchToolService::deepSearch;
    }

    // ===== CALENDAR TOOLS =====

    @Bean
    @Description("Ajoute un nouvel événement à l'agenda Google de l'utilisateur.")
    public Function<AddCalendarEventRequest, ToolResponse> ajouterEvenementCalendrier(CalendarToolService calendarToolService) {
        return calendarToolService::addEvent;
    }

    @Bean
    @Description("Liste les événements à venir de l'agenda Google de l'utilisateur.")
    public Function<ListCalendarEventsRequest, ToolResponse> listerEvenementsCalendrier(CalendarToolService calendarToolService) {
        return calendarToolService::listEvents;
    }

    @Bean
    @Description("Recherche des événements dans l'agenda Google en fonction d'une requête textuelle.")
    public Function<SearchCalendarEventsRequest, ToolResponse> rechercherEvenementsCalendrier(CalendarToolService calendarToolService) {
        return calendarToolService::searchEvents;
    }

    @Bean
    @Description("Supprime un événement de l'agenda Google en fonction de son titre.")
    public Function<DeleteCalendarEventRequest, ToolResponse> supprimerEvenementCalendrier(CalendarToolService calendarToolService) {
        return calendarToolService::deleteEvent;
    }

    // ===== CODE EXECUTION TOOLS =====

    @Bean
    @Description("Exécute un bloc de code Python et retourne le résultat.")
    public Function<ExecutePythonRequest, ToolResponse> executerCodePython(PythonToolService pythonToolService) {
        return pythonToolService::executePython;
    }
}
