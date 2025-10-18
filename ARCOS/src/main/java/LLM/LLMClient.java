package LLM;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class LLMClient {

    private final ChatClient chatClient;

    public LLMClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String generate(String prompt) {
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    public reactor.core.publisher.Flux<String> generateStreaming(String prompt) {
        return chatClient.prompt()
                .user(prompt)
                .stream()
                .content();
    }
}