package Tools.NewsAnalysisTool.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GeographicalCoverageComparison
{
    private String topic;
    private String analysisType;
    private List<RegionalCoverage> regionalCoverages;
    private String dominantRegion;
    private String leastCoveredRegion;
    private Map<String, String> regionalPerspectives;
    private String globalInsight;
}
