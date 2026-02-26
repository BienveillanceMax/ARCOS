package org.arcos.UnitTests.LLM;

import org.arcos.Exceptions.DesireCreationException;
import org.arcos.LLM.Client.LLMClient;
import org.arcos.LLM.Client.ResponseObject.DesireResponse;
import org.arcos.LLM.Client.ResponseObject.MemoryResponse;
import org.arcos.LLM.Client.ResponseObject.OpinionResponse;
import org.arcos.Memory.LongTermMemory.Models.DesireEntry;
import org.arcos.Memory.LongTermMemory.Models.MemoryEntry;
import org.arcos.Memory.LongTermMemory.Models.OpinionEntry;
import org.arcos.Tools.Actions.CalendarActions;
import org.arcos.Tools.Actions.PythonActions;
import org.arcos.Tools.Actions.SearchActions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class LLMClientTest {

    private LLMClient llmClient;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private CalendarActions calendarActions;

    @Mock
    private PythonActions pythonActions;

    @Mock
    private SearchActions searchActions;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        llmClient = new LLMClient(chatClientBuilder, calendarActions, pythonActions, searchActions);
    }

    // ===== generateMemoryResponse =====

    @Test
    void generateMemoryResponse_WhenResponseIsNull_ShouldReturnNull() {
        when(chatClient.prompt(any(Prompt.class)).call().entity(any(BeanOutputConverter.class))).thenReturn(null);

        MemoryEntry result = llmClient.generateMemoryResponse(new Prompt("test"));

        assertNull(result);
    }

    @Test
    void generateMemoryResponse_WhenContentIsNull_ShouldReturnNull() {
        MemoryResponse response = new MemoryResponse();
        response.setContent(null);
        when(chatClient.prompt(any(Prompt.class)).call().entity(any(BeanOutputConverter.class))).thenReturn(response);

        MemoryEntry result = llmClient.generateMemoryResponse(new Prompt("test"));

        assertNull(result);
    }

    @Test
    void generateMemoryResponse_WhenContentIsBlank_ShouldReturnNull() {
        MemoryResponse response = new MemoryResponse();
        response.setContent("   ");
        when(chatClient.prompt(any(Prompt.class)).call().entity(any(BeanOutputConverter.class))).thenReturn(response);

        MemoryEntry result = llmClient.generateMemoryResponse(new Prompt("test"));

        assertNull(result);
    }

    // ===== generateOpinionResponse =====

    @Test
    void generateOpinionResponse_WhenResponseIsNull_ShouldReturnNull() {
        when(chatClient.prompt(any(Prompt.class)).tools(calendarActions, pythonActions, searchActions)
                .call().entity(any(BeanOutputConverter.class))).thenReturn(null);

        OpinionEntry result = llmClient.generateOpinionResponse(new Prompt("test"));

        assertNull(result);
    }

    @Test
    void generateOpinionResponse_WhenSummaryIsNull_ShouldReturnNull() {
        OpinionResponse response = new OpinionResponse();
        response.setSummary(null);
        when(chatClient.prompt(any(Prompt.class)).tools(calendarActions, pythonActions, searchActions)
                .call().entity(any(BeanOutputConverter.class))).thenReturn(response);

        OpinionEntry result = llmClient.generateOpinionResponse(new Prompt("test"));

        assertNull(result);
    }

    @Test
    void generateOpinionResponse_WhenSummaryIsBlank_ShouldReturnNull() {
        OpinionResponse response = new OpinionResponse();
        response.setSummary("  ");
        when(chatClient.prompt(any(Prompt.class)).tools(calendarActions, pythonActions, searchActions)
                .call().entity(any(BeanOutputConverter.class))).thenReturn(response);

        OpinionEntry result = llmClient.generateOpinionResponse(new Prompt("test"));

        assertNull(result);
    }

    // ===== generateDesireResponse =====

    @Test
    void generateDesireResponse_WhenResponseIsNull_ShouldReturnNull() throws DesireCreationException {
        when(chatClient.prompt(any(Prompt.class)).call().entity(any(BeanOutputConverter.class))).thenReturn(null);

        DesireEntry result = llmClient.generateDesireResponse(new Prompt("test"));

        assertNull(result);
    }

    @Test
    void generateDesireResponse_WhenLabelIsNull_ShouldReturnNull() throws DesireCreationException {
        DesireResponse response = new DesireResponse();
        response.setLabel(null);
        when(chatClient.prompt(any(Prompt.class)).call().entity(any(BeanOutputConverter.class))).thenReturn(response);

        DesireEntry result = llmClient.generateDesireResponse(new Prompt("test"));

        assertNull(result);
    }

    @Test
    void generateDesireResponse_WhenLabelIsBlank_ShouldReturnNull() throws DesireCreationException {
        DesireResponse response = new DesireResponse();
        response.setLabel("");
        when(chatClient.prompt(any(Prompt.class)).call().entity(any(BeanOutputConverter.class))).thenReturn(response);

        DesireEntry result = llmClient.generateDesireResponse(new Prompt("test"));

        assertNull(result);
    }
}
