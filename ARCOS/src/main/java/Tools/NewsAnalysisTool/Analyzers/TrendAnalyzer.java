package Tools.NewsAnalysisTool.Analyzers;

import Tools.NewsAnalysisTool.models.GdeltEvent;
import Tools.NewsAnalysisTool.models.TrendPeriod;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors; /**
 * Analyseur de tendances temporelles
 */
@Component
public class TrendAnalyzer {

    public List<TrendPeriod> analyzeTrends(List<GdeltEvent> events) {
        if (events.isEmpty()) return new ArrayList<>();

        // Grouper les événements par périodes
        Map<String, List<GdeltEvent>> eventsByPeriod = groupEventsByWeek(events);

        return eventsByPeriod.entrySet().stream()
                .map(entry -> analyzePeriod(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(TrendPeriod::getStartDate))
                .collect(Collectors.toList());
    }

    private Map<String, List<GdeltEvent>> groupEventsByWeek(List<GdeltEvent> events) {
        return events.stream()
                .collect(Collectors.groupingBy(event -> getWeekKey(event.getEventDate())));
    }

    private String getWeekKey(LocalDateTime date) {
        return date.getYear() + "-W" + date.getDayOfYear() / 7;
    }

    private TrendPeriod analyzePeriod(String periodKey, List<GdeltEvent> events) {
        LocalDateTime minDate = events.stream()
                .map(GdeltEvent::getEventDate)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());

        LocalDateTime maxDate = events.stream()
                .map(GdeltEvent::getEventDate)
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());

        double intensity = calculateIntensity(events);
        String trendType = determineTrendType(events);
        List<String> themes = extractDominantThemes(events);

        return TrendPeriod.builder()
                .startDate(minDate)
                .endDate(maxDate)
                .trendType(trendType)
                .intensity(intensity)
                .description(generatePeriodDescription(events, trendType))
                .dominantThemes(themes)
                .build();
    }

    private double calculateIntensity(List<GdeltEvent> events) {
        return events.stream()
                .mapToDouble(event -> {
                    double goldstein = event.getGoldsteinScale() != null ? Math.abs(event.getGoldsteinScale()) : 1.0;
                    int mentions = event.getNumMentions() != null ? event.getNumMentions() : 1;
                    return goldstein * Math.log(mentions + 1);
                })
                .average()
                .orElse(1.0);
    }

    private String determineTrendType(List<GdeltEvent> events) {
        if (events.size() < 3) return "STABLE";

        List<Double> intensities = events.stream()
                .sorted(Comparator.comparing(GdeltEvent::getEventDate))
                .map(event -> event.getGoldsteinScale() != null ? Math.abs(event.getGoldsteinScale()) : 1.0)
                .collect(Collectors.toList());

        double firstThird = intensities.subList(0, intensities.size() / 3).stream()
                .mapToDouble(Double::doubleValue).average().orElse(0);
        double lastThird = intensities.subList(2 * intensities.size() / 3, intensities.size()).stream()
                .mapToDouble(Double::doubleValue).average().orElse(0);

        double change = (lastThird - firstThird) / Math.max(firstThird, 0.1);

        if (change > 0.2) return "CROISSANT";
        if (change < -0.2) return "DECROISSANT";

        // Vérifier la volatilité
        double variance = calculateVariance(intensities);
        if (variance > 2.0) return "VOLATILE";

        return "STABLE";
    }

    private double calculateVariance(List<Double> values) {
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0);
    }

    private List<String> extractDominantThemes(List<GdeltEvent> events) {
        Map<String, AtomicInteger> themeCount = new HashMap<>();

        events.forEach(event -> {
            if (event.getEventCode() != null) {
                String theme = mapEventCodeToTheme(event.getEventCode());
                themeCount.computeIfAbsent(theme, k -> new AtomicInteger(0)).incrementAndGet();
            }
        });

        return themeCount.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().get() - e1.getValue().get())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private String mapEventCodeToTheme(String eventCode) {
        if (eventCode.startsWith("01") || eventCode.startsWith("02")) return "Coopération";
        if (eventCode.startsWith("03") || eventCode.startsWith("04")) return "Consultation";
        if (eventCode.startsWith("05") || eventCode.startsWith("06")) return "Diplomatie";
        if (eventCode.startsWith("14")) return "Protestation";
        if (eventCode.startsWith("18") || eventCode.startsWith("19")) return "Conflit";
        if (eventCode.startsWith("20")) return "Violence";
        return "Autre";
    }

    private String generatePeriodDescription(List<GdeltEvent> events, String trendType) {
        int eventCount = events.size();
        String dominantActor = events.stream()
                .map(GdeltEvent::getActor1Name)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(name -> name, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("acteur inconnu");

        return String.format("Période %s avec %d événements, acteur principal: %s",
                trendType.toLowerCase(), eventCount, dominantActor);
    }
}
