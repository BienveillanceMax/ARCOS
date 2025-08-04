package LLM;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mistralai.MistralAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LLMService {

    private final MistralAiChatModel chatModel;

    @Autowired
    public LLMService() {
        this.chatModel = MistralAiChatModel.builder().build();;

    }

    public String generatePlanningResponse(String prompt) {
        ChatResponse response = chatModel.call(
                new Prompt(List.of(new UserMessage(prompt)))
        );
        return response.getResult().getOutput().getText();
    }

    public String generateFormulationResponse(String prompt) {
        // Même méthode, ou avec des paramètres différents
        ChatResponse response = chatModel.call(
                new Prompt(List.of(new UserMessage(prompt)))
        );
        return response.getResult().getOutput().getText();
    }
}