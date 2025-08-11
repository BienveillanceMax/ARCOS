package Tools.NewsAnalysisTool;


import Exceptions.GdeltServiceException;
import Tools.NewsAnalysisTool.Analyzers.GeopoliticalAnalyzer;
import Tools.NewsAnalysisTool.Analyzers.MediaCoverageAnalyzer;
import Tools.NewsAnalysisTool.Analyzers.SentimentAnalyzer;
import Tools.NewsAnalysisTool.Analyzers.TrendAnalyzer;
import Tools.NewsAnalysisTool.models.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service principal d'analyse GDELT pour l'assistant IA
 * Fournit des capacités d'analyse des actualités et d'OSINT
 */
@Service
public class GdeltNewsAnalysisService {

    private final GdeltApiClient gdeltClient;
    private final SentimentAnalyzer sentimentAnalyzer;
    private final TrendAnalyzer trendAnalyzer;
    private final GeopoliticalAnalyzer geopoliticalAnalyzer;
    private final MediaCoverageAnalyzer mediaCoverageAnalyzer;
    private final ExecutorService executorService;

    @Autowired
    public GdeltNewsAnalysisService(GdeltApiClient gdeltClient) {
        this.gdeltClient = gdeltClient;
        this.sentimentAnalyzer = new SentimentAnalyzer();
        this.trendAnalyzer = new TrendAnalyzer();
        this.geopoliticalAnalyzer = new GeopoliticalAnalyzer();
        this.mediaCoverageAnalyzer = new MediaCoverageAnalyzer();
        this.executorService = Executors.newFixedThreadPool(5);
    }

    /**
     * Récupère les événements récents selon des filtres
     */
    public List<GdeltEvent> getRecentEvents(EventFilter filter) {
        try {
            String query = buildGdeltQuery(filter);
            List<GdeltEvent> events = gdeltClient.queryEvents(query);

            return events.stream()
                    .filter(event -> matchesFilter(event, filter))
                    .sorted((e1, e2) -> e2.getEventDate().compareTo(e1.getEventDate()))
                    .limit(filter.getMaxResults())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new GdeltServiceException("Erreur lors de la récupération des événements récents", e);
        }
    }

    /**
     * Analyse la tonalité globale d'un événement
     */
    public SentimentAnalysis analyzeSentiment(String eventId) {
        try {
            GdeltEvent event = gdeltClient.getEventById(eventId);
            List<String> articles = gdeltClient.getRelatedArticles(eventId);

            return sentimentAnalyzer.analyzeSentiment(event, articles);
        } catch (Exception e) {
            throw new GdeltServiceException("Erreur lors de l'analyse de sentiment", e);
        }
    }

    /**
     * Donne les informations complètes d'un événement précis
     */
    public DetailedEventInfo getEventDetails(String eventId) {
        try {
            GdeltEvent event = gdeltClient.getEventById(eventId);
            List<String> relatedArticles = gdeltClient.getRelatedArticles(eventId);
            List<Actor> actors = extractActors(event);
            LocationInfo location = extractLocation(event);
            List<String> sources = extractSources(relatedArticles);

            return DetailedEventInfo.builder()
                    .event(event)
                    .summary(generateSummary(event, relatedArticles))
                    .actors(actors)
                    .location(location)
                    .sources(sources)
                    .relatedArticles(relatedArticles)
                    .build();
        } catch (Exception e) {
            throw new GdeltServiceException("Erreur lors de la récupération des détails de l'événement", e);
        }
    }

    /**
     * Recherche d'événements passés sur une période définie
     */
    public List<GdeltEvent> searchHistoricalEvents(String topic, LocalDateTime startDate,
                                                   LocalDateTime endDate, EventFilter filter) {
        try {
            String dateRange = formatDateRange(startDate, endDate);
            String query = buildHistoricalQuery(topic, dateRange, filter);

            List<GdeltEvent> events = gdeltClient.queryHistoricalEvents(query);

            return events.stream()
                    .filter(event -> isWithinDateRange(event, startDate, endDate))
                    .sorted((e1, e2) -> e2.getEventDate().compareTo(e1.getEventDate()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new GdeltServiceException("Erreur lors de la recherche historique", e);
        }
    }

    /**
     * Repère des pics inhabituels dans la couverture médiatique
     */
    public CoverageSpikes detectCoverageSpikes(String topic, int daysBack) {
        try {
            LocalDateTime endDate = LocalDateTime.now();
            LocalDateTime startDate = endDate.minusDays(daysBack);

            List<GdeltEvent> events = searchHistoricalEvents(topic, startDate, endDate,
                    EventFilter.defaultFilter());

            return mediaCoverageAnalyzer.detectSpikes(events, topic);
        } catch (Exception e) {
            throw new GdeltServiceException("Erreur lors de la détection des pics de couverture", e);
        }
    }

    /**
     * Raconte l'historique médiatique d'un sujet
     */
    public MediaHistoryNarrative getMediaHistory(String topic, int monthsBack) {
        try {
            LocalDateTime endDate = LocalDateTime.now();
            LocalDateTime startDate = endDate.minusMonths(monthsBack);

            List<GdeltEvent> events = searchHistoricalEvents(topic, startDate, endDate,
                    EventFilter.defaultFilter());

            List<TrendPeriod> trendPeriods = trendAnalyzer.analyzeTrends(events);
            List<KeyMoment> keyMoments = identifyKeyMoments(events);

            return MediaHistoryNarrative.builder()
                    .topic(topic)
                    .period(startDate + " - " + endDate)
                    .trendPeriods(trendPeriods)
                    .keyMoments(keyMoments)
                    .overallEvolution(calculateOverallEvolution(trendPeriods))
                    .summary(generateHistorySummary(topic, trendPeriods, keyMoments))
                    .build();
        } catch (Exception e) {
            throw new GdeltServiceException("Erreur lors de la génération de l'historique médiatique", e);
        }
    }

    /**
     * Crée un bulletin des tendances géopolitiques mondiales
     */
    public GeopoliticalBulletin createGeopoliticalBulletin() {
        try {
            List<CompletableFuture<RegionalTrends>> futures = Arrays.asList(
                    CompletableFuture.supplyAsync(() -> analyzeRegion("Europe"), executorService),
                    CompletableFuture.supplyAsync(() -> analyzeRegion("Asia"), executorService),
                    CompletableFuture.supplyAsync(() -> analyzeRegion("Americas"), executorService),
                    CompletableFuture.supplyAsync(() -> analyzeRegion("Africa"), executorService),
                    CompletableFuture.supplyAsync(() -> analyzeRegion("Middle East"), executorService)
            );

            List<RegionalTrends> regionalTrends = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            List<GlobalTrend> globalTrends = geopoliticalAnalyzer.identifyGlobalTrends(regionalTrends);
            List<RiskAssessment> riskAssessments = geopoliticalAnalyzer.assessRisks(regionalTrends);

            return GeopoliticalBulletin.builder()
                    .timestamp(LocalDateTime.now())
                    .regionalTrends(regionalTrends)
                    .globalTrends(globalTrends)
                    .riskAssessments(riskAssessments)
                    .summary(geopoliticalAnalyzer.generateBulletinSummary(globalTrends, riskAssessments))
                    .build();
        } catch (Exception e) {
            throw new GdeltServiceException("Erreur lors de la création du bulletin géopolitique", e);
        }
    }

    /**
     * Identifie si la couverture médiatique est en hausse, stable ou baisse
     */
    public CoverageTrend analyzeCoverageTrend(String topic, int daysToAnalyze) {
        try {
            LocalDateTime endDate = LocalDateTime.now();
            LocalDateTime startDate = endDate.minusDays(daysToAnalyze);

            List<GdeltEvent> events = searchHistoricalEvents(topic, startDate, endDate,
                    EventFilter.defaultFilter());

            return mediaCoverageAnalyzer.analyzeTrend(events, topic, daysToAnalyze);
        } catch (Exception e) {
            throw new GdeltServiceException("Erreur lors de l'analyse des tendances de couverture", e);
        }
    }

    /**
     * Compare la couverture d'un sujet dans plusieurs zones géographiques
     */
    public GeographicalCoverageComparison compareGeographicalCoverage(String topic,
                                                                      List<String> regions,
                                                                      int daysBack) {
        try {
            Map<String, List<GdeltEvent>> regionalEvents = new HashMap<>();

            for (String region : regions) {
                EventFilter filter = EventFilter.builder()
                        .region(region)
                        .maxResults(500)
                        .build();

                LocalDateTime endDate = LocalDateTime.now();
                LocalDateTime startDate = endDate.minusDays(daysBack);

                List<GdeltEvent> events = searchHistoricalEvents(topic, startDate, endDate, filter);
                regionalEvents.put(region, events);
            }

            return mediaCoverageAnalyzer.compareRegionalCoverage(topic, regionalEvents);
        } catch (Exception e) {
            throw new GdeltServiceException("Erreur lors de la comparaison géographique", e);
        }
    }

    // Méthodes utilitaires privées

    private String buildGdeltQuery(EventFilter filter) {
        StringBuilder query = new StringBuilder();

        if (filter.getKeywords() != null && !filter.getKeywords().isEmpty()) {
            query.append("(").append(String.join(" OR ", filter.getKeywords())).append(")");
        }

        if (filter.getCountry() != null) {
            query.append(" country:").append(filter.getCountry());
        }

        if (filter.getRegion() != null) {
            query.append(" region:").append(filter.getRegion());
        }

        if (filter.getTheme() != null) {
            query.append(" theme:").append(filter.getTheme());
        }

        return query.toString();
    }

    private boolean matchesFilter(GdeltEvent event, EventFilter filter) {
        if (filter.getMinGoldsteinScale() != null &&
                event.getGoldsteinScale() < filter.getMinGoldsteinScale()) {
            return false;
        }

        if (filter.getMaxGoldsteinScale() != null &&
                event.getGoldsteinScale() > filter.getMaxGoldsteinScale()) {
            return false;
        }

        return true;
    }

    private RegionalTrends analyzeRegion(String region) {
        EventFilter filter = EventFilter.builder()
                .region(region)
                .maxResults(200)
                .build();

        List<GdeltEvent> recentEvents = getRecentEvents(filter);
        return geopoliticalAnalyzer.analyzeRegionalTrends(region, recentEvents);
    }

    private String formatDateRange(LocalDateTime start, LocalDateTime end) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        return start.format(formatter) + "-" + end.format(formatter);
    }

    private String buildHistoricalQuery(String topic, String dateRange, EventFilter filter) {
        StringBuilder query = new StringBuilder();
        query.append("\"").append(topic).append("\"");
        query.append(" timespan:").append(dateRange);

        if (filter.getCountry() != null) {
            query.append(" country:").append(filter.getCountry());
        }

        return query.toString();
    }

    private boolean isWithinDateRange(GdeltEvent event, LocalDateTime start, LocalDateTime end) {
        LocalDateTime eventDate = event.getEventDate();
        return !eventDate.isBefore(start) && !eventDate.isAfter(end);
    }

    private List<Actor> extractActors(GdeltEvent event) {
        // Logique d'extraction des acteurs depuis les données GDELT
        List<Actor> actors = new ArrayList<>();

        if (event.getActor1Name() != null) {
            actors.add(Actor.builder()
                    .name(event.getActor1Name())
                    .type(event.getActor1Type())
                    .countryCode(event.getActor1CountryCode())
                    .role("Actor1")
                    .build());
        }

        if (event.getActor2Name() != null) {
            actors.add(Actor.builder()
                    .name(event.getActor2Name())
                    .type(event.getActor2Type())
                    .countryCode(event.getActor2CountryCode())
                    .role("Actor2")
                    .build());
        }

        return actors;
    }

    private LocationInfo extractLocation(GdeltEvent event) {
        return LocationInfo.builder()
                .country(event.getActionGeoCountryCode())
                .region(event.getActionGeoAdm1Code())
                .city(event.getActionGeoFullName())
                .latitude(event.getActionGeoLat())
                .longitude(event.getActionGeoLong())
                .build();
    }

    private List<String> extractSources(List<String> articles) {
        return articles.stream()
                .map(this::extractDomainFromUrl)
                .distinct()
                .collect(Collectors.toList());
    }

    private String extractDomainFromUrl(String url) {
        try {
            return new java.net.URL(url).getHost();
        } catch (Exception e) {
            return "source inconnue";
        }
    }

    private String generateSummary(GdeltEvent event, List<String> articles) {
        // Génération d'un résumé basé sur l'événement et les articles associés
        StringBuilder summary = new StringBuilder();
        summary.append("Événement: ").append(event.getEventCode())
                .append(" impliquant ").append(event.getActor1Name());

        if (event.getActor2Name() != null) {
            summary.append(" et ").append(event.getActor2Name());
        }

        summary.append(" le ").append(event.getEventDate().toLocalDate())
                .append(" (").append(articles.size()).append(" articles trouvés)");

        return summary.toString();
    }

    private List<KeyMoment> identifyKeyMoments(List<GdeltEvent> events) {
        return events.stream()
                .filter(event -> Math.abs(event.getGoldsteinScale()) > 5.0) // Événements significatifs
                .map(event -> KeyMoment.builder()
                        .date(event.getEventDate())
                        .description(generateEventDescription(event))
                        .significance(calculateSignificance(event))
                        .build())
                .sorted((km1, km2) -> km2.getSignificance().compareTo(km1.getSignificance()))
                .limit(10)
                .collect(Collectors.toList());
    }

    private String generateEventDescription(GdeltEvent event) {
        return String.format("%s - %s impliquant %s",
                event.getEventDate().toLocalDate(),
                event.getEventCode(),
                event.getActor1Name());
    }

    private Double calculateSignificance(GdeltEvent event) {
        return Math.abs(event.getGoldsteinScale()) +
                (event.getNumMentions() != null ? Math.log(event.getNumMentions()) : 0);
    }

    private String calculateOverallEvolution(List<TrendPeriod> trends) {
        if (trends.isEmpty()) return "Aucune donnée disponible";

        double firstPeriodIntensity = trends.get(0).getIntensity();
        double lastPeriodIntensity = trends.get(trends.size() - 1).getIntensity();

        double change = ((lastPeriodIntensity - firstPeriodIntensity) / firstPeriodIntensity) * 100;

        if (change > 20) return "Forte hausse";
        else if (change > 5) return "Hausse modérée";
        else if (change < -20) return "Forte baisse";
        else if (change < -5) return "Baisse modérée";
        else return "Stable";
    }

    private String generateHistorySummary(String topic, List<TrendPeriod> trends,
                                          List<KeyMoment> keyMoments) {
        StringBuilder summary = new StringBuilder();
        summary.append("Analyse de l'évolution médiatique de '").append(topic).append("': ");

        if (!trends.isEmpty()) {
            summary.append(trends.size()).append(" périodes distinctes identifiées. ");
        }

        if (!keyMoments.isEmpty()) {
            summary.append(keyMoments.size()).append(" moments clés détectés, ");
            summary.append("le plus significatif étant: ")
                    .append(keyMoments.get(0).getDescription());
        }

        return summary.toString();
    }
}

