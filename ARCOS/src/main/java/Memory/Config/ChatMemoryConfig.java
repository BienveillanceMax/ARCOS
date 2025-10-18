package Memory.Config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.context.annotation.Bean;

import java.util.List;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatMemoryConfig {

    @Bean
    public ChatMemory chatMemory() {
        return new ChatMemory() {
            @Override
            public void add(String s, List<Message> list) {
                // Not implemented
            }

            @Override
            public List<Message> get(String s) {
                // Not implemented
                return null;
            }

            @Override
            public void clear(String s) {
                // Not implemented
            }
        };
    }
}