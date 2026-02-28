package org.arcos.LLM.Client;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.arcos.LLM.Client.ResponseObject.PlannedActionPlanResponse;
import org.arcos.Personality.Mood.MoodUpdate;
import org.arcos.Tools.Actions.CalendarActions;
import org.arcos.Tools.Actions.PlannedActionActions;
import org.arcos.Tools.Actions.PythonActions;
import org.arcos.Tools.Actions.SearchActions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;


@Component
public class PlannedActionLLMClient
{
    private final BeanOutputConverter<MoodUpdate> converter;
    private final ChatClient chatClient;
    private final CalendarActions calendarActions;
    private final PythonActions pythonActions;
    private final SearchActions searchActions;
    private final PlannedActionActions plannedActionActions;


    private final ObjectMapper objectMapper;

    public PlannedActionLLMClient(ChatClient.Builder chatClientBuilder, CalendarActions calendarActions, PythonActions pythonActions, SearchActions searchActions, PlannedActionActions plannedActionActions) {
        this.chatClient = chatClientBuilder.build();
        this.calendarActions = calendarActions;
        this.pythonActions = pythonActions;
        this.searchActions = searchActions;
        this.plannedActionActions = plannedActionActions;

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
}
