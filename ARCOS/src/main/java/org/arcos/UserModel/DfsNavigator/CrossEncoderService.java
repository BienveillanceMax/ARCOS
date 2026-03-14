package org.arcos.UserModel.DfsNavigator;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.arcos.UserModel.UserModelProperties;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CrossEncoderService {

    private OrtEnvironment env;
    private OrtSession session;
    private HuggingFaceTokenizer tokenizer;
    private final UserModelProperties properties;
    private boolean available = false;

    public CrossEncoderService(UserModelProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initialize() {
        Path modelPath = Path.of(properties.getCrossEncoderModelPath());
        Path tokenizerPath = Path.of(properties.getCrossEncoderTokenizerPath());

        if (!Files.exists(modelPath)) {
            log.warn("Cross-encoder ONNX model not found at {}. DFS Navigator will be disabled.", modelPath);
            return;
        }
        if (!Files.exists(tokenizerPath)) {
            log.warn("Cross-encoder tokenizer not found at {}. DFS Navigator will be disabled.", tokenizerPath);
            return;
        }

        try {
            env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setIntraOpNumThreads(properties.getDfsIntraOpThreads());
            session = env.createSession(modelPath.toString(), opts);

            tokenizer = HuggingFaceTokenizer.newInstance(tokenizerPath);

            available = true;
            log.info("CrossEncoderService initialized: model={}, tokenizer={}", modelPath, tokenizerPath);
        } catch (Exception e) {
            log.warn("Failed to initialize CrossEncoderService. DFS Navigator will be disabled.", e);
            available = false;
        }
    }

    @PreDestroy
    void shutdown() {
        try {
            if (session != null) session.close();
        } catch (OrtException e) {
            log.warn("Error closing ONNX session", e);
        }
        if (tokenizer != null) tokenizer.close();
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Score a query against multiple descriptions using the cross-encoder.
     * @param query the user query
     * @param descriptions list of branch descriptions to score against
     * @return array of scores (one per description), or empty array if not available
     */
    public float[] score(String query, List<String> descriptions) {
        if (!available || descriptions.isEmpty()) {
            return new float[0];
        }

        int batchSize = descriptions.size();
        int maxLen = properties.getDfsMaxLength();

        try {
            long[][] inputIds = new long[batchSize][maxLen];
            long[][] attentionMask = new long[batchSize][maxLen];

            for (int i = 0; i < batchSize; i++) {
                Encoding encoding = tokenizer.encode(query, descriptions.get(i));
                long[] ids = encoding.getIds();
                long[] mask = encoding.getAttentionMask();

                int len = Math.min(ids.length, maxLen);
                System.arraycopy(ids, 0, inputIds[i], 0, len);
                System.arraycopy(mask, 0, attentionMask[i], 0, len);
                // Remaining positions are already 0 (padding)
            }

            OnnxTensor inputIdsTensor = OnnxTensor.createTensor(env, inputIds);
            OnnxTensor maskTensor = OnnxTensor.createTensor(env, attentionMask);

            try (var result = session.run(Map.of("input_ids", inputIdsTensor, "attention_mask", maskTensor))) {
                float[][] logits = (float[][]) result.get(0).getValue();
                float[] scores = new float[batchSize];
                for (int i = 0; i < batchSize; i++) {
                    scores[i] = logits[i][0];
                }
                return scores;
            } finally {
                inputIdsTensor.close();
                maskTensor.close();
            }
        } catch (OrtException e) {
            log.error("ONNX inference failed", e);
            return new float[0];
        }
    }
}
