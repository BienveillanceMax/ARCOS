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
public class RegionalTrends {
    private String region;
    private List<String> dominantThemes;
    private Double stabilityIndex;
    private List<String> emergingIssues;
    private Double mediaCoverageVolume;
    private SentimentAnalysis.SentimentType overallSentiment;
    private List<String> keyActors;
    private String trendDirection;
}
