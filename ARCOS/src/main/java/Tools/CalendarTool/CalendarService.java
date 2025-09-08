package Tools.CalendarTool;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

@Service
public class CalendarService {

    private static final String APPLICATION_NAME = "ARCOS AI Assistant";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    // Mise à jour des scopes pour permettre l'écriture
    private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR);
    private static final String CREDENTIALS_FILE_PATH = "/client_secrets.json";

    private Credential getCredentials() throws IOException, GeneralSecurityException {
        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        InputStream in = CalendarService.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new IOException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    private Calendar getCalendarService() throws IOException, GeneralSecurityException {
        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        Credential credential = getCredentials();
        return new Calendar.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public List<Event> listUpcomingEvents(int maxResults) throws IOException, GeneralSecurityException {
        Calendar service = getCalendarService();

        Events events = service.events().list("primary")
                .setMaxResults(maxResults)
                .setTimeMin(new DateTime(System.currentTimeMillis()))
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .execute();
        return events.getItems();
    }

    /**
     * Crée un nouvel événement dans le calendrier
     * @param title Titre de l'événement
     * @param description Description de l'événement
     * @param startDateTime Date et heure de début
     * @param endDateTime Date et heure de fin
     * @param location Lieu de l'événement (optionnel)
     * @return L'événement créé
     */
    public Event createEvent(String title, String description, LocalDateTime startDateTime,
                             LocalDateTime endDateTime, String location) throws IOException, GeneralSecurityException {
        Calendar service = getCalendarService();

        Event event = new Event()
                .setSummary(title)
                .setDescription(description)
                .setLocation(location);

        // Configuration de la date/heure de début
        DateTime startTime = new DateTime(startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        EventDateTime start = new EventDateTime()
                .setDateTime(startTime)
                .setTimeZone(ZoneId.systemDefault().getId());
        event.setStart(start);

        // Configuration de la date/heure de fin
        DateTime endTime = new DateTime(endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        EventDateTime end = new EventDateTime()
                .setDateTime(endTime)
                .setTimeZone(ZoneId.systemDefault().getId());
        event.setEnd(end);

        return service.events().insert("primary", event).execute();
    }

    /**
     * Crée un événement toute la journée
     * @param title Titre de l'événement
     * @param description Description de l'événement
     * @param date Date de l'événement
     * @param location Lieu de l'événement (optionnel)
     * @return L'événement créé
     */
    public Event createAllDayEvent(String title, String description, java.time.LocalDate date,
                                   String location) throws IOException, GeneralSecurityException {
        Calendar service = getCalendarService();

        Event event = new Event()
                .setSummary(title)
                .setDescription(description)
                .setLocation(location);

        // Pour un événement toute la journée, on utilise setDate au lieu de setDateTime
        EventDateTime start = new EventDateTime()
                .setDate(new DateTime(date.toString()));
        event.setStart(start);

        EventDateTime end = new EventDateTime()
                .setDate(new DateTime(date.plusDays(1).toString()));
        event.setEnd(end);

        return service.events().insert("primary", event).execute();
    }

    /**
     * Met à jour un événement existant
     * @param eventId ID de l'événement à modifier
     * @param title Nouveau titre
     * @param description Nouvelle description
     * @param startDateTime Nouvelle date/heure de début
     * @param endDateTime Nouvelle date/heure de fin
     * @param location Nouveau lieu
     * @return L'événement mis à jour
     */
    public Event updateEvent(String eventId, String title, String description,
                             LocalDateTime startDateTime, LocalDateTime endDateTime,
                             String location) throws IOException, GeneralSecurityException {
        Calendar service = getCalendarService();

        // Récupérer l'événement existant
        Event event = service.events().get("primary", eventId).execute();

        // Mettre à jour les propriétés
        if (title != null) event.setSummary(title);
        if (description != null) event.setDescription(description);
        if (location != null) event.setLocation(location);

        if (startDateTime != null) {
            DateTime startTime = new DateTime(startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            EventDateTime start = new EventDateTime()
                    .setDateTime(startTime)
                    .setTimeZone(ZoneId.systemDefault().getId());
            event.setStart(start);
        }

        if (endDateTime != null) {
            DateTime endTime = new DateTime(endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            EventDateTime end = new EventDateTime()
                    .setDateTime(endTime)
                    .setTimeZone(ZoneId.systemDefault().getId());
            event.setEnd(end);
        }

        return service.events().update("primary", eventId, event).execute();
    }

    /**
     * Met à jour partiellement un événement (seuls les champs non-null sont mis à jour)
     * @param eventId ID de l'événement à modifier
     * @param title Nouveau titre (null = pas de modification)
     * @param description Nouvelle description (null = pas de modification)
     * @param location Nouveau lieu (null = pas de modification)
     * @return L'événement mis à jour
     */
    public Event updateEventPartial(String eventId, String title, String description,
                                    String location) throws IOException, GeneralSecurityException {
        return updateEvent(eventId, title, description, null, null, location);
    }

    /**
     * Supprime un événement du calendrier
     * @param eventId ID de l'événement à supprimer
     */
    public void deleteEvent(String eventId) throws IOException, GeneralSecurityException {
        Calendar service = getCalendarService();
        service.events().delete("primary", eventId).execute();
    }

    /**
     * Récupère un événement spécifique par son ID
     * @param eventId ID de l'événement
     * @return L'événement trouvé
     */
    public Event getEvent(String eventId) throws IOException, GeneralSecurityException {
        Calendar service = getCalendarService();
        return service.events().get("primary", eventId).execute();
    }

    /**
     * Recherche des événements par titre
     * @param searchQuery Terme de recherche
     * @param maxResults Nombre maximum de résultats
     * @return Liste des événements trouvés
     */
    public List<Event> searchEvents(String searchQuery, int maxResults) throws IOException, GeneralSecurityException {
        Calendar service = getCalendarService();

        Events events = service.events().list("primary")
                .setQ(searchQuery)
                .setMaxResults(maxResults)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .execute();
        return events.getItems();
    }

    /**
     * Liste les événements dans une plage de dates
     * @param startDate Date de début
     * @param endDate Date de fin
     * @param maxResults Nombre maximum de résultats
     * @return Liste des événements dans la plage
     */
    public List<Event> listEventsBetweenDates(LocalDateTime startDate, LocalDateTime endDate,
                                              int maxResults) throws IOException, GeneralSecurityException {
        Calendar service = getCalendarService();

        DateTime startTime = new DateTime(startDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        DateTime endTime = new DateTime(endDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

        Events events = service.events().list("primary")
                .setTimeMin(startTime)
                .setTimeMax(endTime)
                .setMaxResults(maxResults)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .execute();
        return events.getItems();
    }
}
