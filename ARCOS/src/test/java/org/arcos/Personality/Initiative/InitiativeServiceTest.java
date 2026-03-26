package org.arcos.Personality.Initiative;

import org.arcos.LLM.Client.ChatOrchestrator;
import org.arcos.LLM.Prompts.PromptBuilder;
import org.arcos.Memory.LongTermMemory.Models.DesireEntry;
import org.arcos.Memory.LongTermMemory.Models.MemoryEntry;
import org.arcos.Memory.LongTermMemory.service.MemoryService;
import org.arcos.Personality.Desires.DesireService;
import org.arcos.Personality.Opinions.OpinionService;
import org.arcos.Personality.PersonalityOrchestrator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class InitiativeServiceTest {

    @Mock MemoryService memoryService;
    @Mock OpinionService opinionService;
    @Mock DesireService desireService;
    @Mock ChatOrchestrator chatOrchestrator;
    @Mock PromptBuilder promptBuilder;
    @Mock PersonalityOrchestrator personalityOrchestrator;

    @InjectMocks
    InitiativeService initiativeService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        lenient().doAnswer(invocation -> { ((Runnable) invocation.getArgument(0)).run(); return null; })
                .when(desireService).withDesireLock(any(Runnable.class));
    }

    @Test
    void testProcessInitiative_Success() {
        // Given
        DesireEntry desire = new DesireEntry();
        desire.setLabel("Apprendre Java");
        desire.setDescription("Learn Java");
        desire.setStatus(DesireEntry.Status.PENDING);

        when(memoryService.searchMemories(anyString(), anyInt())).thenReturn(Collections.emptyList());
        when(opinionService.searchOpinions(anyString())).thenReturn(Collections.emptyList());
        when(promptBuilder.buildInitiativePrompt(any(DesireEntry.class), anyList(), anyList())).thenReturn(new Prompt(new SystemMessage("test initiative prompt")));
        when(chatOrchestrator.generateChatResponse(any(Prompt.class))).thenReturn("I have read the documentation.");

        // When
        boolean success = initiativeService.processInitiative(desire);

        // Then
        assertTrue(success);
        assertEquals(DesireEntry.Status.SATISFIED, desire.getStatus());
        verify(desireService).storeDesire(desire);
        verify(chatOrchestrator).generateChatResponse(any(Prompt.class));

        // Verify BDI loop closure
        verify(memoryService).storeMemory(any(MemoryEntry.class));
        verify(personalityOrchestrator).processMemoryEntryIntoOpinion(any(MemoryEntry.class));
    }

    @Test
    void testProcessInitiative_SuccessCapsLongResult() {
        // Given
        DesireEntry desire = new DesireEntry();
        desire.setLabel("Long result test");
        desire.setDescription("Test desire");
        desire.setStatus(DesireEntry.Status.PENDING);

        String longResult = "A".repeat(500);
        when(memoryService.searchMemories(anyString(), anyInt())).thenReturn(Collections.emptyList());
        when(opinionService.searchOpinions(anyString())).thenReturn(Collections.emptyList());
        when(promptBuilder.buildInitiativePrompt(any(DesireEntry.class), anyList(), anyList())).thenReturn(new Prompt(new SystemMessage("test initiative prompt")));
        when(chatOrchestrator.generateChatResponse(any(Prompt.class))).thenReturn(longResult);

        // When
        boolean success = initiativeService.processInitiative(desire);

        // Then
        assertTrue(success);
        assertEquals(DesireEntry.Status.SATISFIED, desire.getStatus());
    }

    @Test
    void testProcessInitiative_SkipNonActionable() {
        // Given
        DesireEntry desire = new DesireEntry();
        desire.setLabel("Créer un réseau d'espoir");
        desire.setDescription("Trop abstrait pour agir");
        desire.setStatus(DesireEntry.Status.PENDING);

        when(memoryService.searchMemories(anyString(), anyInt())).thenReturn(Collections.emptyList());
        when(opinionService.searchOpinions(anyString())).thenReturn(Collections.emptyList());
        when(promptBuilder.buildInitiativePrompt(any(DesireEntry.class), anyList(), anyList())).thenReturn(new Prompt(new SystemMessage("test initiative prompt")));
        when(chatOrchestrator.generateChatResponse(any(Prompt.class))).thenReturn("[SKIP] Aucun outil ne permet cette action.");

        // When
        boolean success = initiativeService.processInitiative(desire);

        // Then
        assertFalse(success);
        assertEquals(DesireEntry.Status.PENDING, desire.getStatus());
        // No memory stored, no opinion formed
        verify(memoryService, never()).storeMemory(any(MemoryEntry.class));
        verify(personalityOrchestrator, never()).processMemoryEntryIntoOpinion(any(MemoryEntry.class));
        // Desire NOT re-stored (status unchanged, no need)
        verify(desireService, never()).storeDesire(desire);
    }

    @Test
    void testProcessInitiative_NullResult() {
        // Given
        DesireEntry desire = new DesireEntry();
        desire.setLabel("Null result test");
        desire.setDescription("Test desire");
        desire.setStatus(DesireEntry.Status.PENDING);

        when(memoryService.searchMemories(anyString(), anyInt())).thenReturn(Collections.emptyList());
        when(opinionService.searchOpinions(anyString())).thenReturn(Collections.emptyList());
        when(promptBuilder.buildInitiativePrompt(any(DesireEntry.class), anyList(), anyList())).thenReturn(new Prompt(new SystemMessage("test initiative prompt")));
        when(chatOrchestrator.generateChatResponse(any(Prompt.class))).thenReturn(null);

        // When
        boolean success = initiativeService.processInitiative(desire);

        // Then
        assertFalse(success);
        assertEquals(DesireEntry.Status.PENDING, desire.getStatus());
        verify(memoryService, never()).storeMemory(any(MemoryEntry.class));
        verify(desireService, never()).storeDesire(desire);
    }

    @Test
    void testProcessInitiative_Failure() {
        // Given
        DesireEntry desire = new DesireEntry();
        desire.setStatus(DesireEntry.Status.PENDING);

        when(memoryService.searchMemories(anyString(), anyInt())).thenThrow(new RuntimeException("Search failed"));

        // When
        boolean success = initiativeService.processInitiative(desire);

        // Then
        assertFalse(success);
        assertEquals(DesireEntry.Status.PENDING, desire.getStatus());
        verify(desireService).storeDesire(desire);
    }
}
