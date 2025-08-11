package Tools.NewsAnalysisTool.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Filtre pour la recherche d'événements
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EventFilter {
    private List<String> keywords;
    private String country;
    private String region;
    private String theme;
    private String actor;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Double minGoldsteinScale;
    private Double maxGoldsteinScale;
    private Integer minMentions;
    @Builder.Default
    private Integer maxResults = 100;

    public static EventFilter defaultFilter() {
        return EventFilter.builder()
                .maxResults(100)
                .build();
    }
}
