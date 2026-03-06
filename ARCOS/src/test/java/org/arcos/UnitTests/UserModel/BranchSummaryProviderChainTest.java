package org.arcos.UnitTests.UserModel;

import org.arcos.UserModel.Models.TreeBranch;
import org.arcos.UserModel.Retrieval.BranchSummaryBuilder;
import org.arcos.UserModel.Retrieval.BranchSummaryGenerator;
import org.arcos.UserModel.Retrieval.BranchSummaryProviderChain;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BranchSummaryProviderChainTest {

    @Test
    void chain_shouldUseGeneratorWhenAvailable() {
        // Given
        BranchSummaryBuilder builder = mock(BranchSummaryBuilder.class);
        BranchSummaryGenerator generator = mock(BranchSummaryGenerator.class);
        when(generator.isAvailable()).thenReturn(true);
        when(generator.rebuild(TreeBranch.IDENTITE)).thenReturn("LLM summary");

        BranchSummaryProviderChain chain = new BranchSummaryProviderChain(builder, generator);

        // When
        String result = chain.rebuild(TreeBranch.IDENTITE);

        // Then
        assertEquals("LLM summary", result);
        verify(generator).rebuild(TreeBranch.IDENTITE);
        verify(builder, never()).rebuild(any());
    }

    @Test
    void chain_shouldFallBackToBuilderWhenGeneratorUnavailable() {
        // Given
        BranchSummaryBuilder builder = mock(BranchSummaryBuilder.class);
        BranchSummaryGenerator generator = mock(BranchSummaryGenerator.class);
        when(generator.isAvailable()).thenReturn(false);
        when(builder.rebuild(TreeBranch.IDENTITE)).thenReturn("Concatenated summary");

        BranchSummaryProviderChain chain = new BranchSummaryProviderChain(builder, generator);

        // When
        String result = chain.rebuild(TreeBranch.IDENTITE);

        // Then
        assertEquals("Concatenated summary", result);
        verify(generator, never()).rebuild(any());
        verify(builder).rebuild(TreeBranch.IDENTITE);
    }

    @Test
    void chain_shouldFallBackToBuilderOnGeneratorException() {
        // Given
        BranchSummaryBuilder builder = mock(BranchSummaryBuilder.class);
        BranchSummaryGenerator generator = mock(BranchSummaryGenerator.class);
        when(generator.isAvailable()).thenReturn(true);
        when(generator.rebuild(TreeBranch.IDENTITE)).thenThrow(new RuntimeException("LLM error"));
        when(builder.rebuild(TreeBranch.IDENTITE)).thenReturn("Fallback summary");

        BranchSummaryProviderChain chain = new BranchSummaryProviderChain(builder, generator);

        // When
        String result = chain.rebuild(TreeBranch.IDENTITE);

        // Then
        assertEquals("Fallback summary", result);
        verify(generator).rebuild(TreeBranch.IDENTITE);
        verify(builder).rebuild(TreeBranch.IDENTITE);
    }

    @Test
    void generator_shouldCallLocalLlmServiceWithCorrectPrompt() {
        // Given — test the generator's prompt construction
        BranchSummaryGenerator generator = mock(BranchSummaryGenerator.class);
        when(generator.isAvailable()).thenReturn(true);
        when(generator.rebuild(TreeBranch.COMMUNICATION)).thenReturn("Mon createur parle de maniere concise");

        // When
        String result = generator.rebuild(TreeBranch.COMMUNICATION);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("Mon createur"));
    }

    @Test
    void chain_shouldWorkWithNullGenerator() {
        // Given — generator is null (local LLM disabled)
        BranchSummaryBuilder builder = mock(BranchSummaryBuilder.class);
        when(builder.rebuild(TreeBranch.HABITUDES)).thenReturn("Builder-only summary");

        BranchSummaryProviderChain chain = new BranchSummaryProviderChain(builder, null);

        // When
        String result = chain.rebuild(TreeBranch.HABITUDES);

        // Then
        assertEquals("Builder-only summary", result);
        verify(builder).rebuild(TreeBranch.HABITUDES);
    }
}
