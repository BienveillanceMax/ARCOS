package Personality.Desires;

import Exceptions.ResponseParsingException;
import LLM.LLMClient;
import LLM.LLMResponseParser;
import LLM.Prompts.PromptBuilder;
import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.Models.OpinionEntry;
import Memory.LongTermMemory.service.MemoryService;
import Personality.Values.ValueProfile;
import Producers.DesireInitativeProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import Personality.Values.Entities.DimensionSchwartz;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DesireServiceTest {

    @Mock
    private DesireInitativeProducer desireInitativeProducer;

    @Mock
    private PromptBuilder promptBuilder;

    @Mock
    private ValueProfile valueProfile;

    @Mock
    private MemoryService memoryService;

    @Mock
    private LLMClient llmClient;

    @Mock
    private LLMResponseParser llmResponseParser;

    @InjectMocks
    private DesireService desireService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testProcessOpinion_createDesire() throws ResponseParsingException {
        // Arrange
        OpinionEntry opinionEntry = new OpinionEntry();
        opinionEntry.setAssociatedDesire(null);
        opinionEntry.setPolarity(0.8);
        opinionEntry.setStability(0.8);
        opinionEntry.setMainDimension(DimensionSchwartz.OPENNESS_TO_CHANGE);

        when(valueProfile.averageByDimension(any(DimensionSchwartz.class))).thenReturn(80.0);
        when(promptBuilder.buildDesirePrompt(any(OpinionEntry.class), anyDouble())).thenReturn("prompt");
        when(llmClient.generateDesireResponse(anyString())).thenReturn("response");
        when(llmResponseParser.parseDesireFromResponse(anyString(), any())).thenReturn(new DesireEntry());

        // Act
        desireService.processOpinion(opinionEntry);

        // Assert
        verify(memoryService, times(1)).storeDesire(any(DesireEntry.class));
    }

    @Test
    void testProcessOpinion_createDesire_intensityTooLow() {
        // Arrange
        OpinionEntry opinionEntry = new OpinionEntry();
        opinionEntry.setAssociatedDesire(null);
        opinionEntry.setPolarity(0.4);
        opinionEntry.setStability(0.5);
        opinionEntry.setMainDimension(DimensionSchwartz.OPENNESS_TO_CHANGE);

        when(valueProfile.averageByDimension(any(DimensionSchwartz.class))).thenReturn(50.0);

        // Act
        desireService.processOpinion(opinionEntry);

        // Assert
        verify(memoryService, never()).storeDesire(any(DesireEntry.class));
    }

    @Test
    void testProcessOpinion_updateDesire_initiativeTriggered() {
        // Arrange
        OpinionEntry opinionEntry = new OpinionEntry();
        opinionEntry.setAssociatedDesire("desire1");

        DesireEntry desireEntry = new DesireEntry();
        desireEntry.setIntensity(0.1);

        when(memoryService.getDesire("desire1")).thenReturn(desireEntry);

        // Act
        desireService.processOpinion(opinionEntry);

        // Assert
        verify(desireInitativeProducer, times(1)).initDesireInitiative(any(DesireEntry.class));
    }

    @Test
    void testProcessOpinion_updateDesire_initiativeNotTriggered() {
        // Arrange
        OpinionEntry opinionEntry = new OpinionEntry();
        opinionEntry.setAssociatedDesire("desire1");

        DesireEntry desireEntry = new DesireEntry();
        desireEntry.setIntensity(0.3);

        when(memoryService.getDesire("desire1")).thenReturn(desireEntry);

        // Act
        desireService.processOpinion(opinionEntry);

        // Assert
        verify(desireInitativeProducer, never()).initDesireInitiative(any(DesireEntry.class));
    }

    @Test
    void testProcessOpinion_llmParsingException() throws ResponseParsingException {
        // Arrange
        OpinionEntry opinionEntry = new OpinionEntry();
        opinionEntry.setAssociatedDesire(null);
        opinionEntry.setPolarity(0.8);
        opinionEntry.setStability(0.8);
        opinionEntry.setMainDimension(DimensionSchwartz.OPENNESS_TO_CHANGE);

        when(valueProfile.averageByDimension(any(DimensionSchwartz.class))).thenReturn(80.0);
        when(promptBuilder.buildDesirePrompt(any(OpinionEntry.class), anyDouble())).thenReturn("prompt");
        when(llmClient.generateDesireResponse(anyString())).thenReturn("response");
        when(llmResponseParser.parseDesireFromResponse(anyString(), any())).thenThrow(new ResponseParsingException("parsing failed"));

        // Act & Assert
        // The code is designed to catch the parsing exception and return null after retries.
        // The test should assert this behavior, not expect a RuntimeException.
        assertNull(desireService.processOpinion(opinionEntry));
    }
}
