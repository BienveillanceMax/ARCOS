package Tools.NewsAnalysisTool.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Bulletin géopolitique
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GeopoliticalBulletin {
    private LocalDateTime timestamp;
    private List<RegionalTrends> regionalTrends;
    private List<GlobalTrend> globalTrends;
    private List<RiskAssessment> riskAssessments;
    private String summary;
    private Map<String, Integer> hotspotRankings;
}
