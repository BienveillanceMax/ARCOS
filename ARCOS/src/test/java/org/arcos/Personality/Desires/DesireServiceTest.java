package org.arcos.Personality.Desires;

import Exceptions.DesireCreationException;
import LLM.LLMClient;
import LLM.Prompts.PromptBuilder;
import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.Models.OpinionEntry;
import Memory.LongTermMemory.Repositories.DesireRepository;
import Memory.LongTermMemory.Repositories.OpinionRepository;
import Personality.Desires.DesireService;
import Personality.Values.ValueProfile;
import common.utils.ObjectCreationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
        OpinionEntry opinionEntry = new OpinionEntry();
        opinionEntry.setId("opinion-1");
        opinionEntry.setPolarity(0.8);
        opinionEntry.setStability(0.9);
        opinionEntry.setAssociatedMemories(new ArrayList<>());
        opinionEntry.setCreatedAt(LocalDateTime.now());
        opinionEntry.setUpdatedAt(LocalDateTime.now());

        when(valueProfile.averageByDimension(any())).thenReturn(80.0);

        DesireEntry desireEntry = new DesireEntry();
        desireEntry.setId("desire-1");
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

        Document desireDocument = new Document(desireEntry.getId(), "test", desireEntry.getPayload());
        when(desireRepository.findById("desire-1")).thenReturn(Optional.of(desireDocument));
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
}

