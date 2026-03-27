package org.arcos.E2IT;

import org.arcos.EventBus.Events.Event;
import org.arcos.EventBus.Events.EventType;
import org.arcos.UserModel.DfsNavigator.CrossEncoderService;
import org.arcos.UserModel.PersonaTree.PersonaTreeGate;
import org.arcos.UserModel.PersonaTree.PersonaTreeSchemaLoader;
import org.arcos.UserModel.PersonaTree.PersonaTreeService;
import org.arcos.UserModel.UserModelProperties;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * E2E — Sprint 10 E8: UserModel injection.
 *
 * Proves that PersonaTree data flows through the full conversation chain:
 *   PersonaTree populated → WAKEWORD question → PromptBuilder calls DfsNavigator
 *   → DFS scores L1 branches via ONNX cross-encoder → selects relevant leaves
 *   → UserContextFormatter injects markdown into system prompt → LLM (Mistral)
 *   → TTS response reflects PersonaTree knowledge.
 *
 * Prerequisites: Qdrant, Mistral API, ONNX cross-encoder model.
 */
@Tag("e2e")
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class UserModelInjectionE2IT extends BaseE2IT {

    @Autowired private PersonaTreeGate personaTreeGate;
    @Autowired private PersonaTreeService personaTreeService;
    @Autowired private PersonaTreeSchemaLoader schemaLoader;
    @Autowired private CrossEncoderService crossEncoderService;
    @Autowired private UserModelProperties userModelProperties;

    @BeforeEach
    void resetTreeAndPopulate() throws IOException {
        // Start with a clean tree
        Path treePath = Paths.get(userModelProperties.getPersonaTreePath());
        Files.deleteIfExists(treePath);
        personaTreeService.initialize();
    }

    @Test
    void t1_responseReflectsPersonaTreeKnowledge() {
        assumeTrue(crossEncoderService.isAvailable(),
            "Skipping — ONNX CrossEncoder model not available");

        // Populate the tree with distinctive, verifiable data
        String hobbyPath = schemaLoader.getValidLeafPaths().stream()
            .filter(p -> p.toLowerCase().contains("hobby") || p.toLowerCase().contains("leisure")
                || p.toLowerCase().contains("interest"))
            .findFirst().orElse(null);
        assumeTrue(hobbyPath != null,
            "Skipping — no hobby/leisure/interest leaf path found in schema");

        personaTreeGate.applyRawOperations(
            "ADD(" + hobbyPath + ", \"Passionné de cuisine italienne, "
            + "fait des pâtes fraîches maison chaque week-end\")");
        assertTrue(personaTreeGate.getFilledLeafCount() >= 1,
            "Tree should have at least one populated leaf");

        // Ask the LLM a question that should trigger DFS to inject the hobby data
        orchestrator.dispatch(new Event<>(EventType.WAKEWORD,
            "Qu'est-ce que tu sais sur moi ?", "test"));

        Awaitility.await().atMost(Duration.ofSeconds(30))
            .until(() -> mockTTS.hasSpoken());

        String allSpoken = mockTTS.getAllSpokenLower();

        // The response should mention the PersonaTree data
        assertTrue(
            allSpoken.matches(".*(?:cuisine|italien|pâtes|culinaire|cuisiner).*"),
            "LLM response should reflect PersonaTree hobby data (cuisine italienne), got: " + allSpoken);
    }

    @Test
    void t2_emptyPersonaTreeDoesNotCrash() {
        // Tree is already empty after resetTreeAndPopulate()
        assertEquals(0, personaTreeGate.getFilledLeafCount());

        orchestrator.dispatch(new Event<>(EventType.WAKEWORD,
            "Bonjour, comment vas-tu ?", "test"));

        Awaitility.await().atMost(Duration.ofSeconds(20))
            .until(() -> mockTTS.hasSpoken());

        // Should produce a normal response — no crash, no error
        assertFalse(mockTTS.getSpokenTexts().isEmpty(),
            "Empty PersonaTree should not prevent normal conversation");
        mockTTS.getSpokenTexts().forEach(t ->
            assertFalse(t.isBlank(), "Spoken text should not be blank"));
    }
}
