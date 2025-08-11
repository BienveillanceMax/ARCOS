package Tools.NewsAnalysisTool.models;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Résultat d'analyse de sentiment
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SentimentAnalysis {
    private String eventId;
    private SentimentType overallSentiment;
    private Double polarityScore;
    private Double positivityScore;
    private Double negativityScore;
    private Double neutralityScore;
    private Double confidenceLevel;
    private Map<String, Double> emotionScores;
    private List<SentimentSource> sources;

    public enum SentimentType {
        POSITIF, NEGATIF, NEUTRE, MIXTE
    }
}