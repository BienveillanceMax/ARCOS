package org.arcos.UserModel.BatchPipeline;

import lombok.extern.slf4j.Slf4j;
import org.arcos.LLM.Local.ThinkingMode;
import org.arcos.UserModel.UserModelProperties;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class MemListenerClient {

    private final OllamaChatModel ollamaChatModel;
    private final UserModelProperties properties;

    public MemListenerClient(OllamaChatModel ollamaChatModel,
                             UserModelProperties properties) {
        this.ollamaChatModel = ollamaChatModel;
        this.properties = properties;
    }

    public String generate(String prompt) {
        try {
            long timeoutMs = properties.getMemlistenerTimeoutMs();

            OllamaOptions options = OllamaOptions.builder()
                    .model(properties.getMemlistenerModel())
                    .temperature(properties.getMemlistenerTemperature())
                    .numPredict(properties.getMemlistenerMaxTokens())
                    .keepAlive(properties.getMemlistenerKeepAlive())
                    .build();

            // Force no-think mode for Qwen3: structured extraction, no chain-of-thought
            String fullPrompt = ThinkingMode.NO_THINK.getPrefix() + prompt;
            Prompt aiPrompt = new Prompt(fullPrompt, options);

            String result = CompletableFuture.supplyAsync(() ->
                    ollamaChatModel.call(aiPrompt).getResult().getOutput().getText()
            ).orTimeout(timeoutMs, TimeUnit.MILLISECONDS).join();

            log.debug("MemListener response ({} chars)", result != null ? result.length() : 0);
            return result != null ? result : "";
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof TimeoutException) {
                log.warn("MemListener call timed out after {}ms", properties.getMemlistenerTimeoutMs());
            } else {
                log.error("MemListener call failed: {}", e.getMessage());
            }
            return "";
        }
    }
}
