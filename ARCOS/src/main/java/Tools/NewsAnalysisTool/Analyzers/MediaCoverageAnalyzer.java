package Tools.NewsAnalysisTool.Analyzers;

import Tools.NewsAnalysisTool.models.*;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Analyseur de couverture médiatique pour GDELT
 */
@Component
public class MediaCoverageAnalyzer {

    private static final double SPIKE_THRESHOLD_MULTIPLIER = 2.5; // Seuil pour détecter un pic

    /**
     * Détecte les pics inhabituels dans la couverture médiatique
     */
    public CoverageSpikes detectSpikes(List<GdeltEvent> events, String topic) {
        if (events.isEmpty()) {
            return CoverageSpikes.builder()
                    .topic(topic)
                    .spikes(new ArrayList<>())
                    .averageCoverage(0.0)
                    .analysisPeriod("Aucune donnée")
                    .interpretation("Pas d'événements trouvés pour ce sujet")
                    .build();
        }

        Map<LocalDateTime, List<GdeltEvent>> eventsByDay = groupEventsByDay(events);
        Map<LocalDateTime, Integer> dailyCounts = calculateDailyCounts(eventsByDay);

        double averageCoverage = calculateAverageCoverage(dailyCounts);
        List<CoverageSpike> spikes = identifySpikes(dailyCounts, eventsByDay, averageCoverage);

        return CoverageSpikes.builder()
                .topic(topic)
                .spikes(spikes)
                .averageCoverage(averageCoverage)
                .analysisPeriod(generateAnalysisPeriod(events))
                .interpretation(generateSpikeInterpretation(spikes, averageCoverage))
                .build();
    }

    /**
     * Analyse les tendances de couverture (hausse, baisse, stable)
     */
    public CoverageTrend analyzeTrend(List<GdeltEvent> events, String topic, int daysToAnalyze) {
        if (events.isEmpty()) {
            return CoverageTrend.builder()
                    .topic(topic)
                    .direction(CoverageTrend.TrendDirection.STABLE)
                    .changePercentage(0.0)
                    .currentMentions(0)
                    .previousPeriodMentions(0)
                    .interpretation("Aucune donnée disponible")
                    .dataPoints(new ArrayList<>())
                    .confidence("Faible")
                    .build();
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midPoint = now.minusDays(daysToAnalyze / 2);

        List<GdeltEvent> recentEvents = events.stream()
                .filter(e -> e.getEventDate().isAfter(midPoint))
                .collect(Collectors.toList());

        List<GdeltEvent> olderEvents = events.stream()
                .filter(e -> e.getEventDate().isBefore(midPoint))
                .collect(Collectors.toList());

        int currentMentions = calculateTotalMentions(recentEvents);
        int previousMentions = calculateTotalMentions(olderEvents);

        double changePercentage = calculateChangePercentage(currentMentions, previousMentions);
        CoverageTrend.TrendDirection direction = determineTrendDirection(changePercentage);
        List<TrendDataPoint> dataPoints = generateDataPoints(events, daysToAnalyze);

        return CoverageTrend.builder()
                .topic(topic)
                .direction(direction)
                .changePercentage(changePercentage)
                .currentMentions(currentMentions)
                .previousPeriodMentions(previousMentions)
                .interpretation(generateTrendInterpretation(direction, changePercentage))
                .dataPoints(dataPoints)
                .confidence(calculateTrendConfidence(events.size(), Math.abs(changePercentage)))
                .build();
    }

    /**
     * Compare la couverture médiatique entre plusieurs régions
     */
    public GeographicalCoverageComparison compareRegionalCoverage(String topic,
                                                                  Map<String, List<GdeltEvent>> regionalEvents) {

        List<RegionalCoverage> regionalCoverages = regionalEvents.entrySet().stream()
                .map(entry -> analyzeRegionalCoverage(entry.getKey(), entry.getValue()))
                .sorted((rc1, rc2) -> rc2.getCoverageIntensity().compareTo(rc1.getCoverageIntensity()))
                .collect(Collectors.toList());

        String dominantRegion = regionalCoverages.isEmpty() ? "Aucune" :
                regionalCoverages.get(0).getRegion();

        String leastCoveredRegion = regionalCoverages.isEmpty() ? "Aucune" :
                regionalCoverages.get(regionalCoverages.size() - 1).getRegion();

        Map<String, String> regionalPerspectives = generateRegionalPerspectives(regionalCoverages);

        return GeographicalCoverageComparison.builder()
                .topic(topic)
                .analysisType("Comparative")
                .regionalCoverages(regionalCoverages)
                .dominantRegion(dominantRegion)
                .leastCoveredRegion(leastCoveredRegion)
                .regionalPerspectives(regionalPerspectives)
                .globalInsight(generateGlobalInsight(regionalCoverages))
                .build();
    }

    // Méthodes utilitaires privées

    private Map<LocalDateTime, List<GdeltEvent>> groupEventsByDay(List<GdeltEvent> events) {
        return events.stream()
                .collect(Collectors.groupingBy(event ->
                        event.getEventDate().truncatedTo(ChronoUnit.DAYS)));
    }

    private Map<LocalDateTime, Integer> calculateDailyCounts(Map<LocalDateTime, List<GdeltEvent>> eventsByDay) {
        return eventsByDay.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .mapToInt(event -> event.getNumMentions() != null ? event.getNumMentions() : 1)
                                .sum()
                ));
    }

    private double calculateAverageCoverage(Map<LocalDateTime, Integer> dailyCounts) {
        return dailyCounts.values().stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);
    }

    private List<CoverageSpike> identifySpikes(Map<LocalDateTime, Integer> dailyCounts,
                                               Map<LocalDateTime, List<GdeltEvent>> eventsByDay,
                                               double averageCoverage) {

        double threshold = averageCoverage * SPIKE_THRESHOLD_MULTIPLIER;

        return dailyCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > threshold)
                .map(entry -> createCoverageSpike(entry.getKey(), entry.getValue(),
                        eventsByDay.get(entry.getKey()), averageCoverage))
                .sorted((s1, s2) -> s2.getIntensity().compareTo(s1.getIntensity()))
                .collect(Collectors.toList());
    }

    private CoverageSpike createCoverageSpike(LocalDateTime date, Integer mentionCount,
                                              List<GdeltEvent> dayEvents, double average) {
        double intensity = mentionCount / Math.max(average, 1.0);
        double deviation = (mentionCount - average) / average * 100;

        List<String> triggerEvents = dayEvents != null ?
                dayEvents.stream()
                        .limit(3)
                        .map(this::generateEventDescription)
                        .collect(Collectors.toList()) :
                new ArrayList<>();

        return CoverageSpike.builder()
                .date(date)
                .mentionCount(mentionCount)
                .intensity(intensity)
                .description(String.format("Pic de %d mentions (%.1fx la moyenne)", mentionCount, intensity))
                .triggerEvents(triggerEvents)
                .deviationFromAverage(deviation)
                .build();
    }

    private String generateEventDescription(GdeltEvent event) {
        return String.format("%s - %s",
                event.getActor1Name() != null ? event.getActor1Name() : "Acteur inconnu",
                event.getEventCode() != null ? mapEventCodeToDescription(event.getEventCode()) : "Événement");
    }

    private String mapEventCodeToDescription(String eventCode) {
        Map<String, String> codeDescriptions = Map.of(
                "01", "Coopération publique",
                "02", "Appel à la coopération",
                "14", "Manifestation de protestation",
                "18", "Conflit militaire",
                "19", "Conflit",
                "20", "Violence"
        );

        String prefix = eventCode.substring(0, Math.min(2, eventCode.length()));
        return codeDescriptions.getOrDefault(prefix, "Événement " + eventCode);
    }

    private String generateAnalysisPeriod(List<GdeltEvent> events) {
        if (events.isEmpty()) return "Période indéterminée";

        LocalDateTime earliest = events.stream()
                .map(GdeltEvent::getEventDate)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());

        LocalDateTime latest = events.stream()
                .map(GdeltEvent::getEventDate)
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());

        long days = ChronoUnit.DAYS.between(earliest, latest);
        return String.format("Du %s au %s (%d jours)",
                earliest.toLocalDate(), latest.toLocalDate(), days);
    }

    private String generateSpikeInterpretation(List<CoverageSpike> spikes, double average) {
        if (spikes.isEmpty()) {
            return "Aucun pic significatif détecté. Couverture médiatique relativement stable.";
        }

        CoverageSpike biggestSpike = spikes.get(0);
        String interpretation = String.format(
                "%d pic(s) détecté(s). Le plus important le %s avec %.1fx la moyenne normale.",
                spikes.size(),
                biggestSpike.getDate().toLocalDate(),
                biggestSpike.getIntensity()
        );

        if (spikes.size() > 3) {
            interpretation += " Sujet très volatile dans les médias.";
        } else if (spikes.size() == 1) {
            interpretation += " Événement ponctuel ayant généré un intérêt médiatique.";
        }

        return interpretation;
    }

    private int calculateTotalMentions(List<GdeltEvent> events) {
        return events.stream()
                .mapToInt(event -> event.getNumMentions() != null ? event.getNumMentions() : 1)
                .sum();
    }

    private double calculateChangePercentage(int current, int previous) {
        if (previous == 0) {
            return current > 0 ? 100.0 : 0.0;
        }
        return ((double) (current - previous) / previous) * 100.0;
    }

    private CoverageTrend.TrendDirection determineTrendDirection(double changePercentage) {
        double volatilityThreshold = 50.0; // Seuil de volatilité
        double significantChangeThreshold = 15.0; // Seuil de changement significatif

        if (Math.abs(changePercentage) > volatilityThreshold) {
            return CoverageTrend.TrendDirection.VOLATILE;
        } else if (changePercentage > significantChangeThreshold) {
            return CoverageTrend.TrendDirection.HAUSSE;
        } else if (changePercentage < -significantChangeThreshold) {
            return CoverageTrend.TrendDirection.BAISSE;
        } else {
            return CoverageTrend.TrendDirection.STABLE;
        }
    }

    private List<TrendDataPoint> generateDataPoints(List<GdeltEvent> events, int daysToAnalyze) {
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(daysToAnalyze);

        Map<LocalDateTime, List<GdeltEvent>> eventsByDay = events.stream()
                .filter(event -> event.getEventDate().isAfter(startDate))
                .collect(Collectors.groupingBy(event ->
                        event.getEventDate().truncatedTo(ChronoUnit.DAYS)));

        List<TrendDataPoint> dataPoints = new ArrayList<>();

        for (int i = 0; i < daysToAnalyze; i++) {
            LocalDateTime day = startDate.plusDays(i);
            List<GdeltEvent> dayEvents = eventsByDay.getOrDefault(day, new ArrayList<>());

            int mentions = dayEvents.stream()
                    .mapToInt(event -> event.getNumMentions() != null ? event.getNumMentions() : 1)
                    .sum();

            double sentiment = dayEvents.stream()
                    .filter(event -> event.getAvgTone() != null)
                    .mapToDouble(GdeltEvent::getAvgTone)
                    .average()
                    .orElse(0.0);

            String note = generateDayNote(dayEvents, mentions);

            dataPoints.add(TrendDataPoint.builder()
                    .date(day)
                    .mentions(mentions)
                    .sentiment(sentiment)
                    .note(note)
                    .build());
        }

        return dataPoints;
    }

    private String generateDayNote(List<GdeltEvent> events, int mentions) {
        if (events.isEmpty()) return "Pas d'activité";
        if (mentions > 100) return "Forte activité";
        if (mentions > 20) return "Activité modérée";
        return "Faible activité";
    }

    private String generateTrendInterpretation(CoverageTrend.TrendDirection direction, double changePercentage) {
        String baseInterpretation = switch (direction) {
            case HAUSSE -> String.format("Couverture en hausse de %.1f%%. Intérêt médiatique croissant.", changePercentage);
            case BAISSE -> String.format("Couverture en baisse de %.1f%%. Désintérêt médiatique apparent.", Math.abs(changePercentage));
            case VOLATILE -> String.format("Couverture très volatile (%.1f%% de variation). Sujet imprévisible.", changePercentage);
            case STABLE -> String.format("Couverture stable (%.1f%% de variation). Attention médiatique constante.", Math.abs(changePercentage));
        };

        // Ajout de contexte selon l'ampleur du changement
        if (Math.abs(changePercentage) > 100) {
            baseInterpretation += " Changement dramatique nécessitant une analyse approfondie.";
        } else if (Math.abs(changePercentage) > 50) {
            baseInterpretation += " Changement significatif à surveiller.";
        }

        return baseInterpretation;
    }

    private String calculateTrendConfidence(int eventCount, double changePercentage) {
        if (eventCount < 10) return "Très faible";
        if (eventCount < 30) return "Faible";
        if (eventCount < 100) return "Modérée";

        // Plus le changement est important, plus la confiance est élevée (si suffisamment d'événements)
        if (changePercentage > 20) return "Élevée";
        return "Modérée";
    }

    private RegionalCoverage analyzeRegionalCoverage(String region, List<GdeltEvent> events) {
        int eventCount = events.size();
        double coverageIntensity = calculateRegionalIntensity(events);
        SentimentAnalysis.SentimentType dominantSentiment = calculateDominantSentiment(events);
        List<String> topSources = extractTopSources(events);
        List<String> uniqueAngles = identifyUniqueAngles(events);
        String characteristics = generateCoverageCharacteristics(events);

        return RegionalCoverage.builder()
                .region(region)
                .eventCount(eventCount)
                .coverageIntensity(coverageIntensity)
                .dominantSentiment(dominantSentiment)
                .topSources(topSources)
                .uniqueAngles(uniqueAngles)
                .coverageCharacteristics(characteristics)
                .build();
    }

    private double calculateRegionalIntensity(List<GdeltEvent> events) {
        if (events.isEmpty()) return 0.0;

        return events.stream()
                .mapToDouble(event -> {
                    double mentions = event.getNumMentions() != null ? event.getNumMentions() : 1.0;
                    double goldstein = event.getGoldsteinScale() != null ? Math.abs(event.getGoldsteinScale()) : 1.0;
                    return Math.log(mentions + 1) * (goldstein + 1);
                })
                .average()
                .orElse(0.0);
    }

    private SentimentAnalysis.SentimentType calculateDominantSentiment(List<GdeltEvent> events) {
        double avgTone = events.stream()
                .filter(event -> event.getAvgTone() != null)
                .mapToDouble(GdeltEvent::getAvgTone)
                .average()
                .orElse(0.0);

        if (avgTone > 1.0) return SentimentAnalysis.SentimentType.POSITIF;
        if (avgTone < -1.0) return SentimentAnalysis.SentimentType.NEGATIF;
        return SentimentAnalysis.SentimentType.NEUTRE;
    }

    private List<String> extractTopSources(List<GdeltEvent> events) {
        return events.stream()
                .map(event -> extractDomainFromUrl(event.getSourceUrl()))
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(domain -> domain, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private String extractDomainFromUrl(String url) {
        if (url == null) return null;
        try {
            return new java.net.URL(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> identifyUniqueAngles(List<GdeltEvent> events) {
        Map<String, Long> actorFrequency = events.stream()
                .filter(event -> event.getActor1Name() != null)
                .collect(Collectors.groupingBy(GdeltEvent::getActor1Name, Collectors.counting()));

        // Identifier les acteurs uniques ou peu fréquents comme angles spéciaux
        return actorFrequency.entrySet().stream()
                .filter(entry -> entry.getValue() <= 3) // Acteurs mentionnés 3 fois ou moins
                .limit(3)
                .map(entry -> "Focus sur " + entry.getKey())
                .collect(Collectors.toList());
    }

    private String generateCoverageCharacteristics(List<GdeltEvent> events) {
        if (events.isEmpty()) return "Aucune couverture";

        boolean hasHighConflict = events.stream()
                .anyMatch(event -> event.getEventCode() != null &&
                        (event.getEventCode().startsWith("18") || event.getEventCode().startsWith("19")));

        boolean hasDiplomacy = events.stream()
                .anyMatch(event -> event.getEventCode() != null &&
                        (event.getEventCode().startsWith("05") || event.getEventCode().startsWith("06")));

        long uniqueActors = events.stream()
                .map(GdeltEvent::getActor1Name)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        StringBuilder characteristics = new StringBuilder();

        if (hasHighConflict) {
            characteristics.append("Couverture axée sur les conflits. ");
        }
        if (hasDiplomacy) {
            characteristics.append("Présence d'activité diplomatique. ");
        }

        characteristics.append(String.format("Diversité d'acteurs: %d différent(s).", uniqueActors));

        return characteristics.toString();
    }

    private Map<String, String> generateRegionalPerspectives(List<RegionalCoverage> coverages) {
        Map<String, String> perspectives = new HashMap<>();

        for (RegionalCoverage coverage : coverages) {
            String perspective = generateRegionalPerspective(coverage);
            perspectives.put(coverage.getRegion(), perspective);
        }

        return perspectives;
    }

    private String generateRegionalPerspective(RegionalCoverage coverage) {
        StringBuilder perspective = new StringBuilder();

        if (coverage.getCoverageIntensity() > 5.0) {
            perspective.append("Couverture intensive");
        } else if (coverage.getCoverageIntensity() > 2.0) {
            perspective.append("Couverture modérée");
        } else {
            perspective.append("Couverture limitée");
        }

        perspective.append(" avec un ton ");
        switch (coverage.getDominantSentiment()) {
            case POSITIF -> perspective.append("plutôt positif");
            case NEGATIF -> perspective.append("majoritairement négatif");
            case NEUTRE -> perspective.append("neutre");
            default -> perspective.append("mixte");
        }

        if (!coverage.getUniqueAngles().isEmpty()) {
            perspective.append(". ").append(coverage.getUniqueAngles().get(0));
        }

        return perspective.toString();
    }

    private String generateGlobalInsight(List<RegionalCoverage> coverages) {
        if (coverages.isEmpty()) {
            return "Aucune donnée régionale disponible pour l'analyse comparative.";
        }

        // Calculer les statistiques globales
        double avgIntensity = coverages.stream()
                .mapToDouble(RegionalCoverage::getCoverageIntensity)
                .average()
                .orElse(0.0);

        long positiveRegions = coverages.stream()
                .filter(c -> c.getDominantSentiment() == SentimentAnalysis.SentimentType.POSITIF)
                .count();

        long negativeRegions = coverages.stream()
                .filter(c -> c.getDominantSentiment() == SentimentAnalysis.SentimentType.NEGATIF)
                .count();

        StringBuilder insight = new StringBuilder();
        insight.append("Analyse comparative de ").append(coverages.size()).append(" région(s). ");

        if (positiveRegions > negativeRegions) {
            insight.append("Perception globalement positive du sujet. ");
        } else if (negativeRegions > positiveRegions) {
            insight.append("Perception globalement négative du sujet. ");
        } else {
            insight.append("Perception régionale partagée. ");
        }

        // Identifier les disparités
        double maxIntensity = coverages.stream()
                .mapToDouble(RegionalCoverage::getCoverageIntensity)
                .max()
                .orElse(0.0);

        double minIntensity = coverages.stream()
                .mapToDouble(RegionalCoverage::getCoverageIntensity)
                .min()
                .orElse(0.0);

        if (maxIntensity > minIntensity * 3) {
            insight.append("Disparités importantes entre les régions dans l'intensité de couverture.");
        } else {
            insight.append("Couverture relativement homogène entre les régions.");
        }

        return insight.toString();
    }
}
