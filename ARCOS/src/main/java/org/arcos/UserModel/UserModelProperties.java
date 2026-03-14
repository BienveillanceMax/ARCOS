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
    private String personaTreeSchemaPath = "persona-tree-schema.json";
    private String personaTreePath = "data/persona-tree.json";
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

    private Consolidation consolidation = new Consolidation();
    private ProactiveGapFilling proactiveGapFilling = new ProactiveGapFilling();
    private Engagement engagement = new Engagement();

    @Data
    public static class ProactiveGapFilling {
        private boolean enabled = true;
        private int maxPerSession = 1;
        private int minConversationsBetweenSameBranch = 3;
    }

    @Data
    public static class Engagement {
        private boolean enabled = true;
        private int decayWindowConversations = 5;
        private double decayRatioThreshold = 0.5;
        private int minConversationsForTracking = 5;
    }

    @Data
    public static class Consolidation {
        private boolean enabled = false;
        private String model = "qwen3.5:4b";
        private long timeoutMs = 90000;
        private String batchCron = "0 0 4 * * *";
        private int maxLeavesPerRun = 50;
        private int maxParseErrors = 5;
        private int maxTimeoutsPerRun = 3;
        private double decisionConfidenceThreshold = 0.6;
        private int snapshotRetentionCount = 5;
        private int snapshotRetentionDays = 14;
        private boolean enablePatternDetection = false;
        private double patternSimilarityThreshold = 0.75;
        private int patternMinLeaves = 3;
        private int patternMinDays = 3;
        private int patternMaxPerRun = 5;
        private int minLeavesForPercentageCheck = 20;
        private int maxAbsoluteRemovalWhenBelowFloor = 3;
    }
}
