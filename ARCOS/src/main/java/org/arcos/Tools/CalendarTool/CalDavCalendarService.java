package org.arcos.Tools.CalendarTool;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.*;
import org.arcos.Configuration.CalDavProperties;
import org.arcos.Tools.CalendarTool.model.CalendarEvent;
import lombok.extern.slf4j.Slf4j;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Slf4j
public class CalDavCalendarService {

    private static final String ICAL_DATE_TIME_FORMAT = "yyyyMMdd'T'HHmmss";
    private static final String ICAL_DATE_FORMAT = "yyyyMMdd";
    private static final DateTimeFormatter ICAL_DT_FORMATTER = DateTimeFormatter.ofPattern(ICAL_DATE_TIME_FORMAT);

    private final CalDavProperties properties;
    private final RestClient restClient;
    private volatile boolean available;

    public CalDavCalendarService(CalDavProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getUrl())
                .defaultHeaders(headers -> {
                    headers.setBasicAuth(properties.getUsername(), properties.getPassword());
                })
                .build();
        this.available = checkRadicaleAvailability();
    }

    // Visible for testing
    CalDavCalendarService(CalDavProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
        this.available = checkRadicaleAvailability();
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Vérifie la disponibilité de Radicale via un GET sur l'URL de base.
     */
    private boolean checkRadicaleAvailability() {
        try {
            restClient.get()
                    .uri("/.web")
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("Radicale non disponible: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Liste les événements à venir (depuis maintenant).
     */
    public List<CalendarEvent> listUpcomingEvents(int maxResults) {
        String startUtc = LocalDateTime.now().atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC)
                .format(ICAL_DT_FORMATTER) + "Z";

        String xmlBody = buildCalendarQueryXml(startUtc, null);
        List<CalendarEvent> events = executeReport(xmlBody);

        events.sort(Comparator.comparing(CalendarEvent::getStartDateTime,
                Comparator.nullsLast(Comparator.naturalOrder())));

        return events.stream().limit(maxResults).collect(Collectors.toList());
    }

    /**
     * Crée un événement avec horaires précis.
     */
    public CalendarEvent createEvent(String title, String description,
                                     LocalDateTime startDateTime, LocalDateTime endDateTime,
                                     String location) {
        String uid = UUID.randomUUID().toString();
        String icalContent = buildICalEvent(uid, title, description, location,
                "DTSTART:" + formatLocalDateTimeToIcal(startDateTime),
                "DTEND:" + formatLocalDateTimeToIcal(endDateTime));

        putEvent(uid, icalContent);

        return CalendarEvent.builder()
                .id(uid)
                .title(title)
                .description(description)
                .location(location)
                .startDateTime(startDateTime)
                .endDateTime(endDateTime)
                .allDay(false)
                .build();
    }

    /**
     * Crée un événement journée entière.
     */
    public CalendarEvent createAllDayEvent(String title, String description,
                                            LocalDate date, String location) {
        String uid = UUID.randomUUID().toString();
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(ICAL_DATE_FORMAT);
        String icalContent = buildICalEvent(uid, title, description, location,
                "DTSTART;VALUE=DATE:" + date.format(dateFormatter),
                "DTEND;VALUE=DATE:" + date.plusDays(1).format(dateFormatter));

        putEvent(uid, icalContent);

        return CalendarEvent.builder()
                .id(uid)
                .title(title)
                .description(description)
                .location(location)
                .startDateTime(startOfDay)
                .endDateTime(endOfDay)
                .allDay(true)
                .build();
    }

    /**
     * Supprime un événement par son ID (UID iCal).
     */
    public void deleteEvent(String eventId) {
        try {
            String eventUrl = eventUrl(eventId);
            restClient.delete()
                    .uri(eventUrl)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Événement supprimé: {}", eventId);
        } catch (RestClientException e) {
            log.error("Erreur suppression événement {}: {}", eventId, e.getMessage());
            throw e;
        }
    }

    /**
     * Récupère un événement par son ID.
     */
    public CalendarEvent getEvent(String eventId) {
        try {
            String eventUrl = eventUrl(eventId);
            String icalBody = restClient.get()
                    .uri(eventUrl)
                    .retrieve()
                    .body(String.class);
            if (icalBody == null) {
                return null;
            }
            List<CalendarEvent> events = parseICalResponse(icalBody);
            return events.isEmpty() ? null : events.getFirst();
        } catch (RestClientException e) {
            log.error("Erreur récupération événement {}: {}", eventId, e.getMessage());
            throw e;
        }
    }

    /**
     * Recherche des événements par titre (filtre client-side, Radicale ne supporte pas text-match fiable).
     */
    public List<CalendarEvent> searchEvents(String searchQuery, int maxResults) {
        // Fetch all upcoming events and filter client-side
        List<CalendarEvent> allEvents = listUpcomingEvents(200);
        String lowerQuery = searchQuery.toLowerCase();

        return allEvents.stream()
                .filter(e -> e.getTitle() != null && e.getTitle().toLowerCase().contains(lowerQuery))
                .limit(maxResults)
                .collect(Collectors.toList());
    }

    /**
     * Liste les événements entre deux dates.
     */
    public List<CalendarEvent> listEventsBetweenDates(LocalDateTime startDate, LocalDateTime endDate,
                                                       int maxResults) {
        String startUtc = startDate.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC)
                .format(ICAL_DT_FORMATTER) + "Z";
        String endUtc = endDate.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC)
                .format(ICAL_DT_FORMATTER) + "Z";

        String xmlBody = buildCalendarQueryXml(startUtc, endUtc);
        List<CalendarEvent> events = executeReport(xmlBody);

        events.sort(Comparator.comparing(CalendarEvent::getStartDateTime,
                Comparator.nullsLast(Comparator.naturalOrder())));

        return events.stream().limit(maxResults).collect(Collectors.toList());
    }

    // ======================== CalDAV HTTP Operations ========================

    private String eventUrl(String uid) {
        return properties.getCalendarUrl() + uid + ".ics";
    }

    private void putEvent(String uid, String icalContent) {
        try {
            String eventUrl = eventUrl(uid);
            restClient.put()
                    .uri(eventUrl)
                    .contentType(MediaType.parseMediaType("text/calendar; charset=utf-8"))
                    .body(icalContent)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Événement créé/mis à jour: {}", uid);
        } catch (RestClientException e) {
            log.error("Erreur PUT événement {}: {}", uid, e.getMessage());
            throw e;
        }
    }

    private List<CalendarEvent> executeReport(String xmlBody) {
        String calendarUrl = properties.getCalendarUrl();
        try {
            String response = restClient.method(HttpMethod.valueOf("REPORT"))
                    .uri(calendarUrl)
                    .header(HttpHeaders.CONTENT_TYPE, "application/xml; charset=utf-8")
                    .header("Depth", "1")
                    .body(xmlBody)
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                return new ArrayList<>();
            }
            return parseMultistatusResponse(response);
        } catch (RestClientException e) {
            log.error("Erreur REPORT CalDAV: {}", e.getMessage());
            throw e;
        }
    }

    // ======================== iCal Generation ========================

    private String buildICalEvent(String uid, String title, String description,
                                   String location, String dtStartLine, String dtEndLine) {
        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//ARCOS//CalDAV//FR\r\n");
        sb.append("BEGIN:VEVENT\r\n");
        sb.append("UID:").append(uid).append("\r\n");
        sb.append("DTSTAMP:").append(nowUtcFormatted()).append("\r\n");
        sb.append(dtStartLine).append("\r\n");
        sb.append(dtEndLine).append("\r\n");
        sb.append("SUMMARY:").append(escapeIcalText(title)).append("\r\n");
        if (description != null && !description.isBlank()) {
            sb.append("DESCRIPTION:").append(escapeIcalText(description)).append("\r\n");
        }
        if (location != null && !location.isBlank()) {
            sb.append("LOCATION:").append(escapeIcalText(location)).append("\r\n");
        }
        sb.append("END:VEVENT\r\n");
        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    private String formatLocalDateTimeToIcal(LocalDateTime ldt) {
        return ldt.format(ICAL_DT_FORMATTER);
    }

    private String nowUtcFormatted() {
        return LocalDateTime.now(ZoneOffset.UTC).format(ICAL_DT_FORMATTER) + "Z";
    }

    private String escapeIcalText(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n");
    }

    // ======================== CalDAV XML ========================

    private String buildCalendarQueryXml(String startUtc, String endUtc) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<C:calendar-query xmlns:D=\"DAV:\" xmlns:C=\"urn:ietf:params:xml:ns:caldav\">\n");
        sb.append("  <D:prop>\n");
        sb.append("    <D:getetag/>\n");
        sb.append("    <C:calendar-data/>\n");
        sb.append("  </D:prop>\n");
        sb.append("  <C:filter>\n");
        sb.append("    <C:comp-filter name=\"VCALENDAR\">\n");
        sb.append("      <C:comp-filter name=\"VEVENT\">\n");
        if (startUtc != null || endUtc != null) {
            sb.append("        <C:time-range");
            if (startUtc != null) sb.append(" start=\"").append(startUtc).append("\"");
            if (endUtc != null) sb.append(" end=\"").append(endUtc).append("\"");
            sb.append("/>\n");
        }
        sb.append("      </C:comp-filter>\n");
        sb.append("    </C:comp-filter>\n");
        sb.append("  </C:filter>\n");
        sb.append("</C:calendar-query>\n");
        return sb.toString();
    }

    // ======================== Response Parsing ========================

    /**
     * Parse a WebDAV multistatus XML response containing calendar-data.
     */
    private List<CalendarEvent> parseMultistatusResponse(String xmlResponse) {
        List<CalendarEvent> events = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // Security: disable external entities
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xmlResponse.getBytes(StandardCharsets.UTF_8)));

            // Find all calendar-data elements
            NodeList calDataNodes = doc.getElementsByTagNameNS("urn:ietf:params:xml:ns:caldav", "calendar-data");
            // Also extract hrefs for event IDs
            NodeList hrefNodes = doc.getElementsByTagNameNS("DAV:", "href");

            for (int i = 0; i < calDataNodes.getLength(); i++) {
                String icalData = calDataNodes.item(i).getTextContent();
                if (icalData == null || icalData.isBlank()) continue;

                // Extract UID from href if available
                String hrefUid = null;
                if (i < hrefNodes.getLength()) {
                    String href = hrefNodes.item(i).getTextContent();
                    if (href != null && href.endsWith(".ics")) {
                        String filename = href.substring(href.lastIndexOf('/') + 1);
                        hrefUid = filename.replace(".ics", "");
                    }
                }

                List<CalendarEvent> parsed = parseICalResponse(icalData);
                for (CalendarEvent event : parsed) {
                    // Use href-based ID if UID extraction from iCal failed
                    if (event.getId() == null && hrefUid != null) {
                        event.setId(hrefUid);
                    }
                    events.add(event);
                }
            }
        } catch (Exception e) {
            log.error("Erreur parsing réponse multistatus CalDAV: {}", e.getMessage());
        }
        return events;
    }

    /**
     * Parse iCalendar text into CalendarEvent objects using ical4j.
     */
    public List<CalendarEvent> parseICalResponse(String icalData) {
        List<CalendarEvent> events = new ArrayList<>();
        try {
            CalendarBuilder calendarBuilder = new CalendarBuilder();
            Calendar calendar = calendarBuilder.build(new StringReader(icalData));

            for (Component component : calendar.getComponents()) {
                if (component instanceof VEvent vEvent) {
                    events.add(convertVEventToCalendarEvent(vEvent));
                }
            }
        } catch (IOException | ParserException e) {
            log.warn("Erreur parsing iCal: {}", e.getMessage());
        }
        return events;
    }

    private CalendarEvent convertVEventToCalendarEvent(VEvent vEvent) {
        CalendarEvent event = new CalendarEvent();

        // UID
        Optional<Uid> uid = vEvent.getProperty(Property.UID);
        uid.ifPresent(u -> event.setId(u.getValue()));

        // SUMMARY (title)
        Optional<Summary> summary = vEvent.getProperty(Property.SUMMARY);
        summary.ifPresent(s -> event.setTitle(s.getValue()));

        // DESCRIPTION
        Optional<Description> desc = vEvent.getProperty(Property.DESCRIPTION);
        desc.ifPresent(d -> event.setDescription(d.getValue()));

        // LOCATION
        Optional<Location> loc = vEvent.getProperty(Property.LOCATION);
        loc.ifPresent(l -> event.setLocation(l.getValue()));

        // DTSTART
        Optional<DtStart<?>> dtStart = vEvent.getProperty(Property.DTSTART);
        if (dtStart.isPresent()) {
            Object dateValue = dtStart.get().getDate();
            if (dateValue instanceof LocalDate localDate) {
                event.setStartDateTime(localDate.atStartOfDay());
                event.setAllDay(true);
            } else if (dateValue instanceof LocalDateTime ldt) {
                event.setStartDateTime(ldt);
            } else if (dateValue instanceof java.time.temporal.Temporal temporal) {
                event.setStartDateTime(parseTemporalToLocalDateTime(temporal));
            }
        }

        // DTEND
        Optional<DtEnd<?>> dtEnd = vEvent.getProperty(Property.DTEND);
        if (dtEnd.isPresent()) {
            Object dateValue = dtEnd.get().getDate();
            if (dateValue instanceof LocalDate localDate) {
                event.setEndDateTime(localDate.atStartOfDay());
            } else if (dateValue instanceof LocalDateTime ldt) {
                event.setEndDateTime(ldt);
            } else if (dateValue instanceof java.time.temporal.Temporal temporal) {
                event.setEndDateTime(parseTemporalToLocalDateTime(temporal));
            }
        }

        return event;
    }

    private LocalDateTime parseTemporalToLocalDateTime(java.time.temporal.Temporal temporal) {
        try {
            if (temporal instanceof ZonedDateTime zdt) {
                return zdt.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            }
            if (temporal instanceof Instant instant) {
                return instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
            }
            return LocalDateTime.from(temporal);
        } catch (Exception e) {
            log.warn("Impossible de parser temporal {}: {}", temporal, e.getMessage());
            return LocalDateTime.now();
        }
    }
}
