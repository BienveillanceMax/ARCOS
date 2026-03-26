package org.arcos.UnitTests.LLM;

import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.LLM.Client.ChatOrchestrator;
import org.arcos.LLM.Client.LLMClient;
import org.arcos.LLM.Client.ResponseObject.MemoryResponse;
import org.arcos.Memory.LongTermMemory.Models.MemoryEntry;
import org.arcos.Memory.LongTermMemory.Repositories.MemoryRepository;
import org.arcos.Tools.Actions.CalendarActions;
import org.arcos.Tools.Actions.MemoryActions;
import org.arcos.Tools.Actions.PlannedActionActions;
import org.arcos.Tools.Actions.PythonActions;
import org.arcos.Tools.Actions.SearchActions;
import org.arcos.Tools.Actions.GdeltActions;
import org.arcos.Tools.Actions.WeatherActions;
import org.arcos.Tools.Actions.WebPageActions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires vérifiant l'intégration du QuestionAnswerAdvisor dans ChatOrchestrator
 * et l'isolation des appels internes de LLMClient.
 */
class LLMClientAdvisorTest {

    private LLMClient llmClient;
    private ChatOrchestrator chatOrchestrator;

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

    @Mock
    private PlannedActionActions plannedActionActions;

    @Mock
    private MemoryActions memoryActions;

    @Mock
    private WebPageActions webPageActions;

    @Mock
    private WeatherActions weatherActions;

    @Mock
    private GdeltActions gdeltActions;

    @Mock
    private MemoryRepository memoryRepository;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private CentralFeedBackHandler feedBackHandler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(memoryRepository.getVectorStore()).thenReturn(vectorStore);
    }

    // ===== T1 : Vérification de la construction de l'advisor dans ChatOrchestrator =====

    @Test
    void constructor_ShouldCallGetVectorStore_WhenBuildingAdvisor() {
        chatOrchestrator = new ChatOrchestrator(chatClientBuilder, calendarActions, pythonActions, searchActions,
                plannedActionActions, memoryActions, webPageActions, weatherActions, gdeltActions, memoryRepository, feedBackHandler, 3);

        // getVectorStore() doit être appelé exactement une fois pour construire le QuestionAnswerAdvisor
        verify(memoryRepository, times(1)).getVectorStore();
    }

    // ===== T2 : top-k configurable =====

    @Test
    void constructor_ShouldCompleteWithoutException_ForAnyPositiveTopK() {
        assertDoesNotThrow(() -> new ChatOrchestrator(chatClientBuilder, calendarActions, pythonActions,
                searchActions, plannedActionActions, memoryActions, webPageActions, weatherActions,
                gdeltActions, memoryRepository, feedBackHandler, 5));
    }

    // ===== T3 : generateChatResponse non-régressif avec advisor =====

    @Test
    void generateChatResponse_ShouldReturnContent_WhenAdvisorActive() {
        when(chatClient.prompt(any(Prompt.class))
                .advisors(any(Advisor[].class))
                .tools(any(Object[].class))
                .call()
                .content()).thenReturn("réponse de test");

        chatOrchestrator = new ChatOrchestrator(chatClientBuilder, calendarActions, pythonActions, searchActions,
                plannedActionActions, memoryActions, webPageActions, weatherActions, gdeltActions, memoryRepository, feedBackHandler, 3);

        String result = chatOrchestrator.generateChatResponse(new Prompt("bonjour"));

        assertEquals("réponse de test", result);
    }

    // ===== T4 : generateMemoryResponse ne doit pas être affecté par l'advisor =====

    @Test
    void generateMemoryResponse_ShouldReturnNull_WhenResponseIsNull() {
        when(chatClient.prompt(any(Prompt.class)).call().entity(any(BeanOutputConverter.class))).thenReturn(null);

        llmClient = new LLMClient(chatClientBuilder, pythonActions, searchActions);

        MemoryEntry result = llmClient.generateMemoryResponse(new Prompt("test mémoire"));

        assertNull(result);
    }

    // ===== T5 : Dégradation gracieuse si VectorStore retourne un mock vide =====

    @Test
    void constructor_ShouldNotThrow_WhenVectorStoreIsEmpty() {
        // VectorStore valide mais sans données (collection vide)
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(java.util.Collections.emptyList());

        assertDoesNotThrow(() -> new ChatOrchestrator(chatClientBuilder, calendarActions, pythonActions,
                searchActions, plannedActionActions, memoryActions, webPageActions, weatherActions,
                gdeltActions, memoryRepository, feedBackHandler, 3));
    }
}
