package org.arcos.UserModel;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "arcos.user-model")
public class UserModelProperties {

    private boolean enabled = true;
    // Used by PersonaTreeMigrator to find old v1 tree
    private String storagePath = "data/user-tree.json";
    private String personaTreeSchemaPath = "persona-tree-schema.json";
    private String personaTreePath = "data/persona-tree.json";
    private long debounceSaveMs = 500;

    // Batch pipeline (Epic 2)
    private int sessionEndThresholdMinutes = 5;
    private int idleThresholdMinutes = 30;
    private long batchCheckIntervalMs = 60000;
    private int chunkWindowSize = 3;
    private int chunkMaxChars = 8000;
    private String conversationQueuePath = "data/conversation-queue.json";
    private String memlistenerModel = "pierrewagniart/memlistener:q4_k_m";
    private int memlistenerMaxTokens = 512;
    private double memlistenerTemperature = 0.2;
    private long memlistenerTimeoutMs = 120000;
    private String memlistenerKeepAlive = "0";

    private int leafMaxChars = 300;

    // DFS Navigator (Epic 3)
    private String crossEncoderModelPath = "models/finetuned-navigator-deep/model.onnx";
    private String crossEncoderTokenizerPath = "models/finetuned-navigator-deep/tokenizer.json";
    private int dfsTopNL1 = 3;
    private float dfsL2Threshold = 0.0f;
    private int dfsMaxLength = 128;
    private int dfsIntraOpThreads = 4;
}
