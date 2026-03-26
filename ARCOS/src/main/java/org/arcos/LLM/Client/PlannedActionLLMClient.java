package org.arcos.LLM.Client;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.arcos.LLM.Client.ResponseObject.PlannedActionPlanResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class PlannedActionLLMClient
{
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public PlannedActionLLMClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = JsonMapper.builder()
                .enable(JsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS)
                .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
                .build();
    }

    @CircuitBreaker(name = "mistral_free", fallbackMethod = "generatePlannedActionPlanResponseFallback")
    @RateLimiter(name = "mistral_free")
    public PlannedActionPlanResponse generatePlannedActionPlanResponse(Prompt prompt) {
        return chatClient.prompt(prompt)
                .call()
                .entity(new BeanOutputConverter<>(PlannedActionPlanResponse.class, objectMapper));
    }

    @CircuitBreaker(name = "mistral_free", fallbackMethod = "generateToollessResponseFallback")
    @RateLimiter(name = "mistral_free")
    public String generateToollessResponse(Prompt prompt) {
        return chatClient.prompt(prompt)
                .call()
                .content();
    }

    private PlannedActionPlanResponse generatePlannedActionPlanResponseFallback(Prompt prompt, Throwable t) {
        log.error("Mistral indisponible (planned action plan): {}", t.getMessage());
        return null;
    }

    private String generateToollessResponseFallback(Prompt prompt, Throwable t) {
        log.error("Mistral indisponible (planned action toolless): {}", t.getMessage());
        return null;
    }
}
