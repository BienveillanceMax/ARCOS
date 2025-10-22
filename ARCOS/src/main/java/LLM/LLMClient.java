package LLM;

import LLM.service.RateLimiterService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class LLMClient
{

    private final ChatClient chatClient;
    private final RateLimiterService rateLimiterService;

    public LLMClient(ChatClient.Builder chatClientBuilder, RateLimiterService rateLimiterService) {
        this.chatClient = chatClientBuilder.build();
        this.rateLimiterService = rateLimiterService;
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

    public String generateFormulationResponse(String prompt) {
        acquirePermit();
        return chatClient.prompt(prompt)
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