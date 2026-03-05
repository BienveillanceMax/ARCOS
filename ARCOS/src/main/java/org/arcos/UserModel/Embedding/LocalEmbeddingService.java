package org.arcos.UserModel.Embedding;

import lombok.extern.slf4j.Slf4j;
import org.arcos.UserModel.UserModelProperties;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class LocalEmbeddingService {

    private final UserModelProperties properties;
    private TransformersEmbeddingModel embeddingModel;
    private final AtomicBoolean ready = new AtomicBoolean(false);

    public LocalEmbeddingService(UserModelProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Loading local embedding model: {}", properties.getEmbeddingModelName());
                TransformersEmbeddingModel model = new TransformersEmbeddingModel();
                model.setModelResource("https://huggingface.co/sentence-transformers/" + properties.getEmbeddingModelName());
                model.setTokenizerResource("https://huggingface.co/sentence-transformers/" + properties.getEmbeddingModelName());
                model.afterPropertiesSet();
                log.info("Local embedding model loaded successfully");
                return model;
            } catch (Exception e) {
                log.error("Failed to load local embedding model: {}", e.getMessage());
                return null;
            }
        }).thenAccept(model -> {
            if (model != null) {
                this.embeddingModel = model;
                ready.set(true);
            }
        });
    }

    public float[] embed(String text) {
        if (!ready.get() || embeddingModel == null) {
            return null;
        }
        try {
            float[] result = embeddingModel.embed(text);
            return result;
        } catch (Exception e) {
            log.warn("Embedding failed for text: {}", e.getMessage());
            return null;
        }
    }

    public double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0.0;
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        if (denominator == 0.0) {
            return 0.0;
        }
        return dotProduct / denominator;
    }

    public boolean isReady() {
        return ready.get();
    }
}
