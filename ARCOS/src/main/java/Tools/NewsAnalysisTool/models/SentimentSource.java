package Tools.NewsAnalysisTool.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SentimentSource
{
    private String source;
    private SentimentAnalysis.SentimentType sentiment;
    private Double score;
    private String excerpt;
}

