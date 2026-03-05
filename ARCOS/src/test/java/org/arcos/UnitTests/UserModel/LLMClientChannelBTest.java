package org.arcos.UnitTests.UserModel;

import org.arcos.LLM.Client.LLMClient;
import org.arcos.Memory.LongTermMemory.Models.Subject;
import org.arcos.Memory.LongTermMemory.Repositories.MemoryRepository;
import org.arcos.Tools.Actions.CalendarActions;
import org.arcos.Tools.Actions.MemoryActions;
import org.arcos.Tools.Actions.PlannedActionActions;
import org.arcos.Tools.Actions.PythonActions;
import org.arcos.Tools.Actions.SearchActions;
import org.arcos.Tools.Actions.WeatherActions;
import org.arcos.Tools.Actions.WebPageActions;
import org.arcos.UserModel.Models.MemoryAndObservationsResponse;
import org.arcos.UserModel.Models.ObservationCandidateDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour LLMClient.generateMemoryAndObservationsResponse() — UM-004 Channel B.
 * Utilise le meme pattern deep-stubs que LLMClientTest.
 */
class LLMClientChannelBTest {

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

    @Mock
    private PlannedActionActions plannedActionActions;

    @Mock
    private MemoryActions memoryActions;

    @Mock
    private WebPageActions webPageActions;

    @Mock
    private WeatherActions weatherActions;

    @Mock
    private MemoryRepository memoryRepository;

    @Mock
    private VectorStore vectorStore;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(memoryRepository.getVectorStore()).thenReturn(vectorStore);
        llmClient = new LLMClient(chatClientBuilder, calendarActions, pythonActions, searchActions,
                plannedActionActions, memoryActions, webPageActions, weatherActions, memoryRepository, 3);
    }

    // ===== T1 : reponse null -> retourne null =====

    @Test
    void generateMemoryAndObservationsResponse_WhenResponseIsNull_ShouldReturnNull() {
        when(chatClient.prompt(any(Prompt.class)).call().entity(any(BeanOutputConverter.class))).thenReturn(null);

        MemoryAndObservationsResponse result = llmClient.generateMemoryAndObservationsResponse(new Prompt("test"));

        assertNull(result);
    }

    // ===== T2 : content null -> retourne null =====

    @Test
    void generateMemoryAndObservationsResponse_WhenContentIsNull_ShouldReturnNull() {
        MemoryAndObservationsResponse response = new MemoryAndObservationsResponse();
        response.setContent(null);
        when(chatClient.prompt(any(Prompt.class)).call().entity(any(BeanOutputConverter.class))).thenReturn(response);

        MemoryAndObservationsResponse result = llmClient.generateMemoryAndObservationsResponse(new Prompt("test"));

        assertNull(result);
    }

    // ===== T3 : content blank -> retourne null =====

    @Test
    void generateMemoryAndObservationsResponse_WhenContentIsBlank_ShouldReturnNull() {
        MemoryAndObservationsResponse response = new MemoryAndObservationsResponse();
        response.setContent("   ");
        when(chatClient.prompt(any(Prompt.class)).call().entity(any(BeanOutputConverter.class))).thenReturn(response);

        MemoryAndObservationsResponse result = llmClient.generateMemoryAndObservationsResponse(new Prompt("test"));

        assertNull(result);
    }

    // ===== T4 : reponse valide avec observations -> retourne la reponse complete =====

    @Test
    void generateMemoryAndObservationsResponse_WhenValid_ShouldReturnFullResponse() {
        MemoryAndObservationsResponse response = new MemoryAndObservationsResponse();
        response.setContent("Mon createur m'a parle de son travail");
        response.setSubject(Subject.fromString("SELF"));
        response.setSatisfaction(0.7);

        ObservationCandidateDto obs = new ObservationCandidateDto(
                "Mon createur est developpeur", "IDENTITE", null, true);
        response.setUserObservations(List.of(obs));

        when(chatClient.prompt(any(Prompt.class)).call().entity(any(BeanOutputConverter.class))).thenReturn(response);

        MemoryAndObservationsResponse result = llmClient.generateMemoryAndObservationsResponse(new Prompt("test"));

        assertNotNull(result);
        assertEquals("Mon createur m'a parle de son travail", result.getContent());
        assertNotNull(result.getUserObservations());
        assertEquals(1, result.getUserObservations().size());
        assertEquals("Mon createur est developpeur", result.getUserObservations().get(0).getObservation());
        assertTrue(result.getUserObservations().get(0).isExplicite());
    }

    // ===== T5 : reponse valide sans observations -> retourne reponse avec liste vide/null =====

    @Test
    void generateMemoryAndObservationsResponse_WhenValidWithNoObservations_ShouldReturnResponseWithNullObservations() {
        MemoryAndObservationsResponse response = new MemoryAndObservationsResponse();
        response.setContent("Conversation fonctionnelle sur la meteo");
        response.setSubject(Subject.fromString("OTHER"));
        response.setSatisfaction(0.5);
        response.setUserObservations(null);

        when(chatClient.prompt(any(Prompt.class)).call().entity(any(BeanOutputConverter.class))).thenReturn(response);

        MemoryAndObservationsResponse result = llmClient.generateMemoryAndObservationsResponse(new Prompt("test"));

        assertNotNull(result);
        assertEquals("Conversation fonctionnelle sur la meteo", result.getContent());
        assertNull(result.getUserObservations());
    }
}
