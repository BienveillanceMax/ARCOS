package LLM;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class LLMClient
{

    private final ChatClient chatClient;
    private final RateLimiter rateLimiter = RateLimiter.create(0.9);

    public LLMClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    private void acquirePermit() {
        rateLimiter.acquire();
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

    public reactor.core.publisher.Flux<String> generateStreamingResponse(String prompt) {
        acquirePermit();
        return chatClient.prompt(prompt)
                .stream()
                .content();
    }


}