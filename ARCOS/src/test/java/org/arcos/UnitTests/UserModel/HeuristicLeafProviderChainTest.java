package org.arcos.UnitTests.UserModel;

import org.arcos.UserModel.Heuristics.HeuristicLeafProviderChain;
import org.arcos.UserModel.Heuristics.HeuristicNarrativeGenerator;
import org.arcos.UserModel.Heuristics.HeuristicTextTemplates;
import org.arcos.UserModel.Models.ObservationLeaf;
import org.arcos.UserModel.Models.ObservationSource;
import org.arcos.UserModel.Models.SignificantChange;
import org.arcos.UserModel.Models.TreeBranch;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class HeuristicLeafProviderChainTest {

    @Test
    void chain_shouldUseTemplatesWhenConversationCountBelow5() {
        // Given
        HeuristicTextTemplates templates = mock(HeuristicTextTemplates.class);
        HeuristicNarrativeGenerator generator = mock(HeuristicNarrativeGenerator.class);
        when(generator.isAvailable()).thenReturn(true);
        when(templates.generateLeaves(anyList(), anyInt())).thenReturn(Collections.emptyList());

        HeuristicLeafProviderChain chain = new HeuristicLeafProviderChain(templates, generator);

        List<SignificantChange> changes = List.of(
                new SignificantChange("avg_word_count", 10.0, 25.0, TreeBranch.COMMUNICATION));

        // When
        chain.generateLeaves(changes, 3);

        // Then — always uses templates for cold-start, never generator
        verify(templates).generateLeaves(changes, 3);
        verify(generator, never()).generateLeaves(anyList(), anyInt());
    }

    @Test
    void chain_shouldUseGeneratorWhenAvailableAndCountAbove5() {
        // Given
        HeuristicTextTemplates templates = mock(HeuristicTextTemplates.class);
        HeuristicNarrativeGenerator generator = mock(HeuristicNarrativeGenerator.class);
        when(generator.isAvailable()).thenReturn(true);

        ObservationLeaf leaf = new ObservationLeaf("Mon createur parle longuement", TreeBranch.COMMUNICATION, ObservationSource.HEURISTIC);
        when(generator.generateLeaves(anyList(), anyInt())).thenReturn(List.of(leaf));

        HeuristicLeafProviderChain chain = new HeuristicLeafProviderChain(templates, generator);

        List<SignificantChange> changes = List.of(
                new SignificantChange("avg_word_count", 10.0, 25.0, TreeBranch.COMMUNICATION));

        // When
        List<ObservationLeaf> result = chain.generateLeaves(changes, 10);

        // Then
        assertEquals(1, result.size());
        assertEquals("Mon createur parle longuement", result.get(0).getText());
        verify(generator).generateLeaves(changes, 10);
        verify(templates, never()).generateLeaves(anyList(), anyInt());
    }

    @Test
    void chain_shouldFallBackToTemplatesOnGeneratorException() {
        // Given
        HeuristicTextTemplates templates = mock(HeuristicTextTemplates.class);
        HeuristicNarrativeGenerator generator = mock(HeuristicNarrativeGenerator.class);
        when(generator.isAvailable()).thenReturn(true);
        when(generator.generateLeaves(anyList(), anyInt())).thenThrow(new RuntimeException("LLM failed"));

        ObservationLeaf templateLeaf = new ObservationLeaf("Mon créateur s'exprime de manière détaillée.", TreeBranch.COMMUNICATION, ObservationSource.HEURISTIC);
        when(templates.generateLeaves(anyList(), anyInt())).thenReturn(List.of(templateLeaf));

        HeuristicLeafProviderChain chain = new HeuristicLeafProviderChain(templates, generator);

        List<SignificantChange> changes = List.of(
                new SignificantChange("avg_word_count", 10.0, 25.0, TreeBranch.COMMUNICATION));

        // When
        List<ObservationLeaf> result = chain.generateLeaves(changes, 10);

        // Then
        assertEquals(1, result.size());
        verify(generator).generateLeaves(changes, 10);
        verify(templates).generateLeaves(changes, 10);
    }

    @Test
    void generator_shouldProduceValidObservationLeaves() {
        // Given
        HeuristicNarrativeGenerator generator = mock(HeuristicNarrativeGenerator.class);
        ObservationLeaf generated = new ObservationLeaf(
                "Mon createur utilise un vocabulaire riche", TreeBranch.COMMUNICATION, ObservationSource.HEURISTIC);
        when(generator.generateLeaves(anyList(), anyInt())).thenReturn(List.of(generated));

        // When
        List<ObservationLeaf> result = generator.generateLeaves(
                List.of(new SignificantChange("avg_word_length", 3.5, 5.2, TreeBranch.COMMUNICATION)), 10);

        // Then
        assertEquals(1, result.size());
        ObservationLeaf leaf = result.get(0);
        assertNotNull(leaf.getId());
        assertNotNull(leaf.getText());
        assertTrue(leaf.getText().startsWith("Mon createur"));
        assertEquals(ObservationSource.HEURISTIC, leaf.getSource());
        assertEquals(TreeBranch.COMMUNICATION, leaf.getBranch());
    }

    @Test
    void chain_shouldWorkWithNullGenerator() {
        // Given — generator is null (local LLM disabled)
        HeuristicTextTemplates templates = mock(HeuristicTextTemplates.class);
        when(templates.generateLeaves(anyList(), anyInt())).thenReturn(Collections.emptyList());

        HeuristicLeafProviderChain chain = new HeuristicLeafProviderChain(templates, null);

        List<SignificantChange> changes = List.of(
                new SignificantChange("avg_word_count", 10.0, 25.0, TreeBranch.COMMUNICATION));

        // When
        chain.generateLeaves(changes, 10);

        // Then — falls through to templates
        verify(templates).generateLeaves(changes, 10);
    }
}
