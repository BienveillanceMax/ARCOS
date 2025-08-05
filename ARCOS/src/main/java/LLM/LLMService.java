package LLM;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LLMService
{

    private final ChatClient chatClient;

    public LLMService(ChatClient.Builder chatClientBuilder) {
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

    public reactor.core.publisher.Flux<String> generateStreamingResponse(String prompt) {
        return chatClient.prompt(prompt)
                .stream()
                .content();
    }
}