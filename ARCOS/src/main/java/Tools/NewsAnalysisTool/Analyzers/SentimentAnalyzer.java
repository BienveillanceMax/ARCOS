package Tools.NewsAnalysisTool.Analyzers;


import Tools.NewsAnalysisTool.models.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyseur de sentiment pour les événements GDELT
 */
@Component
public class SentimentAnalyzer {

    public SentimentAnalysis analyzeSentiment(GdeltEvent event, List<String> articles) {
        Map<String, Double> emotionScores = calculateEmotionScores(event, articles);

        double positivity = emotionScores.getOrDefault("positive", 0.0);
        double negativity = emotionScores.getOrDefault("negative", 0.0);
        double neutrality = emotionScores.getOrDefault("neutral", 1.0);

        SentimentAnalysis.SentimentType overallSentiment = determineSentimentType(positivity, negativity, neutrality);
        double polarityScore = calculatePolarityScore(positivity, negativity);
        double confidenceLevel = calculateConfidenceLevel(emotionScores);

        return SentimentAnalysis.builder()
                .eventId(event.getGlobalEventId())
                .overallSentiment(overallSentiment)
                .polarityScore(polarityScore)
                .positivityScore(positivity)
                .negativityScore(negativity)
                .neutralityScore(neutrality)
                .confidenceLevel(confidenceLevel)
                .emotionScores(emotionScores)
                .sources(analyzeSentimentSources(articles))
                .build();
    }

    private Map<String, Double> calculateEmotionScores(GdeltEvent event, List<String> articles) {
        Map<String, Double> emotions = new HashMap<>();

        // Utilisation du tone GDELT comme base
        double gdeltTone = event.getAvgTone() != null ? event.getAvgTone() : 0.0;

        if (gdeltTone > 0) {
            emotions.put("positive", Math.min(gdeltTone / 10.0, 1.0));
            emotions.put("negative", 0.1);
            emotions.put("neutral", 0.5 - emotions.get("positive") / 2);
        } else if (gdeltTone < 0) {
            emotions.put("negative", Math.min(Math.abs(gdeltTone) / 10.0, 1.0));
            emotions.put("positive", 0.1);
            emotions.put("neutral", 0.5 - emotions.get("negative") / 2);
        } else {
            emotions.put("neutral", 0.8);
            emotions.put("positive", 0.1);
            emotions.put("negative", 0.1);
        }

        // Ajout d'émotions spécialisées basées sur le code d'événement
        if (event.getEventCode() != null) {
            addContextualEmotions(emotions, event.getEventCode());
        }

        return emotions;
    }

    private void addContextualEmotions(Map<String, Double> emotions, String eventCode) {
        // Mapping des codes d'événements CAMEO vers émotions
        if (eventCode.startsWith("01") || eventCode.startsWith("02")) { // Coopération
            emotions.put("hope", 0.6);
            emotions.put("joy", 0.4);
        } else if (eventCode.startsWith("18") || eventCode.startsWith("19")) { // Conflit
            emotions.put("anger", 0.7);
            emotions.put("fear", 0.5);
        } else if (eventCode.startsWith("14")) { // Protestation
            emotions.put("frustration", 0.6);
            emotions.put("determination", 0.5);
        }
    }

    private SentimentAnalysis.SentimentType determineSentimentType(double pos, double neg, double neu) {
        double threshold = 0.1;

        if (Math.abs(pos - neg) < threshold) return SentimentAnalysis.SentimentType.MIXTE;
        if (pos > neg && pos > neu) return SentimentAnalysis.SentimentType.POSITIF;
        if (neg > pos && neg > neu) return SentimentAnalysis.SentimentType.NEGATIF;
        return SentimentAnalysis.SentimentType.NEUTRE;
    }

    private double calculatePolarityScore(double positivity, double negativity) {
        return (positivity - negativity) / Math.max(positivity + negativity, 0.1);
    }

    private double calculateConfidenceLevel(Map<String, Double> emotions) {
        double maxEmotion = emotions.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        return Math.min(maxEmotion * 2, 1.0); // Confiance basée sur l'émotion dominante
    }

    private List<SentimentSource> analyzeSentimentSources(List<String> articles) {
        return articles.stream()
                .limit(5) // Limiter aux 5 premiers articles
                .map(this::analyzeSingleSource)
                .collect(Collectors.toList());
    }

    private SentimentSource analyzeSingleSource(String articleUrl) {
        // Analyse simplifiée basée sur le domaine
        String domain = extractDomain(articleUrl);
        SentimentAnalysis.SentimentType sentiment = inferSentimentFromDomain(domain);

        return SentimentSource.builder()
                .source(domain)
                .sentiment(sentiment)
                .score(Math.random() * 0.8 + 0.1) // Score simulé
                .excerpt("Article de " + domain)
                .build();
    }

    private String extractDomain(String url) {
        try {
            return new java.net.URL(url).getHost();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private SentimentAnalysis.SentimentType inferSentimentFromDomain(String domain) {
        // Règles simplifiées basées sur le type de source
        if (domain.contains("reuters") || domain.contains("ap") || domain.contains("bbc")) {
            return SentimentAnalysis.SentimentType.NEUTRE;
        }
        return SentimentAnalysis.SentimentType.MIXTE;
    }
}

