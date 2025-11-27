package LLM;

import Exceptions.DesireCreationException;
import Exceptions.ResponseParsingException;
import LLM.service.RateLimiterService;
import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.Models.MemoryEntry;
import Memory.LongTermMemory.Models.OpinionEntry;
import Personality.Mood.ConversationResponse;
import Personality.Mood.MoodUpdate;
import Tools.Actions.CalendarActions;
import Tools.Actions.PythonActions;
import Tools.Actions.SearchActions;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

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
        return chatClient.prompt(prompt)
                .call()
                .entity(new BeanOutputConverter<>(MemoryEntry.class, this.objectMapper));
    }

    @RateLimiter(name = "mistral_free")
    public OpinionEntry generateOpinionResponse(Prompt prompt) {
        return chatClient.prompt(prompt)
                .tools(calendarActions, pythonActions, searchActions)
                .call()
                .entity(new BeanOutputConverter<>(OpinionEntry.class, this.objectMapper));
    }

    @RateLimiter(name = "mistral_free")
    public DesireEntry generateDesireResponse(Prompt prompt) throws DesireCreationException {
        return chatClient.prompt(prompt)
                .call()
                .entity(new BeanOutputConverter<>(DesireEntry.class, this.objectMapper));
    }


    @RateLimiter(name = "mistral_free")
    public ConversationResponse generateConversationResponse(Prompt prompt) {
        return chatClient.prompt(prompt)
                .tools(calendarActions, pythonActions, searchActions)
                .call()
                .entity(new BeanOutputConverter<>(ConversationResponse.class, this.objectMapper));
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