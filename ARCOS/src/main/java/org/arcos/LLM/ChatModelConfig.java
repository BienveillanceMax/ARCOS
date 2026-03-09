package org.arcos.LLM;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mistralai.MistralAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Marque MistralAiChatModel comme @Primary pour que le ChatClient.Builder
 * l'utilise par defaut quand plusieurs ChatModel coexistent (ex: Ollama).
 */
@Configuration
public class ChatModelConfig {

    @Bean
    @Primary
    public ChatModel primaryChatModel(MistralAiChatModel mistralAiChatModel) {
        return mistralAiChatModel;
    }
}
