package Tools.NewsAnalysisTool.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Classes utilitaires pour les données temporelles et mentions
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TimelineDataPoint {
    private LocalDateTime timestamp;
    private Double value;
    private Integer volume;
}
