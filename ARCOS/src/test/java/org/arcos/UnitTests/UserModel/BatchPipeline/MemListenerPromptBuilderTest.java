package org.arcos.UnitTests.UserModel.BatchPipeline;

import org.arcos.UserModel.BatchPipeline.MemListenerPromptBuilder;
import org.arcos.UserModel.BatchPipeline.Queue.ConversationChunk;
import org.arcos.UserModel.BatchPipeline.Queue.ConversationPair;
import org.arcos.UserModel.PersonaTree.PersonaTreeGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemListenerPromptBuilderTest {

    @Mock
    private PersonaTreeGate personaTreeGate;

    private MemListenerPromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        promptBuilder = new MemListenerPromptBuilder(personaTreeGate);
    }

    @Test
    void buildPrompt_producesExpectedFormat() {
        // Given
        Map<String, String> leaves = new LinkedHashMap<>();
        leaves.put("1_Bio.Hair", "brun");
        when(personaTreeGate.getNonEmptyLeaves()).thenReturn(leaves);
        when(personaTreeGate.getValidLeafPaths()).thenReturn(Set.of("1_Bio.Hair", "1_Bio.Eyes"));

        ConversationChunk chunk = new ConversationChunk(
                List.of(new ConversationPair("Je suis brun", "Noté !")),
                "conv-1"
        );

        // When
        String prompt = promptBuilder.buildPrompt(chunk);

        // Then
        assertTrue(prompt.contains("## Dialogue"));
        assertTrue(prompt.contains("Utilisateur: Je suis brun"));
        assertTrue(prompt.contains("Assistant: Noté !"));
        assertTrue(prompt.contains("## État actuel du PersonaTree"));
        assertTrue(prompt.contains("1_Bio.Hair = brun"));
        assertTrue(prompt.contains("## Chemins valides"));
        assertTrue(prompt.contains("## Instructions"));
        assertTrue(prompt.contains("## Opérations"));
        assertTrue(prompt.contains("ADD("));
        assertTrue(prompt.contains("UPDATE("));
        assertTrue(prompt.contains("DELETE("));
        assertTrue(prompt.contains("NO_OP()"));
    }

    @Test
    void buildPrompt_withEmptyTree_showsAucuneDonnee() {
        // Given
        when(personaTreeGate.getNonEmptyLeaves()).thenReturn(Map.of());
        when(personaTreeGate.getValidLeafPaths()).thenReturn(Set.of("path.a", "path.b"));

        ConversationChunk chunk = new ConversationChunk(
                List.of(new ConversationPair("Bonjour", "Salut")),
                "conv-2"
        );

        // When
        String prompt = promptBuilder.buildPrompt(chunk);

        // Then
        assertTrue(prompt.contains("Aucune donnée"));
    }

    @Test
    void buildPrompt_withExistingLeaves_includesThemInPrompt() {
        // Given
        Map<String, String> leaves = new LinkedHashMap<>();
        leaves.put("identity.name", "Pierre");
        leaves.put("identity.age", "30 ans");
        when(personaTreeGate.getNonEmptyLeaves()).thenReturn(leaves);
        when(personaTreeGate.getValidLeafPaths()).thenReturn(
                Set.of("identity.name", "identity.age", "identity.job"));

        ConversationChunk chunk = new ConversationChunk(
                List.of(new ConversationPair("Je suis dev", "Cool !")),
                "conv-3"
        );

        // When
        String prompt = promptBuilder.buildPrompt(chunk);

        // Then
        assertTrue(prompt.contains("identity.name = Pierre"));
        assertTrue(prompt.contains("identity.age = 30 ans"));
    }
}
