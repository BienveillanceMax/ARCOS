package org.arcos.Tools.GdeltTool;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "arcos.gdelt")
public class GdeltProperties {
    private boolean enabled = true;
    private int timeoutSeconds = 60;
    private String defaultTimespan = "1week";
    private int maxArticles = 10;
    private int rateLimitMs = 5500;
    private String baseUrl = "http://api.gdeltproject.org/api/v2/doc/doc";
    private int maxBriefingQueries = 3;
    private String defaultSort = "HybridRel";
}
