package org.arcos.Orchestrator;

import Exceptions.ResponseParsingException;
import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.Models.MemoryEntry;
import Memory.LongTermMemory.Models.OpinionEntry;
import Memory.LongTermMemory.service.MemoryService;
import Personality.Desires.DesireService;
import Personality.Opinions.OpinionService;
import Personality.PersonalityOrchestrator;
import Personality.Values.ValueProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PersonalityOrchestratorTest {

    @InjectMocks
    private PersonalityOrchestrator personalityOrchestrator;

    @Mock
    private MemoryService memoryService;

    @Mock
    private OpinionService opinionService;

    @Mock
    private DesireService desireService;

    @Mock
    private ValueProfile valueProfile;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void processMemory_SuccessPath_ShouldCallAllServices() throws Exception {
        // Given
        String conversation = "test conversation";
        MemoryEntry memoryEntry = new MemoryEntry();
        OpinionEntry opinionEntry = new OpinionEntry();
        when(memoryService.memorizeConversation(conversation)).thenReturn(memoryEntry);
        when(opinionService.processInteraction(memoryEntry)).thenReturn(Collections.singletonList(opinionEntry));
        when(desireService.processOpinion(opinionEntry)).thenReturn(new DesireEntry());

        // When
        personalityOrchestrator.processMemory(conversation);

        // Then
        verify(memoryService).memorizeConversation(conversation);
        verify(opinionService).processInteraction(memoryEntry);
        verify(desireService).processOpinion(opinionEntry);
    }

    @Test
    void processMemory_MemorizationFails_ShouldNotProceed() throws Exception {
        // Given
        String conversation = "test conversation";
        when(memoryService.memorizeConversation(conversation)).thenThrow(new ResponseParsingException("test exception"));

        // When
        personalityOrchestrator.processMemory(conversation);

        // Then
        verify(memoryService, times(3)).memorizeConversation(conversation);
        verify(opinionService, never()).processInteraction(any(MemoryEntry.class));
        verify(desireService, never()).processOpinion(any(OpinionEntry.class));
    }

    @Test
    void processMemory_OpinionFormationFails_ShouldNotCreateDesire() throws Exception {
        // Given
        String conversation = "test conversation";
        MemoryEntry memoryEntry = new MemoryEntry();
        when(memoryService.memorizeConversation(conversation)).thenReturn(memoryEntry);
        when(opinionService.processInteraction(memoryEntry)).thenReturn(null);

        // When
        personalityOrchestrator.processMemory(conversation);

        // Then
        verify(memoryService).memorizeConversation(conversation);
        verify(opinionService, times(3)).processInteraction(memoryEntry);
        verify(desireService, never()).processOpinion(any(OpinionEntry.class));
    }
}

