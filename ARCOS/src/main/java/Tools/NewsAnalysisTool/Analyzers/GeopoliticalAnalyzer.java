package Tools.NewsAnalysisTool.Analyzers;

import Tools.NewsAnalysisTool.models.*;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors; /**
 * Analyseur géopolitique
 */
@Component
public class GeopoliticalAnalyzer {

    private static final Map<String, List<String>> REGIONAL_COUNTRIES = Map.of(
            "Europe", Arrays.asList("FR", "DE", "IT", "ES", "UK", "PL", "NL", "BE", "GR", "PT"),
            "Asia", Arrays.asList("CN", "JP", "IN", "KR", "TH", "VN", "ID", "MY", "SG", "PH"),
            "Americas", Arrays.asList("US", "CA", "MX", "BR", "AR", "CO", "PE", "VE", "CL", "EC"),
            "Africa", Arrays.asList("EG", "ZA", "NG", "KE", "MA", "DZ", "GH", "ET", "UG", "TN"),
            "Middle East", Arrays.asList("SA", "IR", "TR", "IL", "AE", "IQ", "SY", "LB", "JO", "KW")
    );

    public RegionalTrends analyzeRegionalTrends(String region, List<GdeltEvent> events) {
        List<String> dominantThemes = extractRegionalThemes(events);
        Double stabilityIndex = calculateStabilityIndex(events);
        List<String> emergingIssues = identifyEmergingIssues(events);
        Double coverageVolume = calculateCoverageVolume(events);
        SentimentAnalysis.SentimentType overallSentiment = calculateRegionalSentiment(events);
        List<String> keyActors = identifyKeyActors(events);
        String trendDirection = determineTrendDirection(events);

        return RegionalTrends.builder()
                .region(region)
                .dominantThemes(dominantThemes)
                .stabilityIndex(stabilityIndex)
                .emergingIssues(emergingIssues)
                .mediaCoverageVolume(coverageVolume)
                .overallSentiment(overallSentiment)
                .keyActors(keyActors)
                .trendDirection(trendDirection)
                .build();
    }

    public List<GlobalTrend> identifyGlobalTrends(List<RegionalTrends> regionalTrends) {
        Map<String, List<RegionalTrends>> themeToRegions = new HashMap<>();

        // Grouper les régions par thèmes communs
        for (RegionalTrends regional : regionalTrends) {
            for (String theme : regional.getDominantThemes()) {
                themeToRegions.computeIfAbsent(theme, k -> new ArrayList<>()).add(regional);
            }
        }

        return themeToRegions.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= 2) // Thème présent dans au moins 2 régions
                .map(entry -> createGlobalTrend(entry.getKey(), entry.getValue()))
                .sorted((t1, t2) -> t2.getIntensity().compareTo(t1.getIntensity()))
                .limit(5)
                .collect(Collectors.toList());
    }

    public List<RiskAssessment> assessRisks(List<RegionalTrends> regionalTrends) {
        return regionalTrends.stream()
                .map(this::assessRegionalRisk)
                .filter(Objects::nonNull)
                .sorted((r1, r2) -> r2.getLevel().compareTo(r1.getLevel()))
                .collect(Collectors.toList());
    }

    public String generateBulletinSummary(List<GlobalTrend> globalTrends, List<RiskAssessment> risks) {
        StringBuilder summary = new StringBuilder();
        summary.append("Bulletin géopolitique - ").append(LocalDateTime.now().toLocalDate()).append("\n\n");

        if (!globalTrends.isEmpty()) {
            summary.append("Tendances mondiales majeures:\n");
            globalTrends.stream().limit(3).forEach(trend ->
                    summary.append("- ").append(trend.getTheme()).append(": ").append(trend.getDescription()).append("\n"));
            summary.append("\n");
        }

        if (!risks.isEmpty()) {
            long highRisks = risks.stream().filter(r ->
                    r.getLevel() == RiskAssessment.RiskLevel.ELEVE ||
                            r.getLevel() == RiskAssessment.RiskLevel.CRITIQUE).count();

            summary.append("Évaluation des risques: ").append(highRisks)
                    .append(" zone(s) à risque élevé identifiée(s).\n");

            risks.stream()
                    .filter(r -> r.getLevel() == RiskAssessment.RiskLevel.CRITIQUE)
                    .forEach(r -> summary.append("⚠️ CRITIQUE: ").append(r.getRegion())
                            .append(" - ").append(r.getDescription()).append("\n"));
        }

        return summary.toString();
    }

    private List<String> extractRegionalThemes(List<GdeltEvent> events) {
        Map<String, Long> themeCounts = events.stream()
                .filter(event -> event.getEventCode() != null)
                .collect(Collectors.groupingBy(
                        event -> mapEventCodeToTheme(event.getEventCode()),
                        Collectors.counting()
                ));

        return themeCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private Double calculateStabilityIndex(List<GdeltEvent> events) {
        if (events.isEmpty()) return 1.0;

        double avgGoldstein = events.stream()
                .filter(e -> e.getGoldsteinScale() != null)
                .mapToDouble(GdeltEvent::getGoldsteinScale)
                .average()
                .orElse(0.0);

        // Indice de stabilité: plus proche de 0, plus stable (échelle inversée)
        return Math.max(0.0, 1.0 - Math.abs(avgGoldstein) / 10.0);
    }

    private List<String> identifyEmergingIssues(List<GdeltEvent> events) {
        // Identifier les nouveaux types d'événements ou acteurs
        LocalDateTime recentThreshold = LocalDateTime.now().minusDays(7);

        Set<String> recentActors = events.stream()
                .filter(e -> e.getEventDate().isAfter(recentThreshold))
                .map(GdeltEvent::getActor1Name)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> historicalActors = events.stream()
                .filter(e -> e.getEventDate().isBefore(recentThreshold))
                .map(GdeltEvent::getActor1Name)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        recentActors.removeAll(historicalActors);

        return recentActors.stream()
                .limit(3)
                .map(actor -> "Nouvel acteur: " + actor)
                .collect(Collectors.toList());
    }

    private Double calculateCoverageVolume(List<GdeltEvent> events) {
        return events.stream()
                .mapToDouble(event -> event.getNumMentions() != null ? event.getNumMentions() : 1.0)
                .sum();
    }

    private SentimentAnalysis.SentimentType calculateRegionalSentiment(List<GdeltEvent> events) {
        double avgTone = events.stream()
                .filter(e -> e.getAvgTone() != null)
                .mapToDouble(GdeltEvent::getAvgTone)
                .average()
                .orElse(0.0);

        if (avgTone > 1.0) return SentimentAnalysis.SentimentType.POSITIF;
        if (avgTone < -1.0) return SentimentAnalysis.SentimentType.NEGATIF;
        return SentimentAnalysis.SentimentType.NEUTRE;
    }

    private List<String> identifyKeyActors(List<GdeltEvent> events) {
        return events.stream()
                .map(GdeltEvent::getActor1Name)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(name -> name, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private String determineTrendDirection(List<GdeltEvent> events) {
        if (events.size() < 10) return "Données insuffisantes";

        // Comparer les deux moitiés de la période
        int midPoint = events.size() / 2;
        List<GdeltEvent> firstHalf = events.subList(0, midPoint);
        List<GdeltEvent> secondHalf = events.subList(midPoint, events.size());

        double firstHalfIntensity = firstHalf.stream()
                .mapToDouble(e -> Math.abs(e.getGoldsteinScale() != null ? e.getGoldsteinScale() : 0))
                .average().orElse(0);

        double secondHalfIntensity = secondHalf.stream()
                .mapToDouble(e -> Math.abs(e.getGoldsteinScale() != null ? e.getGoldsteinScale() : 0))
                .average().orElse(0);

        double change = (secondHalfIntensity - firstHalfIntensity) / Math.max(firstHalfIntensity, 0.1);

        if (change > 0.15) return "Escalade";
        if (change < -0.15) return "Apaisement";
        return "Stable";
    }

    private GlobalTrend createGlobalTrend(String theme, List<RegionalTrends> regions) {
        double intensity = regions.stream()
                .mapToDouble(r -> 1.0 - r.getStabilityIndex())
                .average()
                .orElse(0.5);

        List<String> affectedRegions = regions.stream()
                .map(RegionalTrends::getRegion)
                .collect(Collectors.toList());

        String trajectory = determineGlobalTrajectory(regions, theme);

        return GlobalTrend.builder()
                .theme(theme)
                .description(generateTrendDescription(theme, regions.size()))
                .affectedRegions(affectedRegions)
                .intensity(intensity)
                .trajectory(trajectory)
                .keyIndicators(Arrays.asList("Couverture médiatique", "Activité diplomatique"))
                .potentialImpact(assessPotentialImpact(theme, intensity))
                .build();
    }

    private String determineGlobalTrajectory(List<RegionalTrends> regions, String theme) {
        long ascending = regions.stream()
                .filter(r -> "Escalade".equals(r.getTrendDirection()))
                .count();

        long descending = regions.stream()
                .filter(r -> "Apaisement".equals(r.getTrendDirection()))
                .count();

        if (ascending > descending) return "MONTANT";
        if (descending > ascending) return "DESCENDANT";
        return "STABLE";
    }

    private String generateTrendDescription(String theme, int regionCount) {
        return String.format("%s observé dans %d région(s), nécessite surveillance continue",
                theme, regionCount);
    }

    private String assessPotentialImpact(String theme, double intensity) {
        if (intensity > 0.7) return "Impact potentiel élevé sur la stabilité mondiale";
        if (intensity > 0.4) return "Impact modéré possible sur les relations internationales";
        return "Impact limité prévu";
    }

    private RiskAssessment assessRegionalRisk(RegionalTrends regional) {
        RiskAssessment.RiskLevel riskLevel = calculateRiskLevel(regional);
        if (riskLevel == RiskAssessment.RiskLevel.FAIBLE) return null;

        String riskType = identifyRiskType(regional);
        List<String> indicators = buildRiskIndicators(regional);

        return RiskAssessment.builder()
                .region(regional.getRegion())
                .riskType(riskType)
                .level(riskLevel)
                .description(generateRiskDescription(regional, riskType))
                .indicators(indicators)
                .timeline("Court terme (1-3 mois)")
                .mitigationFactors(identifyMitigationFactors(regional))
                .build();
    }

    private RiskAssessment.RiskLevel calculateRiskLevel(RegionalTrends regional) {
        double score = 0.0;

        // Facteurs de risque
        if (regional.getStabilityIndex() < 0.3) score += 0.4;
        if (regional.getOverallSentiment() == SentimentAnalysis.SentimentType.NEGATIF) score += 0.3;
        if ("Escalade".equals(regional.getTrendDirection())) score += 0.2;
        if (regional.getDominantThemes().contains("Conflit")) score += 0.3;

        if (score >= 0.8) return RiskAssessment.RiskLevel.CRITIQUE;
        if (score >= 0.6) return RiskAssessment.RiskLevel.ELEVE;
        if (score >= 0.3) return RiskAssessment.RiskLevel.MODERE;
        return RiskAssessment.RiskLevel.FAIBLE;
    }

    private String identifyRiskType(RegionalTrends regional) {
        if (regional.getDominantThemes().contains("Conflit")) return "Conflit armé";
        if (regional.getDominantThemes().contains("Protestation")) return "Instabilité sociale";
        if ("Escalade".equals(regional.getTrendDirection())) return "Escalade des tensions";
        return "Instabilité générale";
    }

    private List<String> buildRiskIndicators(RegionalTrends regional) {
        List<String> indicators = new ArrayList<>();

        if (regional.getStabilityIndex() < 0.5) {
            indicators.add("Indice de stabilité faible (" +
                    String.format("%.2f", regional.getStabilityIndex()) + ")");
        }

        if (regional.getOverallSentiment() == SentimentAnalysis.SentimentType.NEGATIF) {
            indicators.add("Sentiment média majoritairement négatif");
        }

        if (!regional.getEmergingIssues().isEmpty()) {
            indicators.add("Nouveaux acteurs détectés");
        }

        return indicators;
    }

    private String generateRiskDescription(RegionalTrends regional, String riskType) {
        return String.format("Risque de %s en %s. Surveillance recommandée des développements.",
                riskType.toLowerCase(), regional.getRegion());
    }

    private List<String> identifyMitigationFactors(RegionalTrends regional) {
        List<String> factors = new ArrayList<>();

        if (regional.getDominantThemes().contains("Diplomatie")) {
            factors.add("Activité diplomatique en cours");
        }

        if (regional.getStabilityIndex() > 0.3) {
            factors.add("Certains indicateurs de stabilité maintenus");
        }

        if (factors.isEmpty()) {
            factors.add("Surveillance internationale nécessaire");
        }

        return factors;
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
}
