package org.arcos.LLM.Local;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.arcos.UserModel.UserModelProperties;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@ConditionalOnProperty(name = "arcos.local-llm.enabled", havingValue = "true")
public class LocalLlmService {

    private final OllamaChatModel ollamaChatModel;
    private final UserModelProperties properties;
    private final LocalLlmHealthIndicator healthIndicator;
    private final ExecutorService executor;
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    public LocalLlmService(OllamaChatModel ollamaChatModel,
                           UserModelProperties properties,
                           LocalLlmHealthIndicator healthIndicator) {
        this.ollamaChatModel = ollamaChatModel;
        this.properties = properties;
        this.healthIndicator = healthIndicator;

        ThreadFactory daemonFactory = r -> {
            Thread t = new Thread(r, "arcos-local-llm");
            t.setDaemon(true);
            return t;
        };
        this.executor = Executors.newSingleThreadExecutor(daemonFactory);
    }

    @CircuitBreaker(name = "localLlm", fallbackMethod = "generateFallback")
    public CompletableFuture<String> generateSimpleAsync(String prompt) {
        return doGenerate(ThinkingMode.NO_THINK.getPrefix() + prompt);
    }

    @CircuitBreaker(name = "localLlm", fallbackMethod = "generateFallback")
    public CompletableFuture<String> generateComplexAsync(String prompt) {
        return doGenerate(ThinkingMode.THINK.getPrefix() + prompt);
    }

    private CompletableFuture<String> doGenerate(String fullPrompt) {
        if (!isProcessing.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(
                    new RejectedExecutionException("Local LLM is already processing a request"));
        }

        long timeoutMs = properties.getConsolidation().getTimeoutMs();
        String model = properties.getConsolidation().getModel();

        return CompletableFuture.supplyAsync(() -> {
            try {
                OllamaOptions options = OllamaOptions.builder()
                        .model(model)
                        .build();
                Prompt aiPrompt = new Prompt(fullPrompt, options);
                String result = ollamaChatModel.call(aiPrompt).getResult().getOutput().getText();
                log.debug("Local LLM response ({} chars)", result != null ? result.length() : 0);
                return result != null ? result : "";
            } finally {
                isProcessing.set(false);
            }
        }, executor).orTimeout(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public boolean isProcessing() {
        return isProcessing.get();
    }

    public boolean isAvailable() {
        return healthIndicator.isOllamaUp() && !isProcessing.get();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Local LLM executor did not terminate in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("unused")
    private CompletableFuture<String> generateFallback(String prompt, Throwable t) {
        log.warn("Local LLM circuit breaker fallback triggered: {}", t.getMessage());
        return CompletableFuture.completedFuture("");
    }
}
