package org.arcos.Tools.CalendarTool;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponseException;
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
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

@Slf4j
public class CalendarService {

    private static final String APPLICATION_NAME = "ARCOS AI Assistant";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR);
    private static final String CREDENTIALS_FILE_PATH = "/client_secrets.json";

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    private volatile boolean available;
    private volatile Calendar cachedClient;

    public CalendarService() {
        // Phase 1: check client_secrets.json exists
        if (CalendarService.class.getResourceAsStream(CREDENTIALS_FILE_PATH) == null) {
            this.available = false;
            return;
        }

        // Phase 2: validate OAuth tokens are present and refreshable
        try {
            this.cachedClient = buildCalendarClient();
            this.available = true;
        } catch (Exception e) {
            log.warn("Calendrier désactivé : {}", e.getMessage());
            this.available = false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    private Credential loadCredentialNonBlocking() throws IOException, GeneralSecurityException {
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

        // Load stored credential WITHOUT triggering browser flow
        Credential credential = flow.loadCredential("user");
        if (credential == null) {
            throw new IOException("Aucun token OAuth stocké. Lancez : mvn exec:java -Dexec.mainClass=\"org.arcos.Tools.CalendarTool.CalendarOAuthSetup\"");
        }

        if (credential.getRefreshToken() == null) {
            throw new IOException("Token OAuth sans refresh token. Révoquez l'accès (https://myaccount.google.com/permissions) et relancez CalendarOAuthSetup.");
        }

        // Refresh if expired or about to expire (5 min buffer)
        if (credential.getExpiresInSeconds() != null && credential.getExpiresInSeconds() <= 300) {
            log.info("Token OAuth expirant dans {}s, tentative de refresh...", credential.getExpiresInSeconds());
            if (!credential.refreshToken()) {
                throw new IOException("Impossible de rafraîchir le token OAuth. Relancez CalendarOAuthSetup.");
            }
            log.info("Token OAuth rafraîchi avec succès.");
        }

        return credential;
    }

    private Calendar buildCalendarClient() throws IOException, GeneralSecurityException {
        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        Credential credential = loadCredentialNonBlocking();

        HttpRequestInitializer timeoutInitializer = new HttpRequestInitializer() {
            @Override
            public void initialize(HttpRequest request) throws IOException {
                credential.initialize(request);
                request.setConnectTimeout(CONNECT_TIMEOUT_MS);
                request.setReadTimeout(READ_TIMEOUT_MS);
            }
        };

        return new Calendar.Builder(httpTransport, JSON_FACTORY, timeoutInitializer)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    private Calendar getCalendarService() throws IOException, GeneralSecurityException {
        if (cachedClient != null) {
            return cachedClient;
        }
        cachedClient = buildCalendarClient();
        return cachedClient;
    }

    /**
     * Marque le service comme dégradé suite à une erreur d'authentification.
     */
    private void degradeOnAuthFailure(Exception e) {
        log.error("Calendrier dégradé suite à une erreur d'authentification : {}", e.getMessage());
        this.available = false;
        this.cachedClient = null;
    }

    /**
     * Vérifie si l'exception est liée à un problème d'authentification (401/403).
     */
    private boolean isAuthError(Exception e) {
        if (e instanceof HttpResponseException httpEx) {
            int code = httpEx.getStatusCode();
            return code == 401 || code == 403;
        }
        return false;
    }

    public List<Event> listUpcomingEvents(int maxResults) throws IOException, GeneralSecurityException {
        try {
            Calendar service = getCalendarService();
            Events events = service.events().list("primary")
                    .setMaxResults(maxResults)
                    .setTimeMin(new DateTime(System.currentTimeMillis()))
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute();
            return events.getItems();
        } catch (Exception e) {
            if (isAuthError(e)) degradeOnAuthFailure(e);
            throw e;
        }
    }

    /**
     * Crée un nouvel événement dans le calendrier
     */
    public Event createEvent(String title, String description, LocalDateTime startDateTime,
                             LocalDateTime endDateTime, String location) throws IOException, GeneralSecurityException {
        try {
            Calendar service = getCalendarService();

            Event event = new Event()
                    .setSummary(title)
                    .setDescription(description)
                    .setLocation(location);

            DateTime startTime = new DateTime(startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            EventDateTime start = new EventDateTime()
                    .setDateTime(startTime)
                    .setTimeZone(ZoneId.systemDefault().getId());
            event.setStart(start);

            DateTime endTime = new DateTime(endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            EventDateTime end = new EventDateTime()
                    .setDateTime(endTime)
                    .setTimeZone(ZoneId.systemDefault().getId());
            event.setEnd(end);

            return service.events().insert("primary", event).execute();
        } catch (Exception e) {
            if (isAuthError(e)) degradeOnAuthFailure(e);
            throw e;
        }
    }

    /**
     * Crée un événement toute la journée
     */
    public Event createAllDayEvent(String title, String description, java.time.LocalDate date,
                                   String location) throws IOException, GeneralSecurityException {
        try {
            Calendar service = getCalendarService();

            Event event = new Event()
                    .setSummary(title)
                    .setDescription(description)
                    .setLocation(location);

            EventDateTime start = new EventDateTime()
                    .setDate(new DateTime(date.toString()));
            event.setStart(start);

            EventDateTime end = new EventDateTime()
                    .setDate(new DateTime(date.plusDays(1).toString()));
            event.setEnd(end);

            return service.events().insert("primary", event).execute();
        } catch (Exception e) {
            if (isAuthError(e)) degradeOnAuthFailure(e);
            throw e;
        }
    }

    /**
     * Met à jour un événement existant
     */
    public Event updateEvent(String eventId, String title, String description,
                             LocalDateTime startDateTime, LocalDateTime endDateTime,
                             String location) throws IOException, GeneralSecurityException {
        try {
            Calendar service = getCalendarService();

            Event event = service.events().get("primary", eventId).execute();

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
        } catch (Exception e) {
            if (isAuthError(e)) degradeOnAuthFailure(e);
            throw e;
        }
    }

    /**
     * Met à jour partiellement un événement (seuls les champs non-null sont mis à jour)
     */
    public Event updateEventPartial(String eventId, String title, String description,
                                    String location) throws IOException, GeneralSecurityException {
        return updateEvent(eventId, title, description, null, null, location);
    }

    /**
     * Supprime un événement du calendrier
     */
    public void deleteEvent(String eventId) throws IOException, GeneralSecurityException {
        try {
            Calendar service = getCalendarService();
            service.events().delete("primary", eventId).execute();
        } catch (Exception e) {
            if (isAuthError(e)) degradeOnAuthFailure(e);
            throw e;
        }
    }

    /**
     * Récupère un événement spécifique par son ID
     */
    public Event getEvent(String eventId) throws IOException, GeneralSecurityException {
        try {
            Calendar service = getCalendarService();
            return service.events().get("primary", eventId).execute();
        } catch (Exception e) {
            if (isAuthError(e)) degradeOnAuthFailure(e);
            throw e;
        }
    }

    /**
     * Recherche des événements par titre
     */
    public List<Event> searchEvents(String searchQuery, int maxResults) throws IOException, GeneralSecurityException {
        try {
            Calendar service = getCalendarService();

            Events events = service.events().list("primary")
                    .setQ(searchQuery)
                    .setMaxResults(maxResults)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute();
            return events.getItems();
        } catch (Exception e) {
            if (isAuthError(e)) degradeOnAuthFailure(e);
            throw e;
        }
    }

    /**
     * Liste les événements dans une plage de dates
     */
    public List<Event> listEventsBetweenDates(LocalDateTime startDate, LocalDateTime endDate,
                                              int maxResults) throws IOException, GeneralSecurityException {
        try {
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
        } catch (Exception e) {
            if (isAuthError(e)) degradeOnAuthFailure(e);
            throw e;
        }
    }
}
