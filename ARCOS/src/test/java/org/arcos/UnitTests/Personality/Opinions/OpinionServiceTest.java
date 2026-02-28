package org.arcos.UnitTests.Personality.Opinions;

import org.arcos.Configuration.PersonalityProperties;
import org.arcos.LLM.Client.LLMClient;
import org.arcos.LLM.Prompts.PromptBuilder;
import org.arcos.Memory.LongTermMemory.Models.MemoryEntry;
import org.arcos.Memory.LongTermMemory.Models.OpinionEntry;
import org.arcos.Memory.LongTermMemory.Repositories.OpinionRepository;
import org.arcos.Personality.Opinions.OpinionService;
import org.arcos.Personality.Values.Entities.DimensionSchwartz;
import org.arcos.Personality.Values.ValueProfile;
import org.arcos.common.utils.ObjectCreationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;

import java.util.*;

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

    @Mock
    private PersonalityProperties personalityProperties;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(personalityProperties.getOpinionSearchTopk()).thenReturn(10);
        when(personalityProperties.getOpinionSimilarityThreshold()).thenReturn(0.85);
    }

    @Test
    void processInteraction_WhenNoSimilarOpinions_ShouldAddNewOpinion() {
        // Given
        MemoryEntry memoryEntry = ObjectCreationUtils.createMemoryEntry();
        OpinionEntry newOpinion = ObjectCreationUtils.createOpinionEntry();

        newOpinion.getAssociatedMemories().add(memoryEntry.getId());

        when(promptBuilder.buildOpinionPrompt(any(MemoryEntry.class))).thenReturn(new Prompt("prompt"));
        when(llmClient.generateOpinionResponse(any(Prompt.class))).thenReturn(newOpinion);

        // Mock Canonicalization calls
        when(promptBuilder.buildCanonicalizationPrompt(anyString(), anyString())).thenReturn(new Prompt("canonicalPrompt"));
        when(llmClient.generateToollessResponse(any(Prompt.class))).thenReturn("Canonical Text");

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
        MemoryEntry memoryEntry = ObjectCreationUtils.createMemoryEntry();

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
        MemoryEntry memoryEntry = ObjectCreationUtils.createMemoryEntry();


        OpinionEntry newOpinion = ObjectCreationUtils.createOpinionEntry();
        OpinionEntry existingOpinion = ObjectCreationUtils.createOpinionEntry();
        EnumMap<DimensionSchwartz,Double> values = ObjectCreationUtils.createAverageByDimension();

        // Ensure canonicalText is present in newOpinion to avoid search crash if mocked llm response is missing it?
        // No, processInteraction calls getOpinionFromMemoryEntry which sets it.
        // But here we mock generateOpinionResponse returning newOpinion.
        // We also need to mock canonicalization call.

        Map<String, Object> payload = existingOpinion.getPayload();
        payload.put("distance", (float) 0.1);

        // Ensure payload has canonicalText (ObjectCreationUtils now adds it, but just in case)
        if (!payload.containsKey("canonicalText")) {
             payload.put("canonicalText", "Canonical Text");
        }

        Document returnDocument = new Document(existingOpinion.getSummary(), payload);
        Document returnDocument2 = new Document(existingOpinion.getSummary(), payload);



        when(promptBuilder.buildOpinionPrompt(any(MemoryEntry.class))).thenReturn(new Prompt("prompt"));
        when(llmClient.generateOpinionResponse(any(Prompt.class))).thenReturn(newOpinion);

        // Mock Canonicalization calls
        when(promptBuilder.buildCanonicalizationPrompt(anyString(), anyString())).thenReturn(new Prompt("canonicalPrompt"));
        when(llmClient.generateToollessResponse(any(Prompt.class))).thenReturn("Canonical Text");

        when(opinionRepository.search(any())).thenReturn(List.of(returnDocument, returnDocument2));
        when(valueProfile.averageByDimension()).thenReturn(values);
        when(valueProfile.dimensionAverage()).thenReturn(50.0);


        // When
        List<OpinionEntry> result = opinionService.processInteraction(memoryEntry);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    void processInteraction_WhenSimilarOpinionDiesOnUpdate_ShouldReturnEmptyListWithNoNulls() {
        // Given
        MemoryEntry memoryEntry = ObjectCreationUtils.createMemoryEntry();

        // New opinion from LLM with contradicting polarity (-0.8) to force stability to 0
        OpinionEntry newOpinion = ObjectCreationUtils.createOpinionEntry();
        newOpinion.setPolarity(-0.8);

        // Existing opinion in DB (polarity=+0.5, stability=0.1 from factory)
        OpinionEntry existingOpinion = ObjectCreationUtils.createOpinionEntry();
        Map<String, Object> payload = existingOpinion.getPayload();
        payload.put("distance", (float) 0.05); // similarity = 0.95 >= 0.85 threshold
        if (!payload.containsKey("canonicalText")) {
            payload.put("canonicalText", "Je pense que l'on devrait tous s'aimer.");
        }
        Document existingDoc = new Document(existingOpinion.getSummary(), payload);

        // Mocks
        when(promptBuilder.buildOpinionPrompt(any(MemoryEntry.class))).thenReturn(new Prompt("prompt"));
        when(llmClient.generateOpinionResponse(any(Prompt.class))).thenReturn(newOpinion);
        when(promptBuilder.buildCanonicalizationPrompt(anyString(), anyString())).thenReturn(new Prompt("canonicalPrompt"));
        when(llmClient.generateToollessResponse(any(Prompt.class))).thenReturn("Canonical Text");
        when(opinionRepository.search(any())).thenReturn(List.of(existingDoc));

        // ValueProfile: averageByDimension(dim) → 50.0 for imp; no-arg → {CONSERVATION: 50.0} for normVp=0
        EnumMap<DimensionSchwartz, Double> dimMap = new EnumMap<>(DimensionSchwartz.class);
        dimMap.put(DimensionSchwartz.CONSERVATION, 50.0);
        dimMap.put(DimensionSchwartz.SELF_TRANSCENDENCE, 0.0);
        dimMap.put(DimensionSchwartz.OPENNESS_TO_CHANGE, 0.0);
        dimMap.put(DimensionSchwartz.SELF_ENHANCEMENT, 0.0);
        when(valueProfile.averageByDimension(any())).thenReturn(50.0);
        when(valueProfile.averageByDimension()).thenReturn(dimMap);
        when(valueProfile.dimensionAverage()).thenReturn(50.0);

        // When
        List<OpinionEntry> result = opinionService.processInteraction(memoryEntry);

        // Then: opinion died (stability dropped to 0), list must be empty and contain no nulls
        assertNotNull(result);
        assertTrue(result.isEmpty(), "Result should be empty when all similar opinions die");
        assertTrue(result.stream().noneMatch(java.util.Objects::isNull), "Result must not contain null entries");
    }
}
