package org.arcos.LLM.Local;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@ConditionalOnProperty(name = "arcos.local-llm.enabled", havingValue = "true")
public class LocalLlmHealthIndicator implements HealthIndicator {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final AtomicBoolean ollamaUp = new AtomicBoolean(false);

    public LocalLlmHealthIndicator(
            @org.springframework.beans.factory.annotation.Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Scheduled(fixedRate = 30000)
    public void pollOllamaHealth() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean up = response.statusCode() == 200;
            ollamaUp.set(up);
            log.trace("Ollama health check: {}", up ? "UP" : "DOWN");
        } catch (Exception e) {
            ollamaUp.set(false);
            log.trace("Ollama health check failed: {}", e.getMessage());
        }
    }

    @Override
    public Health health() {
        if (ollamaUp.get()) {
            return Health.up().withDetail("ollama", baseUrl).build();
        }
        return Health.down().withDetail("ollama", baseUrl).build();
    }

    public boolean isOllamaUp() {
        return ollamaUp.get();
    }
}
