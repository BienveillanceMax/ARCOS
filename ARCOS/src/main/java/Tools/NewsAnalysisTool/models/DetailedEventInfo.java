package Tools.NewsAnalysisTool.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Informations détaillées d'un événement
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DetailedEventInfo {
    private GdeltEvent event;
    private String summary;
    private List<Actor> actors;
    private LocationInfo location;
    private List<String> sources;
    private List<String> relatedArticles;
    private List<String> themes;
    private SentimentAnalysis sentimentAnalysis;
    private Integer mediaImpact;
}