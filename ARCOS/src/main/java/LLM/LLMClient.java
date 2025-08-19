package LLM;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class LLMClient
{

    private final ChatClient chatClient;

    public LLMClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String generatePlanningResponse(String prompt) {
        return chatClient.prompt(prompt)
                .call()
                .content();
    }

    public String generateFormulationResponse(String prompt) {
        return chatClient.prompt(prompt)
                .call()
                .content();
    }

    public String generateOpinionResponse(String prompt) {
        return chatClient.prompt(prompt)
                .call()
                .content();
    }

    public reactor.core.publisher.Flux<String> generateStreamingResponse(String prompt) {
        return chatClient.prompt(prompt)
                .stream()
                .content();
    }
}