package Tools.NewsAnalysisTool;

import Exceptions.GdeltClientException;
import Tools.NewsAnalysisTool.models.GdeltConfig;
import Tools.NewsAnalysisTool.models.GdeltEvent;
import Tools.NewsAnalysisTool.models.MediaMention;
import Tools.NewsAnalysisTool.models.TimelineDataPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Client pour interagir avec les APIs GDELT
 */
@Component
public class GdeltApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final GdeltConfig config;

    // URLs de base des APIs GDELT
    private static final String GDELT_EVENTS_API = "https://api.gdeltproject.org/api/v2/doc/doc";
    private static final String GDELT_GEO_API = "https://api.gdeltproject.org/api/v2/geo/geo";
    private static final String GDELT_TIMELINE_API = "https://api.gdeltproject.org/api/v2/tv/tv";
    private static final String GDELT_MENTIONS_API = "https://api.gdeltproject.org/api/v1/search_ftxtsearch/search_ftxtsearch";

    public GdeltApiClient(RestTemplate restTemplate, GdeltConfig config) {
        this.restTemplate = restTemplate;
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Recherche d'événements via l'API GDELT
     */
    public List<GdeltEvent> queryEvents(String query) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = String.format("%s?query=%s&mode=artlist&format=json&maxrecords=250&sort=DateDesc",
                    GDELT_EVENTS_API, encodedQuery);

            ResponseEntity<String> response = executeRequest(url);
            return parseEventsResponse(response.getBody());

        } catch (Exception e) {
            throw new GdeltClientException("Erreur lors de la requête d'événements GDELT", e);
        }
    }

    /**
     * Recherche d'événements historiques
     */
    public List<GdeltEvent> queryHistoricalEvents(String query) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = String.format("%s?query=%s&mode=artlist&format=json&maxrecords=500&sort=DateDesc&timespan=3mon",
                    GDELT_EVENTS_API, encodedQuery);

            ResponseEntity<String> response = executeRequest(url);
            return parseEventsResponse(response.getBody());

        } catch (Exception e) {
            throw new GdeltClientException("Erreur lors de la requête d'événements historiques", e);
        }
    }

    /**
     * Récupère un événement spécifique par ID
     */
    public GdeltEvent getEventById(String eventId) {
        try {
            String url = String.format("%s?query=globalEventId:%s&mode=artlist&format=json",
                    GDELT_EVENTS_API, eventId);

            ResponseEntity<String> response = executeRequest(url);
            List<GdeltEvent> events = parseEventsResponse(response.getBody());

            return events.isEmpty() ? null : events.get(0);

        } catch (Exception e) {
            throw new GdeltClientException("Erreur lors de la récupération de l'événement " + eventId, e);
        }
    }

    /**
     * Récupère les articles liés à un événement
     */
    public List<String> getRelatedArticles(String eventId) {
        try {
            String url = String.format("%s?query=globalEventId:%s&mode=artlist&format=json&maxrecords=50",
                    GDELT_EVENTS_API, eventId);

            ResponseEntity<String> response = executeRequest(url);
            return parseArticleUrls(response.getBody());

        } catch (Exception e) {
            throw new GdeltClientException("Erreur lors de la récupération des articles pour l'événement " + eventId, e);
        }
    }

    /**
     * Recherche géographique d'événements
     */
    public List<GdeltEvent> queryEventsByLocation(String country, String query, int days) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String encodedCountry = URLEncoder.encode(country, StandardCharsets.UTF_8);

            String url = String.format("%s?query=%s&sourcelang=eng&format=json&mode=pointdata&country=%s&timespan=%dd",
                    GDELT_GEO_API, encodedQuery, encodedCountry, days);

            ResponseEntity<String> response = executeRequest(url);
            return parseGeoEventsResponse(response.getBody());

        } catch (Exception e) {
            throw new GdeltClientException("Erreur lors de la recherche géographique", e);
        }
    }

    /**
     * Analyse des tendances temporelles
     */
    public List<TimelineDataPoint> getTimeline(String query, int days) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = String.format("%s?query=%s&mode=timelinevol&format=json&timespan=%dd&timezoom=yes",
                    GDELT_TIMELINE_API, encodedQuery, days);

            ResponseEntity<String> response = executeRequest(url);
            return parseTimelineResponse(response.getBody());

        } catch (Exception e) {
            throw new GdeltClientException("Erreur lors de la récupération de la timeline", e);
        }
    }

    /**
     * Recherche de mentions dans les médias
     */
    public List<MediaMention> searchMentions(String query, LocalDateTime startDate, LocalDateTime endDate) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String startDateStr = startDate.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String endDateStr = endDate.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

            String url = String.format("%s?query=%s&startdatetime=%s&enddatetime=%s&format=json&maxrecords=250",
                    GDELT_MENTIONS_API, encodedQuery, startDateStr, endDateStr);

            ResponseEntity<String> response = executeRequest(url);
            return parseMentionsResponse(response.getBody());

        } catch (Exception e) {
            throw new GdeltClientException("Erreur lors de la recherche de mentions", e);
        }
    }

    /**
     * Analyse des émotions dans les articles
     */
    public Map<String, Double> getEmotionAnalysis(String query, int days) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = String.format("%s?query=%s&mode=tonechart&format=json&timespan=%dd",
                    GDELT_EVENTS_API, encodedQuery, days);

            ResponseEntity<String> response = executeRequest(url);
            return parseEmotionResponse(response.getBody());

        } catch (Exception e) {
            throw new GdeltClientException("Erreur lors de l'analyse des émotions", e);
        }
    }

    /**
     * Récupère les thèmes dominants pour un sujet
     */
    public List<String> getDominantThemes(String query, int days) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = String.format("%s?query=%s&mode=wordcloud&format=json&timespan=%dd",
                    GDELT_EVENTS_API, encodedQuery, days);

            ResponseEntity<String> response = executeRequest(url);
            return parseThemesResponse(response.getBody());

        } catch (Exception e) {
            throw new GdeltClientException("Erreur lors de la récupération des thèmes", e);
        }
    }

    // Méthodes utilitaires privées

    private ResponseEntity<String> executeRequest(String url) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "GDELT-Assistant-Client/1.0");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            // Ajout d'un délai pour respecter les limites de l'API
            if (config.getRequestDelay() != null && config.getRequestDelay() > 0) {
                try {
                    Thread.sleep(config.getRequestDelay());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            return restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        } catch (HttpClientErrorException e) {
            throw new GdeltClientException("Erreur HTTP lors de la requête GDELT: " + e.getStatusCode(), e);
        }
    }

    private List<GdeltEvent> parseEventsResponse(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            List<GdeltEvent> events = new ArrayList<>();

            if (root.has("articles")) {
                JsonNode articles = root.get("articles");
                for (JsonNode article : articles) {
                    GdeltEvent event = parseEventFromArticle(article);
                    if (event != null) {
                        events.add(event);
                    }
                }
            }

            return events;
        } catch (Exception e) {
            throw new GdeltClientException("Erreur lors du parsing de la réponse événements", e);
        }
    }

    private GdeltEvent parseEventFromArticle(JsonNode article) {
        try {
            return GdeltEvent.builder()
                    .globalEventId(getTextValue(article, "url")) // Utilisation de l'URL comme ID temporaire
                    .eventDate(parseDateTime(getTextValue(article, "seendate")))
                    .sourceUrl(getTextValue(article, "url"))
                    .avgTone(getDoubleValue(article, "tone"))
                    .actionGeoCountryCode(getTextValue(article, "domain"))
                    .actionGeoFullName(getTextValue(article, "socialimage"))
                    .numMentions(1) // Chaque article représente une mention
                    .build();
        } catch (Exception e) {
            return null; // Ignore les articles mal formatés
        }
    }

    private List<GdeltEvent> parseGeoEventsResponse(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            List<GdeltEvent> events = new ArrayList<>();

            if (root.has("data")) {
                JsonNode data = root.get("data");
                for (JsonNode point : data) {
                    GdeltEvent event = parseGeoEvent(point);
                    if (event != null) {
                        events.add(event);
                    }
                }
            }

            return events;
        } catch (Exception e) {
            throw new GdeltClientException("Erreur lors du parsing des événements géographiques", e);
        }
    }

    private GdeltEvent parseGeoEvent(JsonNode point) {
        try {
            return GdeltEvent.builder()
                    .actionGeoLat(getDoubleValue(point, "lat"))
                    .actionGeoLong(getDoubleValue(point, "lon"))
                    .avgTone(getDoubleValue(point, "tone"))
                    .numMentions(getIntValue(point, "count"))
                    .actionGeoFullName(getTextValue(point, "name"))
                    .eventDate(LocalDateTime.now()) // Date approximative
                    .build();
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> parseArticleUrls(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            List<String> urls = new ArrayList<>();

            if (root.has("articles")) {
                JsonNode articles = root.get("articles");
                for (JsonNode article : articles) {
                    String url = getTextValue(article, "url");
                    if (url != null && !url.isEmpty()) {
                        urls.add(url);
                    }
                }
            }

            return urls;
        } catch (Exception e) {
            throw new GdeltClientException("Erreur lors du parsing des URLs d'articles", e);
        }
    }

    private List<TimelineDataPoint> parseTimelineResponse(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            List<TimelineDataPoint> timeline = new ArrayList<>();

            if (root.has("timeline")) {
                JsonNode timelineNode = root.get("timeline");
                for (JsonNode point : timelineNode) {
                    TimelineDataPoint dataPoint = TimelineDataPoint.builder()
                            .timestamp(parseDateTime(getTextValue(point, "date")))
                            .value(getDoubleValue(point, "value"))
                            .volume(getIntValue(point, "volume"))
                            .build();
                    timeline.add(dataPoint);
                }
            }

            return timeline;
        } catch (Exception e) {
            throw new GdeltClientException("Erreur lors du parsing de la timeline", e);
        }
    }

    private List<MediaMention> parseMentionsResponse(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            List<MediaMention> mentions = new ArrayList<>();

            if (root.has("mentions")) {
                JsonNode mentionsNode = root.get("mentions");
                for (JsonNode mention : mentionsNode) {
                    MediaMention mediaMention = MediaMention.builder()
                            .url(getTextValue(mention, "url"))
                            .title(getTextValue(mention, "title"))
                            .domain(getTextValue(mention, "domain"))
                            .publishDate(parseDateTime(getTextValue(mention, "publishdate")))
                            .tone(getDoubleValue(mention, "tone"))
                            .build();
                    mentions.add(mediaMention);
                }
            }

            return mentions;
        } catch (Exception e) {
            throw new GdeltClientException("Erreur lors du parsing des mentions", e);
        }
    }

    private Map<String, Double> parseEmotionResponse(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            Map<String, Double> emotions = new HashMap<>();

            if (root.has("emotions")) {
                JsonNode emotionsNode = root.get("emotions");
                emotionsNode.fieldNames().forEachRemaining(emotion -> {
                    emotions.put(emotion, emotionsNode.get(emotion).asDouble());
                });
            } else {
                // Emotions par défaut si pas disponibles
                emotions.put("positive", 0.3);
                emotions.put("negative", 0.4);
                emotions.put("neutral", 0.3);
            }

            return emotions;
        } catch (Exception e) {
            throw new GdeltClientException("Erreur lors du parsing des émotions", e);
        }
    }

    private List<String> parseThemesResponse(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            List<String> themes = new ArrayList<>();

            if (root.has("themes")) {
                JsonNode themesNode = root.get("themes");
                for (JsonNode theme : themesNode) {
                    themes.add(getTextValue(theme, "theme"));
                }
            }

            return themes;
        } catch (Exception e) {
            throw new GdeltClientException("Erreur lors du parsing des thèmes", e);
        }
    }

    private LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return LocalDateTime.now();
        }

        try {
            // Format GDELT: YYYYMMDDHHMMSS
            if (dateStr.length() >= 8) {
                String cleanDate = dateStr.replaceAll("[^0-9]", "");
                if (cleanDate.length() >= 8) {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
                    // Compléter avec des 0 si nécessaire
                    while (cleanDate.length() < 14) {
                        cleanDate += "00";
                    }
                    return LocalDateTime.parse(cleanDate.substring(0, 14), formatter);
                }
            }
        } catch (Exception e) {
            // En cas d'erreur, retourner la date actuelle
        }

        return LocalDateTime.now();
    }

    private String getTextValue(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asText() : null;
    }

    private Double getDoubleValue(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asDouble() : null;
    }

    private Integer getIntValue(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asInt() : null;
    }
}



