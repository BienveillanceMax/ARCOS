package Tools.NewsAnalysisTool.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegionalCoverage {
    private String region;
    private Integer eventCount;
    private Double coverageIntensity;
    private SentimentAnalysis.SentimentType dominantSentiment;
    private List<String> topSources;
    private List<String> uniqueAngles;
    private String coverageCharacteristics;
}