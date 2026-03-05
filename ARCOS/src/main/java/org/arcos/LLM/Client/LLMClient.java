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
import org.arcos.Memory.LongTermMemory.Repositories.MemoryRepository;
import org.arcos.Personality.Mood.MoodUpdate;
import org.arcos.Tools.Actions.CalendarActions;
import org.arcos.Tools.Actions.MemoryActions;
import org.arcos.Tools.Actions.PlannedActionActions;
import org.arcos.Tools.Actions.PythonActions;
import org.arcos.Tools.Actions.SearchActions;
import org.arcos.Tools.Actions.WeatherActions;
import org.arcos.Tools.Actions.WebPageActions;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Slf4j
@Component
public class LLMClient
{

    private static final String MEMORY_ADVISOR_PROMPT_TEMPLATE = """
            {query}

            ## Souvenirs pertinents
            {question_answer_context}
            """;

    private final BeanOutputConverter<MoodUpdate> converter;
    private final ChatClient chatClient;
    private final CalendarActions calendarActions;
    private final PythonActions pythonActions;
    private final SearchActions searchActions;
    private final PlannedActionActions plannedActionActions;
    private final MemoryActions memoryActions;
    private final WebPageActions webPageActions;
    private final WeatherActions weatherActions;
    private final QuestionAnswerAdvisor questionAnswerAdvisor;

    private final ObjectMapper objectMapper;

    public LLMClient(ChatClient.Builder chatClientBuilder,
                     CalendarActions calendarActions,
                     PythonActions pythonActions,
                     SearchActions searchActions,
                     PlannedActionActions plannedActionActions,
                     MemoryActions memoryActions,
                     WebPageActions webPageActions,
                     WeatherActions weatherActions,
                     MemoryRepository memoryRepository,
                     @Value("${arcos.memory.advisor.top-k:3}") int memoryAdvisorTopK) {
        this.chatClient = chatClientBuilder.build();
        this.calendarActions = calendarActions;
        this.pythonActions = pythonActions;
        this.searchActions = searchActions;
        this.plannedActionActions = plannedActionActions;
        this.memoryActions = memoryActions;
        this.webPageActions = webPageActions;
        this.weatherActions = weatherActions;

        this.questionAnswerAdvisor = QuestionAnswerAdvisor.builder(memoryRepository.getVectorStore())
                .searchRequest(SearchRequest.builder().topK(memoryAdvisorTopK).build())
                .promptTemplate(new PromptTemplate(MEMORY_ADVISOR_PROMPT_TEMPLATE))
                .build();

        this.objectMapper = JsonMapper.builder()
                .enable(JsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS)
                .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
                .build();
        converter = new BeanOutputConverter<>(MoodUpdate.class, this.objectMapper);

       /*
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        ChatResponse firstDebugCall = chatClient.prompt("Dis 'e'").call().chatResponse(); // Useful to get the model used
        log.info(firstDebugCall.getMetadata().getModel());
       */
    }

    @RateLimiter(name = "mistral_free")
    public PlannedActionPlanResponse generatePlannedActionPlanResponse(Prompt prompt) {
        return chatClient.prompt(prompt)
                .call()
                .entity(new BeanOutputConverter<>(PlannedActionPlanResponse.class, objectMapper));
    }

    @RateLimiter(name = "mistral_free")
    public String generateChatResponse(Prompt prompt) {
        return chatClient.prompt(prompt)
                .advisors(questionAnswerAdvisor)
                .tools(calendarActions, pythonActions, searchActions, plannedActionActions, memoryActions, webPageActions, weatherActions)
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
    public Flux<String> generateStreamingChatResponse(Prompt prompt) {
        return chatClient.prompt(prompt)
                .advisors(questionAnswerAdvisor)
                .tools(calendarActions, pythonActions, searchActions, plannedActionActions, memoryActions, webPageActions, weatherActions)
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