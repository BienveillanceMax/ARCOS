package Orchestrator;

import LLM.LLMClient;
import LLM.Prompts.PromptBuilder;
import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.Models.MemoryEntry;
import Memory.LongTermMemory.service.MemoryService;
import Personality.Desires.DesireService;
import Personality.Opinions.OpinionService;
import Personality.PersonalityOrchestrator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
    @Mock LLMClient llmClient;
    @Mock PromptBuilder promptBuilder;
    @Mock PersonalityOrchestrator personalityOrchestrator;

    @InjectMocks InitiativeService initiativeService;

    @Test
    void testProcessInitiative_Success() {
        // Given
        DesireEntry desire = new DesireEntry();
        desire.setDescription("Learn Java");
        desire.setStatus(DesireEntry.Status.PENDING);

        when(memoryService.searchMemories(anyString(), anyInt())).thenReturn(Collections.emptyList());
        when(opinionService.searchOpinions(anyString())).thenReturn(Collections.emptyList());
        when(promptBuilder.buildInitiativePrompt(any(DesireEntry.class), anyList(), anyList())).thenReturn(mock(Prompt.class));
        when(llmClient.generateChatResponse(any(Prompt.class))).thenReturn("I have read the documentation.");

        // When
        initiativeService.processInitiative(desire);

        // Then
        assertEquals(DesireEntry.Status.SATISFIED, desire.getStatus());
        verify(desireService).storeDesire(desire);
        verify(llmClient).generateChatResponse(any(Prompt.class));

        // Verify BDI loop closure
        verify(memoryService).storeMemory(any(MemoryEntry.class));
        verify(personalityOrchestrator).processExplicitMemory(any(MemoryEntry.class));
    }

    @Test
    void testProcessInitiative_Failure() {
        // Given
        DesireEntry desire = new DesireEntry();
        desire.setStatus(DesireEntry.Status.PENDING);

        when(memoryService.searchMemories(anyString(), anyInt())).thenThrow(new RuntimeException("Search failed"));

        // When
        try {
            initiativeService.processInitiative(desire);
            fail("Should have thrown exception");
        } catch (RuntimeException e) {
            // Expected
        }

        // Then
        assertEquals(DesireEntry.Status.PENDING, desire.getStatus());
        verify(desireService).storeDesire(desire);
    }
}
