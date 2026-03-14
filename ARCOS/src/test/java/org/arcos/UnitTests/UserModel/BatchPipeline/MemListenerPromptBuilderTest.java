package org.arcos.UnitTests.UserModel.BatchPipeline;

import org.arcos.UserModel.BatchPipeline.MemListenerPromptBuilder;
import org.arcos.UserModel.BatchPipeline.Queue.ConversationChunk;
import org.arcos.UserModel.BatchPipeline.Queue.ConversationPair;
import org.arcos.UserModel.PersonaTree.PersonaNode;
import org.arcos.UserModel.PersonaTree.PersonaTree;
import org.arcos.UserModel.PersonaTree.PersonaTreeGate;
import org.arcos.UserModel.PersonaTree.PersonaTreeSchemaLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemListenerPromptBuilderTest {

    @Mock
    private PersonaTreeGate personaTreeGate;

    @Mock
    private PersonaTreeSchemaLoader schemaLoader;

    private MemListenerPromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        promptBuilder = new MemListenerPromptBuilder(personaTreeGate, schemaLoader);
    }

    private PersonaTree buildSmallTestTree() {
        LinkedHashMap<String, PersonaNode> hairChildren = new LinkedHashMap<>();
        hairChildren.put("Scalp_Hair", PersonaNode.leaf(""));
        hairChildren.put("Eyes", PersonaNode.leaf(""));

        LinkedHashMap<String, PersonaNode> appearanceChildren = new LinkedHashMap<>();
        appearanceChildren.put("Hair", PersonaNode.branch(hairChildren));

        LinkedHashMap<String, PersonaNode> bioChildren = new LinkedHashMap<>();
        bioChildren.put("Physical_Appearance", PersonaNode.branch(appearanceChildren));

        LinkedHashMap<String, PersonaNode> roots = new LinkedHashMap<>();
        roots.put("1_Bio", PersonaNode.branch(bioChildren));
        return new PersonaTree(roots);
    }

    @Test
    void buildPrompt_containsTable9KeyPhrases() {
        // Given
        when(schemaLoader.loadSchema()).thenReturn(buildSmallTestTree());
        when(personaTreeGate.getNonEmptyLeaves()).thenReturn(Map.of("1_Bio.Physical_Appearance.Hair.Scalp_Hair", "brun"));

        ConversationChunk chunk = new ConversationChunk(
                List.of(new ConversationPair("Je suis brun", "Noté !")),
                "conv-1"
        );

        // When
        String prompt = promptBuilder.buildPrompt(chunk);

        // Then — Table 9 key phrases
        assertTrue(prompt.contains("information déjà structurée"), "Should contain schema rule");
        assertTrue(prompt.contains("déclaration explicite la plus récente"), "Should contain conflict resolution");
        assertTrue(prompt.contains("strictement interdit"), "Should contain UPDATE merge rule");
        assertTrue(prompt.contains("ADD(chemin.complet, \"valeur en français\")"), "Should contain ADD format");
        assertTrue(prompt.contains("UPDATE(chemin.complet, \"nouvelle valeur fusionnée\")"), "Should contain UPDATE format");
        assertTrue(prompt.contains("DELETE(chemin.complet, None)"), "Should contain DELETE format");
        assertTrue(prompt.contains("NO_OP()"), "Should contain NO_OP format");
        assertTrue(prompt.contains("génère uniquement les opérations"), "Should contain trigger");
    }

    @Test
    void buildPrompt_containsJsonSchema() {
        // Given
        when(schemaLoader.loadSchema()).thenReturn(buildSmallTestTree());
        when(personaTreeGate.getNonEmptyLeaves()).thenReturn(Map.of());

        ConversationChunk chunk = new ConversationChunk(
                List.of(new ConversationPair("Bonjour", "Salut")),
                "conv-2"
        );

        // When
        String prompt = promptBuilder.buildPrompt(chunk);

        // Then — JSON schema block present
        assertTrue(prompt.contains("## Schéma PersonaTree"), "Should contain schema section");
        assertTrue(prompt.contains("1_Bio"), "Should contain root node in JSON");
        assertTrue(prompt.contains("Scalp_Hair"), "Should contain leaf node in JSON");
    }

    @Test
    void buildPrompt_overlaysCurrentValuesInSchema() {
        // Given
        when(schemaLoader.loadSchema()).thenReturn(buildSmallTestTree());
        Map<String, String> leaves = new LinkedHashMap<>();
        leaves.put("1_Bio.Physical_Appearance.Hair.Scalp_Hair", "cheveux bruns");
        when(personaTreeGate.getNonEmptyLeaves()).thenReturn(leaves);

        ConversationChunk chunk = new ConversationChunk(
                List.of(new ConversationPair("Test", "Ok")),
                "conv-3"
        );

        // When
        String prompt = promptBuilder.buildPrompt(chunk);

        // Then — value should be overlaid in JSON
        assertTrue(prompt.contains("cheveux bruns"), "Should contain overlaid value in JSON schema");
    }

    @Test
    void buildPrompt_includesDialogueSection() {
        // Given
        when(schemaLoader.loadSchema()).thenReturn(buildSmallTestTree());
        when(personaTreeGate.getNonEmptyLeaves()).thenReturn(Map.of());

        ConversationChunk chunk = new ConversationChunk(
                List.of(new ConversationPair("Je suis dev", "Cool !")),
                "conv-4"
        );

        // When
        String prompt = promptBuilder.buildPrompt(chunk);

        // Then
        assertTrue(prompt.contains("## Dialogue"));
        assertTrue(prompt.contains("Utilisateur: Je suis dev"));
        assertTrue(prompt.contains("Assistant: Cool !"));
    }

    @Test
    void buildPrompt_containsRoleAndObjective() {
        // Given
        when(schemaLoader.loadSchema()).thenReturn(buildSmallTestTree());
        when(personaTreeGate.getNonEmptyLeaves()).thenReturn(Map.of());

        ConversationChunk chunk = new ConversationChunk(
                List.of(new ConversationPair("Bonjour", "Salut")),
                "conv-5"
        );

        // When
        String prompt = promptBuilder.buildPrompt(chunk);

        // Then
        assertTrue(prompt.contains("générateur d'opérations pour un arbre de mémoire PersonaTree"));
        assertTrue(prompt.contains("caractéristiques personnalisées"));
    }
}
