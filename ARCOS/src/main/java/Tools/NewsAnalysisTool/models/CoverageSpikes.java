package Tools.NewsAnalysisTool.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Pics de couverture médiatique détectés
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CoverageSpikes {
    private String topic;
    private List<CoverageSpike> spikes;
    private Double averageCoverage;
    private String analysisPeriod;
    private String interpretation;
}
