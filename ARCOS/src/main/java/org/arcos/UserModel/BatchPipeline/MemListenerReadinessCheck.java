package org.arcos.UserModel.BatchPipeline;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.arcos.UserModel.UserModelProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Checks whether the MemListener model is available in Ollama.
 * Polled periodically so the batch pipeline can gate on model readiness.
 */
@Slf4j
@Component
public class MemListenerReadinessCheck {

    private final String ollamaBaseUrl;
    private final String modelName;
    private final HttpClient httpClient;
    private final AtomicBoolean modelReady = new AtomicBoolean(false);

    public MemListenerReadinessCheck(
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
            UserModelProperties properties) {
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.modelName = properties.getMemlistenerModel();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @PostConstruct
    public void init() {
        checkModel();
        if (!modelReady.get()) {
            log.warn("MemListener model '{}' not found in Ollama at {}. "
                    + "Batch pipeline will be disabled until the model is provisioned. "
                    + "Run: docker compose up memlistener-init",
                    modelName, ollamaBaseUrl);
        }
    }

    @Scheduled(fixedRate = 60000)
    public void checkModel() {
        try {
            String body = "{\"name\": \"" + modelName + "\"}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaBaseUrl + "/api/show"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean found = response.statusCode() == 200;
            if (found && !modelReady.get()) {
                log.info("MemListener model '{}' is now available in Ollama", modelName);
            }
            modelReady.set(found);
        } catch (Exception e) {
            modelReady.set(false);
            log.trace("MemListener model check failed: {}", e.getMessage());
        }
    }

    public boolean isModelReady() {
        return modelReady.get();
    }
}
