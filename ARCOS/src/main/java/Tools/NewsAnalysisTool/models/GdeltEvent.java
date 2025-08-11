package Tools.NewsAnalysisTool.models;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;


/**
 * Modèle représentant un événement GDELT
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GdeltEvent {
    private String globalEventId;
    private LocalDateTime eventDate;
    private String eventCode;
    private String eventBaseCode;
    private String eventRootCode;

    // Acteurs
    private String actor1Name;
    private String actor1Code;
    private String actor1CountryCode;
    private String actor1Type;
    private String actor2Name;
    private String actor2Code;
    private String actor2CountryCode;
    private String actor2Type;

    // Géolocalisation
    private String actionGeoCountryCode;
    private String actionGeoAdm1Code;
    private String actionGeoFullName;
    private Double actionGeoLat;
    private Double actionGeoLong;

    // Métriques
    private Double goldsteinScale;
    private Integer numMentions;
    private Integer numSources;
    private Integer numArticles;
    private Double avgTone;

    // URLs et sources
    private String sourceUrl;
    private List<String> relatedUrls;
}