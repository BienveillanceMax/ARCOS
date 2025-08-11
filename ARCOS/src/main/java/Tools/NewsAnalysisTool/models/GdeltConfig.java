package Tools.NewsAnalysisTool.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration du service GDELT
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GdeltConfig {
    private String apiBaseUrl;
    private String apiKey;
    private Integer defaultTimeout;
    private Integer maxRetries;
    private Integer requestDelay;
    private Boolean enableCaching;
    private String cacheDirectory;
}