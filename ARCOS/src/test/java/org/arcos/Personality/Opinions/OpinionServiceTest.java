package org.arcos.Personality.Opinions;

import LLM.LLMClient;
import LLM.Prompts.PromptBuilder;
import Memory.LongTermMemory.Models.MemoryEntry;
import Memory.LongTermMemory.Models.OpinionEntry;
import Memory.LongTermMemory.Repositories.OpinionRepository;
import Personality.Opinions.OpinionService;
import Personality.Values.ValueProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OpinionServiceTest {

    @InjectMocks
    private OpinionService opinionService;

    @Mock
    private LLMClient llmClient;

    @Mock
    private OpinionRepository opinionRepository;

    @Mock
    private PromptBuilder promptBuilder;

    @Mock
    private ValueProfile valueProfile;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void processInteraction_WhenNoSimilarOpinions_ShouldAddNewOpinion() {
        // Given
        MemoryEntry memoryEntry = new MemoryEntry();
        memoryEntry.setId("memory-1");
        OpinionEntry newOpinion = new OpinionEntry();
        newOpinion.setSubject("New Subject");
        newOpinion.setAssociatedMemories(new ArrayList<>());
        newOpinion.setCreatedAt(LocalDateTime.now());
        newOpinion.setUpdatedAt(LocalDateTime.now());

        when(promptBuilder.buildOpinionPrompt(any(MemoryEntry.class))).thenReturn(new Prompt("prompt"));
        when(llmClient.generateOpinionResponse(any(Prompt.class))).thenReturn(newOpinion);
        when(opinionRepository.search(any())).thenReturn(new ArrayList<>());

        // When
        List<OpinionEntry> result = opinionService.processInteraction(memoryEntry);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(opinionRepository).save(any());
    }

    @Test
    void processInteraction_WhenLLMFails_ShouldReturnNull() {
        // Given
        MemoryEntry memoryEntry = new MemoryEntry();
        memoryEntry.setId("memory-1");
        when(promptBuilder.buildOpinionPrompt(any(MemoryEntry.class))).thenReturn(new Prompt("prompt"));
        when(llmClient.generateOpinionResponse(any(Prompt.class))).thenThrow(new RuntimeException("LLM failed"));

        // When
        List<OpinionEntry> result = opinionService.processInteraction(memoryEntry);

        // Then
        assertNull(result);
        verify(opinionRepository, never()).save(any());
    }

    @Test
    void processInteraction_WhenUpdateFails_ShouldReturnEmptyList() {
        // Given
        MemoryEntry memoryEntry = new MemoryEntry();
        memoryEntry.setId("memory-1");
        OpinionEntry newOpinion = new OpinionEntry();
        newOpinion.setSubject("Existing Subject");
        newOpinion.setPolarity(0.5);
        newOpinion.setAssociatedMemories(new ArrayList<>());

        OpinionEntry existingOpinion = new OpinionEntry();
        existingOpinion.setId("opinion-1");
        existingOpinion.setSubject("Existing Subject");
        existingOpinion.setStability(0.1);
        existingOpinion.setCreatedAt(LocalDateTime.now());
        existingOpinion.setUpdatedAt(LocalDateTime.now());
        existingOpinion.setAssociatedMemories(new ArrayList<>());
        existingOpinion.setSummary("summary");
        existingOpinion.setNarrative("narrative");
        existingOpinion.setPolarity(0.5);
        existingOpinion.setConfidence(0.5);
        existingOpinion.setAssociatedDesire("desire-1");

        when(promptBuilder.buildOpinionPrompt(any(MemoryEntry.class))).thenReturn(new Prompt("prompt"));
        when(llmClient.generateOpinionResponse(any(Prompt.class))).thenReturn(newOpinion);
        when(opinionRepository.search(any())).thenReturn(Collections.singletonList(new Document(existingOpinion.getId(), existingOpinion.getPayload())));
        when(valueProfile.averageByDimension(any())).thenReturn(50.0);
        when(valueProfile.dimensionAverage()).thenReturn(50.0);


        // When
        List<OpinionEntry> result = opinionService.processInteraction(memoryEntry);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
