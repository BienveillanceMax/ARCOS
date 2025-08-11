package Tools.NewsAnalysisTool.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TrendDataPoint {
    private LocalDateTime date;
    private Integer mentions;
    private Double sentiment;
    private String note;
}

