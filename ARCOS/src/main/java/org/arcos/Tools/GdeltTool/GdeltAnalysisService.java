package org.arcos.Tools.GdeltTool;

import lombok.extern.slf4j.Slf4j;
import org.arcos.Tools.GdeltTool.GdeltDocClient.*;
import org.arcos.UserModel.GdeltThemeIndex.GdeltKeyword;
import org.arcos.UserModel.GdeltThemeIndex.GdeltThemeIndexGate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@ConditionalOnProperty(name = "arcos.gdelt.enabled", havingValue = "true", matchIfMissing = true)
public class GdeltAnalysisService {

    private static final String LANG_FILTER = " (sourcelang:french OR sourcelang:english)";
    private static final int MAX_COUNTRY_TONE_COMPARISONS = 2;

    private final GdeltThemeIndexGate themeIndexGate;
    private final GdeltDocClient docClient;
    private final GdeltProperties properties;

    public GdeltAnalysisService(GdeltThemeIndexGate themeIndexGate,
                                GdeltDocClient docClient,
                                GdeltProperties properties) {
        this.themeIndexGate = themeIndexGate;
        this.docClient = docClient;
        this.properties = properties;
    }

    /**
     * Mode briefing : utilise le UserModel pour determiner les sujets pertinents.
     */
    public String generateBriefing() {
        List<GdeltKeyword> keywords = themeIndexGate.getAllKeywords();

        if (keywords.isEmpty()) {
            log.info("Briefing requested but no GDELT keywords indexed yet");
            return "Aucun centre d'interet indexe pour le briefing. "
                 + "Le profil utilisateur ne contient pas encore assez d'informations "
                 + "sur les centres d'interet, croyances ou engagements.";
        }

        log.info("Briefing requested — {} GDELT keywords available from {} indexed leaves",
                keywords.size(), themeIndexGate.getIndexedLeafCount());

        List<String> queries = buildBriefingQueries(keywords);
        StringBuilder report = new StringBuilder();
        report.append("=== BRIEFING ACTUALITES PERSONNALISE ===\n\n");

        int totalArticles = 0;
        for (String query : queries) {
            ArtlistResponse response = docClient.fetchArticles(
                    query, "24hours", properties.getMaxArticles(), properties.getDefaultSort());

            if (!response.articles().isEmpty()) {
                report.append("--- Theme : ").append(query).append(" ---\n");
                report.append(formatArticlesGroupedByCountry(response.articles()));
                report.append("\n");
                totalArticles += response.articles().size();
            }
        }

        String globalQuery = queries.getFirst();
        TimelineResponse toneTimeline = docClient.fetchTimeline(globalQuery, "timelinetone", "1week");
        report.append("--- Tendance tonale (7 jours) ---\n");
        report.append(analyzeToneTrend(toneTimeline));
        report.append("\n\n");

        report.append(totalArticles).append(" articles analyses sur ")
              .append(queries.size()).append(" requetes.");

        return report.toString();
    }

    /**
     * Mode analyse : analyse approfondie d'un sujet specifique.
     * 4-6 API calls: artlist, volraw, global tone, geo, + up to 2 country tones.
     */
    public String analyzeSubject(String subject) {
        log.info("Deep analysis requested for subject: {}", subject);

        String quotedSubject = quoteIfMultiWord(subject);
        StringBuilder report = new StringBuilder();
        report.append("=== ANALYSE : ").append(subject).append(" ===\n\n");

        // 1. Articles in French/English, grouped by country
        String articleQuery = quotedSubject + LANG_FILTER;
        ArtlistResponse articles = docClient.fetchArticles(
                articleQuery, properties.getDefaultTimespan(), properties.getMaxArticles(), properties.getDefaultSort());

        report.append("--- Articles cles (sources francophones et anglophones) ---\n");
        if (articles.articles().isEmpty()) {
            report.append("Aucun article trouve pour ce sujet.\n");
        } else {
            report.append(formatArticlesGroupedByCountry(articles.articles()));
        }
        report.append("\n");

        // 2. Volume trend with absolute context
        TimelineResponse volTimeline = docClient.fetchTimeline(quotedSubject, "timelinevolraw", properties.getDefaultTimespan());
        report.append("--- Attention mediatique ---\n");
        report.append(analyzeVolumeTrend(volTimeline));
        report.append("\n\n");

        // 3. Global sentiment trajectory
        TimelineResponse toneTimeline = docClient.fetchTimeline(quotedSubject, "timelinetone", "3months");
        report.append("--- Sentiment des medias (3 mois) ---\n");
        report.append(analyzeToneTrend(toneTimeline));
        report.append("\n\n");

        // 4. Geographic coverage → identify top countries
        TimelineResponse geoTimeline = docClient.fetchTimeline(quotedSubject, "timelinesourcecountry", properties.getDefaultTimespan());
        List<String> topCountries = extractTopCountries(geoTimeline);

        report.append("--- Couverture geographique ---\n");
        report.append(analyzeGeoCoverage(geoTimeline));
        report.append("\n\n");

        // 5. Narrative divergence: compare tone across top 2 countries
        if (topCountries.size() >= 2) {
            report.append("--- Comparaison des perspectives nationales ---\n");
            report.append(analyzeNarrativeDivergence(quotedSubject, topCountries));
            report.append("\n");
        }

        return report.toString();
    }

    // --- Narrative divergence ---

    private String analyzeNarrativeDivergence(String quotedSubject, List<String> topCountries) {
        List<String> countriesToCompare = topCountries.subList(0,
                Math.min(MAX_COUNTRY_TONE_COMPARISONS, topCountries.size()));

        Map<String, Double> countryTones = new LinkedHashMap<>();
        for (String country : countriesToCompare) {
            String countryQuery = quotedSubject + " sourcecountry:" + country.toLowerCase();
            TimelineResponse countryTone = docClient.fetchTimeline(countryQuery, "timelinetone", properties.getDefaultTimespan());
            double avgTone = extractAverageTone(countryTone);
            if (!Double.isNaN(avgTone)) {
                countryTones.put(country, avgTone);
            }
        }

        if (countryTones.size() < 2) {
            return "Donnees insuffisantes pour comparer les perspectives nationales.";
        }

        StringBuilder sb = new StringBuilder();
        List<Map.Entry<String, Double>> entries = new ArrayList<>(countryTones.entrySet());

        for (Map.Entry<String, Double> entry : entries) {
            sb.append(String.format("- Medias de %s : traitement %s\n",
                    entry.getKey(), describeTone(entry.getValue())));
        }

        // Detect divergence
        double maxTone = entries.stream().mapToDouble(Map.Entry::getValue).max().orElse(0);
        double minTone = entries.stream().mapToDouble(Map.Entry::getValue).min().orElse(0);
        double spread = maxTone - minTone;

        String mostPositive = entries.stream().max(Comparator.comparingDouble(Map.Entry::getValue)).get().getKey();
        String mostNegative = entries.stream().min(Comparator.comparingDouble(Map.Entry::getValue)).get().getKey();

        if (spread > 3.0) {
            sb.append(String.format("DIVERGENCE NARRATIVE FORTE : les medias de %s traitent ce sujet de facon nettement plus positive " +
                    "que ceux de %s. Cet ecart suggere des cadres d'interpretation tres differents.", mostPositive, mostNegative));
        } else if (spread > 1.5) {
            sb.append(String.format("Divergence moderee : %s couvre le sujet plus positivement que %s.", mostPositive, mostNegative));
        } else {
            sb.append("Consensus relatif entre les pays compares — perspectives similaires.");
        }

        return sb.toString();
    }

    private double extractAverageTone(TimelineResponse timeline) {
        if (timeline.timeline().isEmpty()) return Double.NaN;
        TimelineSeries series = timeline.timeline().getFirst();
        if (series.data().isEmpty()) return Double.NaN;
        return series.data().stream().mapToDouble(TimelineDataPoint::value).average().orElse(Double.NaN);
    }

    private List<String> extractTopCountries(TimelineResponse geoTimeline) {
        if (geoTimeline.timeline().isEmpty()) return List.of();

        return geoTimeline.timeline().stream()
                .collect(Collectors.toMap(
                        s -> extractCountryName(s.series()),
                        s -> s.data().stream().mapToDouble(TimelineDataPoint::value).sum(),
                        Double::sum
                ))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();
    }

    // --- Article formatting ---

    private String formatArticlesGroupedByCountry(List<GdeltArticle> articles) {
        // Group by country, preserving order of first appearance
        Map<String, List<GdeltArticle>> byCountry = new LinkedHashMap<>();
        for (GdeltArticle a : articles) {
            String country = (a.sourcecountry() != null && !a.sourcecountry().isBlank())
                    ? a.sourcecountry() : "Autre";
            byCountry.computeIfAbsent(country, k -> new ArrayList<>()).add(a);
        }

        StringBuilder sb = new StringBuilder();
        if (byCountry.size() <= 1) {
            // Single country — flat list, no grouping noise
            int i = 1;
            for (GdeltArticle a : articles) {
                sb.append(formatSingleArticle(i++, a));
            }
        } else {
            // Multiple countries — group by perspective
            int i = 1;
            for (Map.Entry<String, List<GdeltArticle>> entry : byCountry.entrySet()) {
                sb.append("[").append(entry.getKey()).append("]\n");
                for (GdeltArticle a : entry.getValue()) {
                    sb.append(formatSingleArticle(i++, a));
                }
            }
            sb.append(summarizeSourceDiversity(articles));
        }
        return sb.toString();
    }

    private String formatSingleArticle(int index, GdeltArticle a) {
        StringBuilder sb = new StringBuilder();
        sb.append(index).append(". ").append(a.title())
          .append(" — ").append(a.domain());
        if (a.language() != null && !a.language().isBlank()) {
            sb.append(" [").append(a.language()).append("]");
        }
        sb.append(" — ").append(formatDate(a.seendate())).append("\n");
        return sb.toString();
    }

    private String summarizeSourceDiversity(List<GdeltArticle> articles) {
        Map<String, Long> byCountry = articles.stream()
                .filter(a -> a.sourcecountry() != null && !a.sourcecountry().isBlank())
                .collect(Collectors.groupingBy(GdeltArticle::sourcecountry, Collectors.counting()));

        Map<String, Long> byLang = articles.stream()
                .filter(a -> a.language() != null && !a.language().isBlank())
                .collect(Collectors.groupingBy(GdeltArticle::language, Collectors.counting()));

        return "Sources : " + articles.size() + " articles de " + byCountry.size() + " pays en "
                + byLang.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .map(e -> e.getValue() + " " + e.getKey().toLowerCase())
                    .collect(Collectors.joining(", "))
                + ".\n";
    }

    // --- Volume analysis ---

    String analyzeVolumeTrend(TimelineResponse timeline) {
        if (timeline.timeline().isEmpty()) {
            return "Donnees de volume indisponibles.";
        }

        TimelineSeries series = timeline.timeline().getFirst();
        List<TimelineDataPoint> data = series.data();
        if (data.isEmpty()) {
            return "Donnees de volume indisponibles.";
        }

        int mid = data.size() / 2;
        if (mid == 0) {
            return String.format("%.0f articles recenses sur la periode.", data.getFirst().value());
        }

        // Total article count for absolute context
        double totalArticles = data.stream().mapToDouble(TimelineDataPoint::value).sum();

        double earlierAvg = data.subList(0, mid).stream()
                .mapToDouble(TimelineDataPoint::value).average().orElse(0);
        double recentAvg = data.subList(mid, data.size()).stream()
                .mapToDouble(TimelineDataPoint::value).average().orElse(0);

        double changePercent = earlierAvg > 0 ? ((recentAvg - earlierAvg) / earlierAvg) * 100 : 0;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%.0f articles recenses sur la periode. ", totalArticles));

        if (changePercent > 30) {
            sb.append(String.format("SUJET EN FORTE HAUSSE : la couverture a augmente de %.0f%% recemment.", changePercent));
        } else if (changePercent > 10) {
            sb.append(String.format("Sujet en hausse : +%.0f%% de couverture recemment.", changePercent));
        } else if (changePercent < -30) {
            sb.append(String.format("Sujet en perte d'attention : la couverture a baisse de %.0f%%.", Math.abs(changePercent)));
        } else if (changePercent < -10) {
            sb.append(String.format("Sujet en baisse : -%.0f%% de couverture recemment.", Math.abs(changePercent)));
        } else {
            sb.append("Couverture mediatique stable.");
        }

        // Peak detection
        double max = data.stream().mapToDouble(TimelineDataPoint::value).max().orElse(0);
        double avg = data.stream().mapToDouble(TimelineDataPoint::value).average().orElse(0);
        if (max > avg * 2.5 && avg > 0) {
            TimelineDataPoint peak = data.stream()
                    .max(Comparator.comparingDouble(TimelineDataPoint::value)).orElse(null);
            if (peak != null) {
                sb.append(String.format(" Pic d'attention le %s (%.0f articles, %.1fx la moyenne).",
                        formatDate(peak.date()), peak.value(), max / avg));
            }
        }

        return sb.toString();
    }

    // --- Tone analysis ---

    String analyzeToneTrend(TimelineResponse timeline) {
        if (timeline.timeline().isEmpty()) {
            return "Donnees de sentiment indisponibles.";
        }

        TimelineSeries series = timeline.timeline().getFirst();
        List<TimelineDataPoint> data = series.data();

        if (data.isEmpty()) {
            return "Donnees de sentiment indisponibles.";
        }

        double avg = data.stream().mapToDouble(TimelineDataPoint::value).average().orElse(0);

        int mid = data.size() / 2;
        if (mid == 0) {
            return String.format("Le traitement mediatique est %s.", describeTone(avg));
        }

        double firstHalfAvg = data.subList(0, mid).stream()
                .mapToDouble(TimelineDataPoint::value).average().orElse(0);
        double secondHalfAvg = data.subList(mid, data.size()).stream()
                .mapToDouble(TimelineDataPoint::value).average().orElse(0);

        double delta = secondHalfAvg - firstHalfAvg;

        StringBuilder sb = new StringBuilder();
        // Plain-language summary that Mistral can directly reformulate
        sb.append(String.format("Le traitement mediatique global est %s. ", describeTone(avg)));

        if (delta > 1.0) {
            sb.append(String.format("La couverture est devenue plus positive recemment " +
                    "(passant de %s a %s).", describeTone(firstHalfAvg), describeTone(secondHalfAvg)));
        } else if (delta < -1.0) {
            sb.append(String.format("La couverture est devenue plus negative recemment " +
                    "(passant de %s a %s).", describeTone(firstHalfAvg), describeTone(secondHalfAvg)));
        } else {
            sb.append("Le sentiment est reste stable sur la periode.");
        }

        // Notable events — only report if genuinely dramatic
        double max = data.stream().mapToDouble(TimelineDataPoint::value).max().orElse(0);
        double min = data.stream().mapToDouble(TimelineDataPoint::value).min().orElse(0);
        if (max - min > 3.0) {
            TimelineDataPoint peakPos = data.stream()
                    .max(Comparator.comparingDouble(TimelineDataPoint::value)).orElse(null);
            TimelineDataPoint peakNeg = data.stream()
                    .min(Comparator.comparingDouble(TimelineDataPoint::value)).orElse(null);
            sb.append(String.format(" Moment le plus positif le %s, moment le plus critique le %s.",
                    formatDate(peakPos.date()), formatDate(peakNeg.date())));
        }

        return sb.toString();
    }

    // --- Geo analysis ---

    String analyzeGeoCoverage(TimelineResponse timeline) {
        if (timeline.timeline().isEmpty()) {
            return "Donnees de couverture geographique indisponibles.";
        }

        Map<String, Double> countryVolumes = timeline.timeline().stream()
                .collect(Collectors.toMap(
                        s -> extractCountryName(s.series()),
                        s -> s.data().stream().mapToDouble(TimelineDataPoint::value).sum(),
                        Double::sum
                ));

        if (countryVolumes.isEmpty()) {
            return "Donnees de couverture geographique indisponibles.";
        }

        double totalVolume = countryVolumes.values().stream().mapToDouble(Double::doubleValue).sum();

        List<Map.Entry<String, Double>> top = countryVolumes.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(5)
                .toList();

        StringBuilder sb = new StringBuilder();

        // Only mention total country count if it's notably low (niche topic) or high
        if (countryVolumes.size() <= 5) {
            sb.append("Sujet de niche : seulement ").append(countryVolumes.size()).append(" pays couvrent ce sujet. ");
        } else if (countryVolumes.size() >= 30) {
            sb.append("Sujet d'interet mondial : ").append(countryVolumes.size()).append(" pays couvrent ce sujet. ");
        }

        sb.append("Pays les plus actifs : ");
        sb.append(top.stream()
                .map(e -> {
                    double pct = totalVolume > 0 ? (e.getValue() / totalVolume) * 100 : 0;
                    return e.getKey() + String.format(" (%.0f%%)", pct);
                })
                .collect(Collectors.joining(", ")));
        sb.append(".");

        if (top.size() >= 2) {
            double topPct = totalVolume > 0 ? (top.getFirst().getValue() / totalVolume) * 100 : 0;

            if (topPct > 60) {
                sb.append(" La couverture est dominee par ").append(top.getFirst().getKey())
                  .append(" — les autres pays en parlent peu.");
            } else if (topPct < 25 && countryVolumes.size() > 10) {
                sb.append(" La couverture est bien repartie — aucun pays ne domine le recit.");
            }
        }

        return sb.toString();
    }

    // --- Briefing query building ---

    List<String> buildBriefingQueries(List<GdeltKeyword> keywords) {
        List<String> gdeltThemes = new ArrayList<>();
        List<String> frenchTerms = new ArrayList<>();
        List<String> englishTerms = new ArrayList<>();

        for (GdeltKeyword kw : keywords) {
            switch (kw.language()) {
                case GDELT_THEME -> gdeltThemes.add("theme:" + kw.term());
                case FR -> frenchTerms.add(quoteIfMultiWord(kw.term()));
                case EN -> englishTerms.add(quoteIfMultiWord(kw.term()));
            }
        }

        List<String> allTerms = new ArrayList<>();
        allTerms.addAll(frenchTerms);
        allTerms.addAll(gdeltThemes);
        allTerms.addAll(englishTerms);

        if (allTerms.isEmpty()) {
            return List.of();
        }

        int maxQueries = properties.getMaxBriefingQueries();
        List<String> queries = new ArrayList<>();
        int groupSize = Math.max(1, (int) Math.ceil((double) allTerms.size() / maxQueries));

        for (int i = 0; i < allTerms.size(); i += groupSize) {
            int end = Math.min(i + groupSize, allTerms.size());
            List<String> group = allTerms.subList(i, end);
            String combined = "(" + String.join(" OR ", group) + ") sourcelang:french";
            queries.add(combined);
            if (queries.size() >= maxQueries) break;
        }

        return queries;
    }

    // --- Helpers ---

    private String quoteIfMultiWord(String term) {
        if (term.contains(" ") && !term.startsWith("\"")) {
            return "\"" + term + "\"";
        }
        return term;
    }

    private String extractCountryName(String seriesLabel) {
        return seriesLabel.replace(" Volume Intensity", "").trim();
    }

    private String describeTone(double tone) {
        if (tone > 2.0) return "plutot positif et optimiste";
        if (tone > 0.5) return "legerement positif";
        if (tone > -0.5) return "neutre et factuel";
        if (tone > -2.0) return "legerement negatif";
        if (tone > -5.0) return "negatif et critique";
        return "tres negatif et alarmiste";
    }

    String formatDate(String seendate) {
        if (seendate == null || seendate.length() < 8) return "date inconnue";
        try {
            DateTimeFormatter input = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
            DateTimeFormatter output = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            LocalDateTime dt = LocalDateTime.parse(seendate, input);
            return dt.format(output);
        } catch (Exception e) {
            return seendate;
        }
    }
}
