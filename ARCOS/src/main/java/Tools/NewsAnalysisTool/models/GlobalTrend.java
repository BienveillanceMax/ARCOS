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
public class GlobalTrend {
    private String theme;
    private String description;
    private List<String> affectedRegions;
    private Double intensity;
    private String trajectory; // MONTANT, DESCENDANT, STABLE
    private List<String> keyIndicators;
    private String potentialImpact;
}
