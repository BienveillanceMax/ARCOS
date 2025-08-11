package Tools.NewsAnalysisTool.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Tendance de couverture médiatique
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CoverageTrend {
    private String topic;
    private TrendDirection direction;
    private Double changePercentage;
    private Integer currentMentions;
    private Integer previousPeriodMentions;
    private String interpretation;
    private List<TrendDataPoint> dataPoints;
    private String confidence;

    public enum TrendDirection {
        HAUSSE, BAISSE, STABLE, VOLATILE
    }
}
