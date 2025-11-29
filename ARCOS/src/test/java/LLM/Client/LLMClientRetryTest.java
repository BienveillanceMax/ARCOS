package LLM.Client;

import Memory.LongTermMemory.Models.MemoryEntry;
import Tools.Actions.CalendarActions;
import Tools.Actions.PythonActions;
import Tools.Actions.SearchActions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootTest(classes = LLMClientRetryTest.TestApp.class)
public class LLMClientRetryTest {

    @SpringBootApplication
    @Import(LLMClient.class)
    static class TestApp {
        @Bean
        public ChatClient chatClient() {
            return mock(ChatClient.class);
        }

        @Bean
        public ChatClient.Builder chatClientBuilder(ChatClient chatClient) {
            ChatClient.Builder builder = mock(ChatClient.Builder.class);
            when(builder.build()).thenReturn(chatClient);
            return builder;
        }
    }

    @Autowired
    private LLMClient llmClient;

    @MockBean
    private CalendarActions calendarActions;

    @MockBean
    private PythonActions pythonActions;

    @MockBean
    private SearchActions searchActions;

    @Autowired
    private ChatClient chatClient;

    @Test
    public void testGenerateMemoryResponseRetries() {
        // Setup ChatClient mock chain
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        // First 2 calls fail (return invalid JSON which causes RuntimeException in entity()), 3rd succeeds
        // Wait, entity() executes the conversion. We need to mock entity() to throw exception then succeed?
        // No, entity(Converter) calls the converter.
        // If we want to test the retry on exception, we should mock `entity` to throw exception.
        // Or better, let's mock `entity` directly if possible.

        // Actually, `BeanOutputConverter` parses the `content` of the response if we use `entity`.
        // But `entity(Converter)` implementation in Spring AI calls `call().content()` then `converter.convert()`.
        // Wait, checking Spring AI source or documentation:
        // `entity(BeanOutputConverter)`:
        // return this.response.map(response -> converter.convert(response.getResult().getOutput().getContent()));

        // So `entity` just calls converter.convert().

        // However, `entity` method is on `CallResponseSpec`.
        // So we can mock `entity` to throw exception.

        when(callResponseSpec.entity(any(org.springframework.ai.converter.StructuredOutputConverter.class)))
                .thenThrow(new RuntimeException("Parsing error 1"))
                .thenThrow(new RuntimeException("Parsing error 2"))
                .thenReturn(new LLM.Client.ResponseObject.MemoryResponse()); // Success

        // Trigger the method
        MemoryEntry result = llmClient.generateMemoryResponse(new Prompt("test"));

        // Verify retries
        verify(callResponseSpec, times(3)).entity(any(org.springframework.ai.converter.StructuredOutputConverter.class));
        assertNotNull(result);
    }
}
