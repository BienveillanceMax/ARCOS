package org.arcos.UnitTests.UserModel;

import org.arcos.LLM.Client.LLMClient;
import org.arcos.LLM.Prompts.PromptBuilder;
import org.arcos.Memory.LongTermMemory.Models.MemoryEntry;
import org.arcos.Memory.LongTermMemory.Models.Subject;
import org.arcos.Memory.LongTermMemory.Repositories.MemoryRepository;
import org.arcos.Memory.LongTermMemory.service.MemoryService;
import org.arcos.UserModel.Models.MemoryAndObservationsResponse;
import org.arcos.UserModel.Models.ObservationCandidateDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour MemoryService — Channel B (UM-004).
 * Verifie le comportement de memorizeConversation() quand userModelEnabled=true,
 * et la methode getAndClearLastObservations().
 */
class MemoryServiceChannelBTest {

    private MemoryService memoryService;

    @Mock
    private MemoryRepository memoryRepository;

    @Mock
    private LLMClient llmClient;

    @Mock
    private PromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // userModelEnabled=true for Channel B tests
        memoryService = new MemoryService(memoryRepository, llmClient, promptBuilder, true);
    }

    // ===== T1 : userModelEnabled=true -> utilise generateMemoryAndObservationsResponse =====

    @Test
    void memorizeConversation_WhenUserModelEnabled_ShouldCallGenerateMemoryAndObservationsResponse() {
        // Given
        String conversation = "USER: Je suis developpeur. ASSISTANT: Genial!";
        Prompt prompt = new Prompt("test");
        MemoryAndObservationsResponse response = new MemoryAndObservationsResponse();
        response.setContent("Le createur est developpeur");
        response.setSubject(Subject.fromString("SELF"));
        response.setSatisfaction(0.8);
        response.setUserObservations(List.of(
                new ObservationCandidateDto("Mon createur est developpeur", "IDENTITE", null, true)
        ));

        when(promptBuilder.buildMemoryPrompt(conversation)).thenReturn(prompt);
        when(llmClient.generateMemoryAndObservationsResponse(prompt)).thenReturn(response);

        // When
        MemoryEntry result = memoryService.memorizeConversation(conversation);

        // Then
        assertNotNull(result);
        assertEquals("Le createur est developpeur", result.getContent());
        verify(llmClient).generateMemoryAndObservationsResponse(prompt);
        verify(llmClient, never()).generateMemoryResponse(any());
        verify(memoryRepository).save(any(Document.class));
    }

    // ===== T2 : observations sont stockees et accessibles via getAndClearLastObservations =====

    @Test
    void memorizeConversation_WhenUserModelEnabled_ShouldStoreObservations() {
        // Given
        String conversation = "test conversation";
        Prompt prompt = new Prompt("test");
        ObservationCandidateDto obs1 = new ObservationCandidateDto("Mon createur aime Java", "INTERETS", null, true);
        ObservationCandidateDto obs2 = new ObservationCandidateDto("Mon createur est curieux", "IDENTITE", null, false);

        MemoryAndObservationsResponse response = new MemoryAndObservationsResponse();
        response.setContent("test content");
        response.setSubject(Subject.fromString("SELF"));
        response.setSatisfaction(0.7);
        response.setUserObservations(List.of(obs1, obs2));

        when(promptBuilder.buildMemoryPrompt(conversation)).thenReturn(prompt);
        when(llmClient.generateMemoryAndObservationsResponse(prompt)).thenReturn(response);

        // When
        memoryService.memorizeConversation(conversation);
        List<ObservationCandidateDto> observations = memoryService.getAndClearLastObservations();

        // Then
        assertNotNull(observations);
        assertEquals(2, observations.size());
        assertEquals("Mon createur aime Java", observations.get(0).getObservation());
        assertEquals("INTERETS", observations.get(0).getBranche());
        assertTrue(observations.get(0).isExplicite());
    }

    // ===== T3 : getAndClearLastObservations efface les observations apres lecture =====

    @Test
    void getAndClearLastObservations_ShouldClearAfterFirstCall() {
        // Given
        String conversation = "test conversation";
        Prompt prompt = new Prompt("test");
        MemoryAndObservationsResponse response = new MemoryAndObservationsResponse();
        response.setContent("content");
        response.setSubject(Subject.fromString("OTHER"));
        response.setSatisfaction(0.5);
        response.setUserObservations(List.of(
                new ObservationCandidateDto("Mon createur test", "HABITUDES", null, false)
        ));

        when(promptBuilder.buildMemoryPrompt(conversation)).thenReturn(prompt);
        when(llmClient.generateMemoryAndObservationsResponse(prompt)).thenReturn(response);

        memoryService.memorizeConversation(conversation);

        // When
        List<ObservationCandidateDto> first = memoryService.getAndClearLastObservations();
        List<ObservationCandidateDto> second = memoryService.getAndClearLastObservations();

        // Then
        assertNotNull(first);
        assertEquals(1, first.size());
        assertNull(second, "Second call should return null after clearing");
    }

    // ===== T4 : reponse null -> retourne null et pas d'observations =====

    @Test
    void memorizeConversation_WhenResponseIsNull_ShouldReturnNullAndNoObservations() {
        // Given
        String conversation = "test conversation";
        Prompt prompt = new Prompt("test");
        when(promptBuilder.buildMemoryPrompt(conversation)).thenReturn(prompt);
        when(llmClient.generateMemoryAndObservationsResponse(prompt)).thenReturn(null);

        // When
        MemoryEntry result = memoryService.memorizeConversation(conversation);
        List<ObservationCandidateDto> observations = memoryService.getAndClearLastObservations();

        // Then
        assertNull(result);
        assertNull(observations, "No observations should be stored when response is null");
        verify(memoryRepository, never()).save(any(Document.class));
    }

    // ===== T5 : reponse avec content blank -> retourne null =====

    @Test
    void memorizeConversation_WhenContentIsBlank_ShouldReturnNull() {
        // Given
        String conversation = "test conversation";
        Prompt prompt = new Prompt("test");
        MemoryAndObservationsResponse response = new MemoryAndObservationsResponse();
        response.setContent("   ");
        response.setSubject(Subject.fromString("OTHER"));
        response.setSatisfaction(0.5);

        when(promptBuilder.buildMemoryPrompt(conversation)).thenReturn(prompt);
        when(llmClient.generateMemoryAndObservationsResponse(prompt)).thenReturn(response);

        // When
        MemoryEntry result = memoryService.memorizeConversation(conversation);

        // Then
        assertNull(result);
        verify(memoryRepository, never()).save(any(Document.class));
    }
}
