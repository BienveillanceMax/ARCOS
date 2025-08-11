package Tools.NewsAnalysisTool.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Acteur dans un événement
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Actor
{
    private String name;
    private String code;
    private String type;
    private String countryCode;
    private String role;
    private String organization;
    private Double relevanceScore;
}
