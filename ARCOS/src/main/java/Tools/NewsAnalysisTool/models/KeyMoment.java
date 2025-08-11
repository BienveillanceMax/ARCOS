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
public class KeyMoment {
    private LocalDateTime date;
    private String description;
    private Double significance;
    private List<String> relatedEvents;
    private String impact;
}
