package Personality.Opinions;

import LLM.LLMClient;
import LLM.LLMResponseParser;
import LLM.Prompts.PromptBuilder;
import Memory.LongTermMemory.Models.MemoryEntry;
import Memory.LongTermMemory.Models.OpinionEntry;
import Memory.LongTermMemory.Models.SearchResult.SearchResult;
import Memory.LongTermMemory.service.MemoryService;
import Personality.Values.Entities.DimensionSchwartz;
import Personality.Values.ValueProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class OpinionServiceTest {

    @Mock
    private ValueProfile valueProfile;

    @Mock
    private PromptBuilder promptBuilder;

    @Mock
    private MemoryService memoryService;

    @Mock
    private LLMClient llmClient;

    @Mock
    private LLMResponseParser llmResponseParser;

    @InjectMocks
    private OpinionService opinionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testProcessInteraction_addOpinion() throws Exception {
        // Arrange
        MemoryEntry memoryEntry = new MemoryEntry();
        memoryEntry.setId("memory1");
        memoryEntry.setContent("Test content");

        OpinionEntry opinionEntry = new OpinionEntry();
        opinionEntry.setSubject("Test subject");
        opinionEntry.setMainDimension(DimensionSchwartz.OPENNESS_TO_CHANGE);

        when(promptBuilder.buildOpinionPrompt(any(MemoryEntry.class))).thenReturn("prompt");
        when(llmClient.generateOpinionResponse(anyString())).thenReturn("response");
        when(llmResponseParser.parseOpinionFromResponse(anyString(), any(MemoryEntry.class))).thenReturn(opinionEntry);
        when(memoryService.searchOpinions(anyString())).thenReturn(new ArrayList<>());
        when(valueProfile.averageByDimension(any(DimensionSchwartz.class))).thenReturn(60.0);

        // Act
        List<OpinionEntry> result = opinionService.processInteraction(memoryEntry);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(memoryService, times(1)).storeOpinion(any(OpinionEntry.class));
    }

    @Test
    void testProcessInteraction_updateOpinion() throws Exception {
        // Arrange
        MemoryEntry memoryEntry = new MemoryEntry();
        memoryEntry.setId("memory1");
        memoryEntry.setContent("Test content");

        OpinionEntry newOpinionEntry = new OpinionEntry();
        newOpinionEntry.setSubject("Test subject");
        newOpinionEntry.setPolarity(0.5);
        newOpinionEntry.setMainDimension(DimensionSchwartz.OPENNESS_TO_CHANGE);

        OpinionEntry existingOpinion = new OpinionEntry();
        existingOpinion.setId("opinion1");
        existingOpinion.setSubject("Test subject");
        existingOpinion.setPolarity(0.2);
        existingOpinion.setConfidence(0.5);
        existingOpinion.setStability(0.5);
        existingOpinion.setMainDimension(DimensionSchwartz.OPENNESS_TO_CHANGE);

        SearchResult<OpinionEntry> searchResult = new SearchResult<>(existingOpinion, 0.9f);
        List<SearchResult<OpinionEntry>> searchResults = Collections.singletonList(searchResult);

        when(promptBuilder.buildOpinionPrompt(any(MemoryEntry.class))).thenReturn("prompt");
        when(llmClient.generateOpinionResponse(anyString())).thenReturn("response");
        when(llmResponseParser.parseOpinionFromResponse(anyString(), any(MemoryEntry.class))).thenReturn(newOpinionEntry);
        when(memoryService.searchOpinions(anyString())).thenReturn(searchResults);
        when(valueProfile.averageByDimension()).thenReturn(new EnumMap<>(Map.of(DimensionSchwartz.OPENNESS_TO_CHANGE, 70.0)));
        when(valueProfile.averageByDimension(DimensionSchwartz.OPENNESS_TO_CHANGE)).thenReturn(70.0);
        when(valueProfile.dimensionAverage()).thenReturn(50.0);


        // Act
        List<OpinionEntry> result = opinionService.processInteraction(memoryEntry);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(memoryService, times(1)).storeOpinion(any(OpinionEntry.class));
        assertTrue(result.get(0).getStability() > 0.5); // Stability should increase
    }

    @Test
    void testProcessInteraction_llmError() throws Exception {
        // Arrange
        MemoryEntry memoryEntry = new MemoryEntry();
        memoryEntry.setId("memory1");
        memoryEntry.setContent("Test content");

        when(promptBuilder.buildOpinionPrompt(any(MemoryEntry.class))).thenReturn("prompt");
        when(llmClient.generateOpinionResponse(anyString())).thenReturn("response");
        when(llmResponseParser.parseOpinionFromResponse(anyString(), any(MemoryEntry.class))).thenThrow(new RuntimeException("LLM parsing failed"));

        // Act
        List<OpinionEntry> result = opinionService.processInteraction(memoryEntry);

        // Assert
        assertNull(result);
    }

    @Test
    void testProcessInteraction_addOpinion_stabilityCalculation() throws Exception {
        // Arrange
        MemoryEntry memoryEntry = new MemoryEntry();
        memoryEntry.setId("memory1");
        memoryEntry.setContent("Test content");

        OpinionEntry opinionEntry = new OpinionEntry();
        opinionEntry.setSubject("Test subject");
        opinionEntry.setMainDimension(DimensionSchwartz.OPENNESS_TO_CHANGE);

        when(promptBuilder.buildOpinionPrompt(any(MemoryEntry.class))).thenReturn("prompt");
        when(llmClient.generateOpinionResponse(anyString())).thenReturn("response");
        when(llmResponseParser.parseOpinionFromResponse(anyString(), any(MemoryEntry.class))).thenReturn(opinionEntry);
        when(memoryService.searchOpinions(anyString())).thenReturn(new ArrayList<>());
        when(valueProfile.averageByDimension(DimensionSchwartz.OPENNESS_TO_CHANGE)).thenReturn(70.0);

        // Act
        List<OpinionEntry> result = opinionService.processInteraction(memoryEntry);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(0.85, result.get(0).getStability(), 0.01); // 0.5 + (70 / 200)
    }

    @Test
    void testProcessInteraction_updateOpinion_deleteOnLowStability() throws Exception {
        // Arrange
        MemoryEntry memoryEntry = new MemoryEntry();
        memoryEntry.setId("memory1");
        memoryEntry.setContent("Test content");

        OpinionEntry newOpinionEntry = new OpinionEntry();
        newOpinionEntry.setSubject("Test subject");
        newOpinionEntry.setPolarity(-0.8);
        newOpinionEntry.setMainDimension(DimensionSchwartz.CONSERVATION);

        OpinionEntry existingOpinion = new OpinionEntry();
        existingOpinion.setId("opinion1");
        existingOpinion.setSubject("Test subject");
        existingOpinion.setPolarity(0.8);
        existingOpinion.setConfidence(0.5);
        existingOpinion.setStability(0.1); // Low stability
        existingOpinion.setMainDimension(DimensionSchwartz.CONSERVATION);

        SearchResult<OpinionEntry> searchResult = new SearchResult<>(existingOpinion, 0.9f);
        List<SearchResult<OpinionEntry>> searchResults = Collections.singletonList(searchResult);

        when(promptBuilder.buildOpinionPrompt(any(MemoryEntry.class))).thenReturn("prompt");
        when(llmClient.generateOpinionResponse(anyString())).thenReturn("response");
        when(llmResponseParser.parseOpinionFromResponse(anyString(), any(MemoryEntry.class))).thenReturn(newOpinionEntry);
        when(memoryService.searchOpinions(anyString())).thenReturn(searchResults);
        when(valueProfile.averageByDimension()).thenReturn(new EnumMap<>(Map.of(DimensionSchwartz.CONSERVATION, 20.0)));
        when(valueProfile.averageByDimension(DimensionSchwartz.CONSERVATION)).thenReturn(20.0);
        when(valueProfile.dimensionAverage()).thenReturn(50.0);

        // Act
        List<OpinionEntry> result = opinionService.processInteraction(memoryEntry);

        // Assert
        assertTrue(result.isEmpty());
        verify(memoryService, times(1)).deleteOpinion("opinion1");
    }
}
