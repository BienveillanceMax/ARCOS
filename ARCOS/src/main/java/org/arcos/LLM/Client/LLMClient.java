package org.arcos.LLM.Client;

import org.arcos.Exceptions.DesireCreationException;
import org.arcos.LLM.Client.ResponseObject.DesireResponse;
import org.arcos.LLM.Client.ResponseObject.MemoryResponse;
import org.arcos.UserModel.Models.MemoryAndObservationsResponse;
import org.arcos.LLM.Client.ResponseObject.OpinionResponse;
import org.arcos.LLM.Client.ResponseObject.PlannedActionPlanResponse;
import org.arcos.Memory.LongTermMemory.Models.DesireEntry;
import org.arcos.Memory.LongTermMemory.Models.MemoryEntry;
import org.arcos.Memory.LongTermMemory.Models.OpinionEntry;
import org.arcos.Personality.Mood.MoodUpdate;
import org.arcos.Tools.Actions.PythonActions;
import org.arcos.Tools.Actions.SearchActions;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LLMClient
{

    private final BeanOutputConverter<MoodUpdate> converter;
    private final ChatClient chatClient;
    private final PythonActions pythonActions;
    private final SearchActions searchActions;

    private final ObjectMapper objectMapper;

    public LLMClient(ChatClient.Builder chatClientBuilder,
                     PythonActions pythonActions,
                     SearchActions searchActions) {
        this.chatClient = chatClientBuilder.build();
        this.pythonActions = pythonActions;
        this.searchActions = searchActions;

        this.objectMapper = JsonMapper.builder()
                .enable(JsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS)
                .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
                .build();
        converter = new BeanOutputConverter<>(MoodUpdate.class, this.objectMapper);
    }

    @RateLimiter(name = "mistral_free")
    public PlannedActionPlanResponse generatePlannedActionPlanResponse(Prompt prompt) {
        return chatClient.prompt(prompt)
                .call()
                .entity(new BeanOutputConverter<>(PlannedActionPlanResponse.class, objectMapper));
    }

    @RateLimiter(name = "mistral_free")
    public String generateToollessResponse(Prompt prompt) {
        return chatClient.prompt(prompt)
                .call()
                .content();
    }

    @RateLimiter(name = "mistral_free")
    public MemoryEntry generateMemoryResponse(Prompt prompt) {
        MemoryResponse response = chatClient.prompt(prompt)
                .call()
                .entity(new BeanOutputConverter<>(MemoryResponse.class, this.objectMapper));
        if (response == null || response.getContent() == null || response.getContent().isBlank()) {
            log.warn("LLM returned null or empty memory response");
            return null;
        }
        return MemoryEntry.fromMemoryResponse(response);
    }

    @RateLimiter(name = "mistral_free")
    public MemoryAndObservationsResponse generateMemoryAndObservationsResponse(Prompt prompt) {
        MemoryAndObservationsResponse response = chatClient.prompt(prompt)
                .call()
                .entity(new BeanOutputConverter<>(MemoryAndObservationsResponse.class, this.objectMapper));
        if (response == null || response.getContent() == null || response.getContent().isBlank()) {
            log.warn("LLM returned null or empty memory+observations response");
            return null;
        }
        return response;
    }

    @RateLimiter(name = "mistral_free")
    public OpinionEntry generateOpinionResponse(Prompt prompt) {
        OpinionResponse response = chatClient.prompt(prompt)
                .tools(pythonActions, searchActions)
                .call()
                .entity(new BeanOutputConverter<>(OpinionResponse.class, this.objectMapper));
        if (response == null || response.getSummary() == null || response.getSummary().isBlank()) {
            log.warn("LLM returned null or empty opinion response");
            return null;
        }
        return OpinionEntry.fromOpinionResponse(response);
    }

    @RateLimiter(name = "mistral_free")
    public DesireEntry generateDesireResponse(Prompt prompt) throws DesireCreationException {
        DesireResponse response = chatClient.prompt(prompt)
                .call()
                .entity(new BeanOutputConverter<>(DesireResponse.class, this.objectMapper));
        if (response == null || response.getLabel() == null || response.getLabel().isBlank()) {
            log.warn("LLM returned null or empty desire response");
            return null;
        }
        return DesireEntry.fromDesireResponse(response);
    }

    @RateLimiter(name = "mistral_free")
    public MoodUpdate generateMoodUpdateResponse(Prompt prompt) {
        return chatClient.prompt(prompt)
                .call()
                .entity(converter);
    }
}