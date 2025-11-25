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
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class LLMClient {

    private final ChatClient chatClient;
    private final CalendarActions calendarActions;
    private final PythonActions pythonActions;
    private final SearchActions searchActions;



    public LLMClient(ChatClient.Builder chatClientBuilder, CalendarActions calendarActions, PythonActions pythonActions, SearchActions searchActions) {
        this.chatClient = chatClientBuilder.build();
        this.calendarActions = calendarActions;
        this.pythonActions = pythonActions;
        this.searchActions = searchActions;
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
                .entity(MemoryEntry.class);
    }

    @RateLimiter(name = "mistral_free")
    public OpinionEntry generateOpinionResponse(Prompt prompt) {
        return chatClient.prompt(prompt)
                .tools(calendarActions, pythonActions, searchActions)
                .call()
                .entity(OpinionEntry.class);
    }

    @RateLimiter(name = "mistral_free")
    public DesireEntry generateDesireResponse(Prompt prompt) throws DesireCreationException {
        return chatClient.prompt(prompt)
                .call()
                .entity(DesireEntry.class);
    }


    @RateLimiter(name = "mistral_free")
    public Flux<String> generateConversationResponseStream(Prompt prompt) {
        return chatClient.prompt(prompt)
                .tools(calendarActions, pythonActions, searchActions)
                .stream()
                .content();
    }
}
