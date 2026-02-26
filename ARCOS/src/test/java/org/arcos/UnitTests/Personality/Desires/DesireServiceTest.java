package org.arcos.UnitTests.Personality.Desires;

import org.arcos.Exceptions.DesireCreationException;
import org.arcos.LLM.Client.LLMClient;
import org.arcos.LLM.Prompts.PromptBuilder;
import org.arcos.Memory.LongTermMemory.Models.DesireEntry;
import org.arcos.Memory.LongTermMemory.Models.OpinionEntry;
import org.arcos.Memory.LongTermMemory.Repositories.DesireRepository;
import org.arcos.Memory.LongTermMemory.Repositories.OpinionRepository;
import org.arcos.Personality.Desires.DesireService;
import org.arcos.Personality.Values.ValueProfile;
import org.arcos.common.utils.ObjectCreationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DesireServiceTest {

    @InjectMocks
    private DesireService desireService;

    @Mock
    private PromptBuilder promptBuilder;

    @Mock
    private ValueProfile valueProfile;

    @Mock
    private LLMClient llmClient;

    @Mock
    private DesireRepository desireRepository;

    @Mock
    private OpinionRepository opinionRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void processOpinion_WhenIntensityIsHighAndNoAssociatedDesire_ShouldCreateDesire() throws DesireCreationException {
        // Given
        OpinionEntry opinionEntry = ObjectCreationUtils.createOpinionEntry();
        opinionEntry.setAssociatedDesire(null);
        opinionEntry.setPolarity(1);
        opinionEntry.setStability(1);

        DesireEntry desireEntry = ObjectCreationUtils.createIntensePendingDesireEntry(opinionEntry.getId());
        desireEntry.setOpinionId(opinionEntry.getId());

        when(valueProfile.averageByDimension(any())).thenReturn(80.0);
        when(promptBuilder.buildDesirePrompt(any(OpinionEntry.class), anyDouble())).thenReturn(new Prompt("prompt"));
        when(llmClient.generateDesireResponse(any(Prompt.class))).thenReturn(desireEntry);

        // When
        DesireEntry result = desireService.processOpinion(opinionEntry);

        // Then
        assertNotNull(result);
        verify(desireRepository).save(any());
        verify(opinionRepository).save(any());
        assertEquals(desireEntry.getId(), opinionEntry.getAssociatedDesire());
    }

    @Test
    void processOpinion_WhenIntensityIsLow_ShouldNotCreateDesire() {
        // Given
        OpinionEntry opinionEntry = new OpinionEntry();
        opinionEntry.setPolarity(0.1);
        opinionEntry.setStability(0.2);
        when(valueProfile.averageByDimension(any())).thenReturn(30.0);

        // When
        DesireEntry result = desireService.processOpinion(opinionEntry);

        // Then
        assertNull(result);
        verify(desireRepository, never()).save(any());
    }

    @Test
    void processOpinion_WhenAssociatedDesireExists_ShouldUpdateDesire() {
        // Given
        OpinionEntry opinionEntry = ObjectCreationUtils.createOpinionEntry();
        DesireEntry desireEntry = ObjectCreationUtils.createIntensePendingDesireEntry(opinionEntry.getId());
        opinionEntry.setAssociatedDesire(desireEntry.getId());

        Document desireDocument = new Document(desireEntry.getId(), "test", desireEntry.getPayload());
        when(desireRepository.findById(desireEntry.getId())).thenReturn(Optional.of(desireDocument));
        when(valueProfile.averageByDimension(any())).thenReturn(50.0);
        when(valueProfile.calculateValueAlignment(any())).thenReturn(1.0);

        // When
        DesireEntry result = desireService.processOpinion(opinionEntry);

        // Then
        assertNotNull(result);
        verify(desireRepository).save(any());
    }

    @Test
    void processOpinion_WhenDesireCreationFails_ShouldReturnNull() throws DesireCreationException {
        // Given
        OpinionEntry opinionEntry = new OpinionEntry();
        opinionEntry.setPolarity(0.8);
        opinionEntry.setStability(0.9);
        when(valueProfile.averageByDimension(any())).thenReturn(80.0);
        when(promptBuilder.buildDesirePrompt(any(OpinionEntry.class), anyDouble())).thenReturn(new Prompt("prompt"));
        doThrow(new DesireCreationException("LLM error")).when(llmClient).generateDesireResponse(any(Prompt.class));

        // When
        DesireEntry result = desireService.processOpinion(opinionEntry);

        // Then
        assertNull(result);
        verify(desireRepository, never()).save(any());
    }

    @Test
    void createDesire_WhenLLMReturnsNullTwiceThenSucceeds_ShouldReturnDesireOnThirdAttempt() throws DesireCreationException {
        // Given — high intensity to pass D_CREATE_THRESHOLD
        OpinionEntry opinionEntry = ObjectCreationUtils.createOpinionEntry(); // associatedDesire = ""
        opinionEntry.setPolarity(1.0);
        opinionEntry.setStability(1.0);

        DesireEntry validDesire = ObjectCreationUtils.createIntensePendingDesireEntry(opinionEntry.getId());

        when(valueProfile.averageByDimension(any())).thenReturn(80.0);
        when(promptBuilder.buildDesirePrompt(any(OpinionEntry.class), anyDouble())).thenReturn(new Prompt("prompt"));
        when(llmClient.generateDesireResponse(any(Prompt.class)))
                .thenReturn(null)        // attempt 1: null
                .thenReturn(null)        // attempt 2: null
                .thenReturn(validDesire); // attempt 3: success

        // When
        DesireEntry result = desireService.processOpinion(opinionEntry);

        // Then
        assertNotNull(result);
        verify(llmClient, times(3)).generateDesireResponse(any(Prompt.class));
        verify(desireRepository).save(any());
    }

    @Test
    void createDesire_WhenLLMAlwaysReturnsNull_ShouldReturnNullAfterThreeAttempts() throws DesireCreationException {
        // Given
        OpinionEntry opinionEntry = ObjectCreationUtils.createOpinionEntry(); // associatedDesire = ""
        opinionEntry.setPolarity(1.0);
        opinionEntry.setStability(1.0);

        when(valueProfile.averageByDimension(any())).thenReturn(80.0);
        when(promptBuilder.buildDesirePrompt(any(OpinionEntry.class), anyDouble())).thenReturn(new Prompt("prompt"));
        when(llmClient.generateDesireResponse(any(Prompt.class))).thenReturn(null); // always null

        // When
        DesireEntry result = desireService.processOpinion(opinionEntry);

        // Then
        assertNull(result);
        verify(llmClient, times(3)).generateDesireResponse(any(Prompt.class));
        verify(desireRepository, never()).save(any());
    }
}

