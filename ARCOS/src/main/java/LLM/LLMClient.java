package LLM;

import LLM.service.RateLimiterService;
import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.Models.MemoryEntry;
import Memory.LongTermMemory.Models.OpinionEntry;
import Tools.Actions.CalendarActions;
import Tools.Actions.PythonActions;
import Tools.Actions.SearchActions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

@Service
public class LLMClient
{

    private final ChatClient chatClient;
    private final RateLimiterService rateLimiterService;
    private final CalendarActions calendarActions;
    private final PythonActions pythonActions;
    private final SearchActions searchActions;


    public LLMClient(ChatClient.Builder chatClientBuilder, RateLimiterService rateLimiterService, CalendarActions calendarActions, PythonActions pythonActions, SearchActions searchActions) {
        this.chatClient = chatClientBuilder.build();
        this.rateLimiterService = rateLimiterService;
        this.calendarActions = calendarActions;
        this.pythonActions = pythonActions;
        this.searchActions = searchActions;
    }

    private void acquirePermit() {
        rateLimiterService.acquirePermit();
    }

    public String generateChatResponse(Prompt prompt) {
        acquirePermit();
        return chatClient.prompt(prompt)
                .tools(calendarActions, pythonActions, searchActions)
                .call()
                .content();
    }

    public String generateToollessResponse(Prompt prompt) {
        acquirePermit();
        return chatClient.prompt(prompt)
                .call()
                .content();
    }


    public MemoryEntry generateMemoryResponse(Prompt prompt) {
        acquirePermit();
        return chatClient.prompt(prompt)
                .call()
                .entity(MemoryEntry.class);
    }

    public OpinionEntry generateOpinionResponse(Prompt prompt) {
        acquirePermit();
        return chatClient.prompt(prompt)
                .tools(calendarActions, pythonActions, searchActions)
                .call()
                .entity(OpinionEntry.class);
    }

    public DesireEntry generateDesireResponse(Prompt prompt) {
        acquirePermit();
        return chatClient.prompt(prompt)
                .call()
                .entity(DesireEntry.class);
    }


}