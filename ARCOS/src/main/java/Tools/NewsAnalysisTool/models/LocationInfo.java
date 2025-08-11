package Tools.NewsAnalysisTool.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Informations de localisation
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LocationInfo {
    private String country;
    private String region;
    private String city;
    private Double latitude;
    private Double longitude;
    private String fullName;
    private String featureClass;
}