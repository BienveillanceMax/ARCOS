package Tools.NewsAnalysisTool.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Narrative de l'historique médiatique
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MediaHistoryNarrative {
    private String topic;
    private String period;
    private List<TrendPeriod> trendPeriods;
    private List<KeyMoment> keyMoments;
    private String overallEvolution;
    private String summary;
    private Map<String, Object> statistics;
}
