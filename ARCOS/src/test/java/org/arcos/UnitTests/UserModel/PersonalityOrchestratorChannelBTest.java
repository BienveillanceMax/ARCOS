package org.arcos.UnitTests.UserModel;

import org.arcos.Memory.LongTermMemory.Models.MemoryEntry;
import org.arcos.Memory.LongTermMemory.Models.OpinionEntry;
import org.arcos.Memory.LongTermMemory.service.MemoryService;
import org.arcos.Personality.Desires.DesireService;
import org.arcos.Personality.Opinions.OpinionService;
import org.arcos.Personality.PersonalityOrchestrator;
import org.arcos.UserModel.Extraction.UserTreeUpdater;
import org.arcos.UserModel.Models.ObservationCandidate;
import org.arcos.UserModel.Models.ObservationCandidateDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour PersonalityOrchestrator — routing des observations (UM-004).
 * Verifie que les observations extraites par Channel B sont bien routees vers UserTreeUpdater.
 */
class PersonalityOrchestratorChannelBTest {

    private PersonalityOrchestrator orchestrator;

    @Mock
    private MemoryService memoryService;

    @Mock
    private OpinionService opinionService;

    @Mock
    private DesireService desireService;

    @Mock
    private UserTreeUpdater userTreeUpdater;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orchestrator = new PersonalityOrchestrator(memoryService, opinionService, desireService, userTreeUpdater);
    }

    // ===== T1 : observations presentes -> routees vers UserTreeUpdater =====

    @Test
    void processMemory_WithObservations_ShouldRouteToUserTreeUpdater() throws Exception {
        // Given
        String conversation = "USER: Je suis developpeur. ASSISTANT: Cool!";
        MemoryEntry memoryEntry = new MemoryEntry();
        ObservationCandidateDto obs1 = new ObservationCandidateDto("Mon createur est developpeur", "IDENTITE", null, true);
        ObservationCandidateDto obs2 = new ObservationCandidateDto("Mon createur aime le code", "INTERETS", null, false);

        when(memoryService.memorizeConversation(conversation)).thenReturn(memoryEntry);
        when(memoryService.getAndClearLastObservations()).thenReturn(List.of(obs1, obs2));
        when(opinionService.processInteraction(memoryEntry)).thenReturn(Collections.emptyList());
        when(userTreeUpdater.processObservation(any(ObservationCandidate.class)))
                .thenReturn(UserTreeUpdater.UpdateResult.ADD);

        // When
        orchestrator.processMemory(conversation);

        // Then
        verify(userTreeUpdater, times(2)).processObservation(any(ObservationCandidate.class));
    }

    // ===== T2 : pas d'observations -> UserTreeUpdater non appele =====

    @Test
    void processMemory_WithNoObservations_ShouldNotCallUserTreeUpdater() throws Exception {
        // Given
        String conversation = "USER: Quelle heure est-il? ASSISTANT: 14h.";
        MemoryEntry memoryEntry = new MemoryEntry();

        when(memoryService.memorizeConversation(conversation)).thenReturn(memoryEntry);
        when(memoryService.getAndClearLastObservations()).thenReturn(null);
        when(opinionService.processInteraction(memoryEntry)).thenReturn(Collections.emptyList());

        // When
        orchestrator.processMemory(conversation);

        // Then
        verify(userTreeUpdater, never()).processObservation(any(ObservationCandidate.class));
    }

    // ===== T3 : observations avec liste vide -> UserTreeUpdater non appele =====

    @Test
    void processMemory_WithEmptyObservationList_ShouldNotCallUserTreeUpdater() throws Exception {
        // Given
        String conversation = "USER: Meteo? ASSISTANT: Il pleut.";
        MemoryEntry memoryEntry = new MemoryEntry();

        when(memoryService.memorizeConversation(conversation)).thenReturn(memoryEntry);
        when(memoryService.getAndClearLastObservations()).thenReturn(Collections.emptyList());
        when(opinionService.processInteraction(memoryEntry)).thenReturn(Collections.emptyList());

        // When
        orchestrator.processMemory(conversation);

        // Then
        verify(userTreeUpdater, never()).processObservation(any(ObservationCandidate.class));
    }

    // ===== T4 : UserTreeUpdater null -> pas d'erreur =====

    @Test
    void processMemory_WithNullUserTreeUpdater_ShouldNotFail() throws Exception {
        // Given
        PersonalityOrchestrator orchestratorNoUpdater =
                new PersonalityOrchestrator(memoryService, opinionService, desireService, null);
        String conversation = "USER: test ASSISTANT: test";
        MemoryEntry memoryEntry = new MemoryEntry();

        when(memoryService.memorizeConversation(conversation)).thenReturn(memoryEntry);
        when(opinionService.processInteraction(memoryEntry)).thenReturn(Collections.emptyList());

        // When / Then — should not throw
        orchestratorNoUpdater.processMemory(conversation);

        // getAndClearLastObservations should never be called when updater is null
        verify(memoryService, never()).getAndClearLastObservations();
    }

    // ===== T5 : exception dans processObservation -> ne bloque pas le pipeline =====

    @Test
    void processMemory_WhenObservationRoutingFails_ShouldContinueProcessing() throws Exception {
        // Given
        String conversation = "USER: test ASSISTANT: test";
        MemoryEntry memoryEntry = new MemoryEntry();
        ObservationCandidateDto obs = new ObservationCandidateDto("Mon createur test", "IDENTITE", null, false);
        OpinionEntry opinionEntry = new OpinionEntry();

        when(memoryService.memorizeConversation(conversation)).thenReturn(memoryEntry);
        when(memoryService.getAndClearLastObservations()).thenReturn(List.of(obs));
        when(userTreeUpdater.processObservation(any(ObservationCandidate.class)))
                .thenThrow(new RuntimeException("Routing error"));
        when(opinionService.processInteraction(memoryEntry)).thenReturn(Collections.singletonList(opinionEntry));

        // When
        orchestrator.processMemory(conversation);

        // Then — opinion processing should still happen despite routing failure
        verify(opinionService).processInteraction(memoryEntry);
        verify(desireService).processOpinion(opinionEntry);
    }
}
