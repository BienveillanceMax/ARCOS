package org.arcos.UserModel;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "arcos.user-model")
public class UserModelProperties {

    private boolean enabled = true;
    private String storagePath = "data/user-tree.json";
    private String archivePath = "data/user-tree-archive.json";
    private int maxActiveObservations = 300;
    private int minConversationsBeforeInjection = 3;
    private String embeddingModelName = "all-MiniLM-L6-v2";
    private String pruningCron = "0 0 3 * * *";
    private double retrievalMinSrank = 0.3;
    private int identityBudgetTokens = 25;
    private int communicationBudgetTokens = 30;
    private int totalBudgetTokens = 80;
    private double emaAlphaColdStart = 0.3;
    private double emaAlphaStable = 0.1;
    private double significanceThreshold = 0.20;
    private int significanceConsecutiveSessions = 3;
    private long debounceSaveMs = 500;
    private List<String> disfluenceWords = List.of(
            "euh", "heu", "bah", "ben", "hein", "quoi",
            "genre", "voilà", "enfin", "donc"
    );
}
