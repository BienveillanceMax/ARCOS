package LLM;

import LLM.service.RateLimiterService;
import Tools.Actions.CalendarActions;
import Tools.Actions.PythonActions;
import Tools.Actions.SearchActions;
import org.springframework.ai.chat.client.ChatClient;
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

    public String generatePlanningResponse(String prompt) {
        acquirePermit();
        return chatClient.prompt(prompt)
                .call()
                .content();
    }

    public String generateChatResponse(String prompt) {
        acquirePermit();
        return chatClient.prompt(prompt).tools(calendarActions, pythonActions, searchActions)
                .call()
                .content();
    }

    public String generateMemoryResponse(String prompt) {
        acquirePermit();
        return chatClient.prompt(prompt)
                .call()
                .content();
    }

    public String generateOpinionResponse(String prompt) {
        acquirePermit();
        return chatClient.prompt(prompt)
                .call()
                .content();
    }

    public String generateDesireResponse(String prompt) {
        acquirePermit();
        return chatClient.prompt(prompt)
                .call()
                .content();
    }

    public String generateResponse(String prompt) {
        acquirePermit();
        return chatClient.prompt(prompt)
                .call()
                .content();
    }


}