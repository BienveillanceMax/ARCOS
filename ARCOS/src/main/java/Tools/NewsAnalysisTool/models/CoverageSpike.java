package Tools.NewsAnalysisTool.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CoverageSpike {
    private LocalDateTime date;
    private Integer mentionCount;
    private Double intensity;
    private String description;
    private List<String> triggerEvents;
    private Double deviationFromAverage;
}

