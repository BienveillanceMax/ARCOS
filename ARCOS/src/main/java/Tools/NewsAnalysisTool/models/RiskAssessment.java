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
public class RiskAssessment {
    private String region;
    private String riskType;
    private RiskLevel level;
    private String description;
    private List<String> indicators;
    private String timeline;
    private List<String> mitigationFactors;

    public enum RiskLevel {
        FAIBLE, MODERE, ELEVE, CRITIQUE
    }
}
