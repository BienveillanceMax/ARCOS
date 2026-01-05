package org.arcos.LLM.Client;

import org.arcos.Exceptions.DesireCreationException;
import org.arcos.Exceptions.ResponseParsingException;
import org.arcos.LLM.Client.ResponseObject.DesireResponse;
import org.arcos.LLM.Client.ResponseObject.MemoryResponse;
import org.arcos.LLM.Client.ResponseObject.OpinionResponse;
import org.arcos.Memory.LongTermMemory.Models.DesireEntry;
import org.arcos.Memory.LongTermMemory.Models.MemoryEntry;
import org.arcos.Memory.LongTermMemory.Models.OpinionEntry;
import org.arcos.Personality.Mood.MoodUpdate;
import org.arcos.Tools.Actions.CalendarActions;
import org.arcos.Tools.Actions.PythonActions;
import org.arcos.Tools.Actions.SearchActions;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
public class LLMClient
{

    private final BeanOutputConverter<MoodUpdate> converter;
    private final ChatClient chatClient;
    private final CalendarActions calendarActions;
    private final PythonActions pythonActions;
    private final SearchActions searchActions;


    private final ObjectMapper objectMapper;

    public LLMClient(ChatClient.Builder chatClientBuilder, CalendarActions calendarActions, PythonActions pythonActions, SearchActions searchActions) {
        this.chatClient = chatClientBuilder.build();
        this.calendarActions = calendarActions;
        this.pythonActions = pythonActions;
        this.searchActions = searchActions;

        this.objectMapper = JsonMapper.builder()
                .enable(JsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS)
                .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
                .build();
        converter = new BeanOutputConverter<>(MoodUpdate.class, this.objectMapper);

       /* try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        ChatResponse firstDebugCall = chatClient.prompt("Dis 'e'").call().chatResponse(); // Useful to get the model used
        log.info(firstDebugCall.getMetadata().getModel());*/
    }


    @RateLimiter(name = "mistral_free")
    public String generateChatResponse(Prompt prompt) {
        return chatClient.prompt(prompt)
                .tools(calendarActions, pythonActions, searchActions)
                .call()
                .content();
    }

    @RateLimiter(name = "mistral_free")
    public String generateToollessResponse(Prompt prompt) {
        return chatClient.prompt(prompt)
                .call()
                .content();
    }

    @RateLimiter(name = "mistral_free")
    public MemoryEntry generateMemoryResponse(Prompt prompt) throws ResponseParsingException {
        try {
            MemoryResponse response = chatClient.prompt(prompt)
                    .call()
                    .entity(new BeanOutputConverter<>(MemoryResponse.class, this.objectMapper));
            if (response == null) {
                return null;
            }
            return MemoryEntry.fromMemoryResponse(response);
        } catch (Exception e) {
            throw new ResponseParsingException("LLM error", e);
        }
    }

    @RateLimiter(name = "mistral_free")
    public OpinionEntry generateOpinionResponse(Prompt prompt) {
        OpinionResponse response = chatClient.prompt(prompt)
                .tools(calendarActions, pythonActions, searchActions)
                .call()
                .entity(new BeanOutputConverter<>(OpinionResponse.class, this.objectMapper));
        if (response == null) {
            return null;
        }
        return OpinionEntry.fromOpinionResponse(response);
    }

    @RateLimiter(name = "mistral_free")
    public DesireEntry generateDesireResponse(Prompt prompt) throws DesireCreationException {
        DesireResponse response = chatClient.prompt(prompt)
                .call()
                .entity(new BeanOutputConverter<>(DesireResponse.class, this.objectMapper));
        if (response == null) {
            return null;
        }
        return DesireEntry.fromDesireResponse(response);
    }

    @RateLimiter(name = "mistral_free")
    public Flux<String> generateStreamingChatResponse(Prompt prompt) {
        return chatClient.prompt(prompt)
                .tools(calendarActions, pythonActions, searchActions)
                .stream()
                .content();
    }

    @RateLimiter(name = "mistral_free")
    public MoodUpdate generateMoodUpdateResponse(Prompt prompt) {
        return chatClient.prompt(prompt)
                .call()
                .entity(converter);
    }
}