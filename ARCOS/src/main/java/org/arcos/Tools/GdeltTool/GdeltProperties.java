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
}
