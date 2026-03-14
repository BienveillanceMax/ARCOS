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

    // Batch pipeline (Epic 2)
    private int idleThresholdMinutes = 30;
    private long batchCheckIntervalMs = 60000;
    private int chunkWindowSize = 3;
    private int chunkMaxChars = 8000;
    private String conversationQueuePath = "data/conversation-queue.json";
    private String memlistenerModel = "memlistener:q4_k_m";
    private int memlistenerMaxTokens = 512;
    private double memlistenerTemperature = 0.2;
    private long memlistenerTimeoutMs = 120000;
    private String memlistenerKeepAlive = "0";

    // DFS Navigator (Epic 3)
    private String crossEncoderModelPath = "models/finetuned-navigator-deep/model.onnx";
    private String crossEncoderTokenizerPath = "models/finetuned-navigator-deep/tokenizer.json";
    private int dfsTopNL1 = 3;
    private float dfsL2Threshold = 0.0f;
    private int dfsMaxLength = 128;
    private int dfsIntraOpThreads = 4;

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
