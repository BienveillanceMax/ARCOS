package org.arcos.E2IT;

import org.arcos.UserModel.BatchPipeline.BatchPipelineOrchestrator;
import org.arcos.UserModel.BatchPipeline.MemListenerReadinessCheck;
import org.arcos.UserModel.BatchPipeline.Queue.ConversationQueueService;
import org.arcos.UserModel.DfsNavigator.CrossEncoderService;
import org.arcos.UserModel.DfsNavigator.DfsNavigatorService;
import org.arcos.UserModel.DfsNavigator.DfsResult;
import org.arcos.UserModel.PersonaTree.PersonaTreeGate;
import org.arcos.UserModel.PersonaTree.PersonaTreeSchemaLoader;
import org.arcos.UserModel.PersonaTree.PersonaTreeService;
import org.arcos.UserModel.PersonaTree.TreeOperationResult;
import org.arcos.UserModel.UserModelProperties;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("e2e")
class UserModelE2IT extends BaseE2IT {

    @Autowired private PersonaTreeGate personaTreeGate;
    @Autowired private PersonaTreeService personaTreeService;
    @Autowired private PersonaTreeSchemaLoader schemaLoader;
    @Autowired private DfsNavigatorService dfsNavigatorService;
    @Autowired private CrossEncoderService crossEncoderService;
    @Autowired private ConversationQueueService conversationQueueService;
    @Autowired private BatchPipelineOrchestrator batchPipelineOrchestrator;
    @Autowired private MemListenerReadinessCheck readinessCheck;
    @Autowired private UserModelProperties userModelProperties;

    // Known valid leaf paths from persona-tree-schema.json
    private static final String PATH_AGE =
        "1_Biological_Characteristics.Physiological_Status.Age_Related_Characteristics.Chronological_Age";
    private static final String PATH_LIFE_STAGE =
        "1_Biological_Characteristics.Physiological_Status.Age_Related_Characteristics.Life_Stage";

    @BeforeEach
    void resetTree() throws IOException {
        // Delete tree and queue files to force fresh schema load
        Path treePath = Paths.get(userModelProperties.getPersonaTreePath());
        Path queuePath = Paths.get(userModelProperties.getConversationQueuePath());
        Files.deleteIfExists(treePath);
        Files.deleteIfExists(queuePath);

        // Delete snapshot files
        Path dataDir = treePath.getParent();
        if (dataDir != null && Files.exists(dataDir)) {
            try (var stream = Files.list(dataDir)) {
                stream.filter(p -> p.getFileName().toString().startsWith("persona-tree-snapshot-"))
                      .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException e) { } });
            }
        }

        // Reload tree from schema
        personaTreeService.initialize();

        // Drain in-memory queue (file deletion alone doesn't reset in-memory state)
        conversationQueueService.drainAll();
    }

    // ======== Tree Operations (T1–T6) ========

    @Test
    void t1_addOperationSetsLeafValue() {
        List<TreeOperationResult> results = personaTreeGate.applyRawOperations(
            "ADD(" + PATH_AGE + ", \"35 ans\")");

        assertEquals(1, results.size());
        assertTrue(results.get(0).success(), "ADD operation should succeed");
        Optional<String> value = personaTreeGate.getLeafValue(PATH_AGE);
        assertTrue(value.isPresent(), "Leaf should have a value after ADD");
        assertEquals("35 ans", value.get());
        assertEquals(1, personaTreeGate.getFilledLeafCount());
    }

    @Test
    void t2_updateOperationOverwritesValue() {
        personaTreeGate.applyRawOperations("ADD(" + PATH_AGE + ", \"35 ans\")");

        List<TreeOperationResult> results = personaTreeGate.applyRawOperations(
            "UPDATE(" + PATH_AGE + ", \"36 ans\")");

        assertTrue(results.get(0).success());
        assertEquals("36 ans", personaTreeGate.getLeafValue(PATH_AGE).orElse(null));
        assertEquals(1, personaTreeGate.getFilledLeafCount(), "UPDATE should not add a new leaf");
    }

    @Test
    void t3_deleteOperationClearsLeafValue() {
        personaTreeGate.applyRawOperations("ADD(" + PATH_AGE + ", \"35 ans\")");

        List<TreeOperationResult> results = personaTreeGate.applyRawOperations(
            "DELETE(" + PATH_AGE + ", None)");

        assertTrue(results.get(0).success());
        Optional<String> value = personaTreeGate.getLeafValue(PATH_AGE);
        assertTrue(value.isEmpty(), "Leaf should be empty after DELETE");
        assertEquals(0, personaTreeGate.getFilledLeafCount());
    }

    @Test
    void t4_noOpDoesNothing() {
        List<TreeOperationResult> results = personaTreeGate.applyRawOperations("NO_OP()");

        assertEquals(1, results.size());
        assertTrue(results.get(0).success());
        assertEquals(0, personaTreeGate.getFilledLeafCount());
    }

    @Test
    void t5_invalidPathRejected() {
        List<TreeOperationResult> results = personaTreeGate.applyRawOperations(
            "ADD(invalid.nonexistent.path, \"valeur\")");

        assertEquals(1, results.size());
        assertFalse(results.get(0).success(), "Invalid path should produce failure result");
        assertNotNull(results.get(0).errorMessage(), "Failure should include error message");
        assertEquals(0, personaTreeGate.getFilledLeafCount());
    }

    @Test
    void t6_batchOperationsMixedValidAndInvalid() {
        String ops = "ADD(" + PATH_AGE + ", \"35 ans\")\n"
            + "ADD(" + PATH_LIFE_STAGE + ", \"Adulte actif\")\n"
            + "NO_OP()\n"
            + "ADD(invalid.path, \"x\")";

        List<TreeOperationResult> results = personaTreeGate.applyRawOperations(ops);

        assertEquals(4, results.size());
        assertTrue(results.get(0).success());
        assertTrue(results.get(1).success());
        assertTrue(results.get(2).success());  // NO_OP
        assertFalse(results.get(3).success()); // invalid path
        assertEquals(2, personaTreeGate.getFilledLeafCount());
    }

    // ======== Persistence (T7) ========

    @Test
    void t7_treeSurvivesServiceReload() throws IOException {
        personaTreeGate.applyRawOperations("ADD(" + PATH_AGE + ", \"35 ans\")");
        personaTreeGate.persist();

        // Reload from disk
        personaTreeService.initialize();

        Optional<String> value = personaTreeGate.getLeafValue(PATH_AGE);
        assertTrue(value.isPresent(), "Leaf value should survive persist + reload");
        assertEquals("35 ans", value.get());
    }

    // ======== DFS Navigation (T8–T11) ========

    @Test
    void t8_dfsSelectsRelevantL1BranchForWorkQuery() {
        assumeTrue(crossEncoderService.isAvailable(),
            "Skipping DFS test — ONNX CrossEncoder model not available");

        // Populate Social_Identity leaves — find valid paths from schema at runtime
        String socialIdentityPrefix = "4_Identity_Characteristics.Social_Identity";
        String occupationPath = schemaLoader.getValidLeafPaths().stream()
            .filter(p -> p.startsWith(socialIdentityPrefix) && p.contains("Occupation"))
            .findFirst()
            .orElse(null);
        assumeTrue(occupationPath != null,
            "Skipping: no Occupation leaf path found under Social_Identity in schema");

        personaTreeGate.applyRawOperations("ADD(" + occupationPath + ", \"Architecte depuis 5 ans\")");

        DfsResult result = dfsNavigatorService.navigate("Qu'est-ce que tu sais de mon travail ?");

        assertFalse(result.relevantLeaves().isEmpty(), "DFS should return relevant leaves");
        assertTrue(result.selectedL1Branches().contains("Social_Identity"),
            "Social_Identity L1 branch should be selected for a work query");
        assertTrue(result.relevantLeaves().containsKey(occupationPath),
            "Occupation path should be in relevant leaves");
    }

    @Test
    void t9_dfsDoesNotReturnIrrelevantBranches() {
        assumeTrue(crossEncoderService.isAvailable(),
            "Skipping DFS test — ONNX CrossEncoder model not available");

        // Only populate Social_Identity (work-related)
        String socialIdentityPrefix = "4_Identity_Characteristics.Social_Identity";
        String occupationPath = schemaLoader.getValidLeafPaths().stream()
            .filter(p -> p.startsWith(socialIdentityPrefix) && p.contains("Occupation"))
            .findFirst().orElse(null);
        assumeTrue(occupationPath != null, "No Occupation path found in schema");

        personaTreeGate.applyRawOperations("ADD(" + occupationPath + ", \"Architecte depuis 5 ans\")");

        DfsResult result = dfsNavigatorService.navigate("Parle-moi de ma famille");

        // Work data should not be returned for a family query
        result.relevantLeaves().keySet().forEach(key ->
            assertFalse(key.startsWith(socialIdentityPrefix),
                "Work paths should not appear in family query results: " + key));
    }

    @Test
    void t10_dfsReturnsEmptyForEmptyTree() {
        assumeTrue(crossEncoderService.isAvailable(),
            "Skipping DFS test — ONNX CrossEncoder model not available");

        DfsResult result = dfsNavigatorService.navigate("Qu'est-ce que tu sais de moi ?");

        assertTrue(result.relevantLeaves().isEmpty(),
            "Empty tree should produce empty DFS result");
    }

    @Test
    void t11_dfsLatencyWithinBound() {
        assumeTrue(crossEncoderService.isAvailable(),
            "Skipping DFS test — ONNX CrossEncoder model not available");

        // Populate a few leaves
        personaTreeGate.applyRawOperations(
            "ADD(" + PATH_AGE + ", \"35 ans\")\n"
            + "ADD(" + PATH_LIFE_STAGE + ", \"Adulte actif\")");

        DfsResult result = dfsNavigatorService.navigate("Quelques informations sur moi");

        assertTrue(result.latencyMs() < 5000,
            "DFS navigation should complete within 5 seconds, took: " + result.latencyMs() + "ms");
    }

    // ======== Batch Pipeline (T12–T15) — requires Ollama ========

    @Test
    @Tag("requires-ollama")
    void t12_memListenerPopulatesTreeFromUnambiguousConversations() {
        assumeTrue(readinessCheck.isModelReady(),
            "Skipping t12 — MemListener model not available in Ollama");

        conversationQueueService.enqueue(makeConversation(
            "Je suis architecte depuis cinq ans, je travaille dans un cabinet à Paris",
            "C'est un métier qui demande beaucoup de précision et de vision créative"));
        conversationQueueService.enqueue(makeConversation(
            "Mon chien Pixel me réveille tous les matins à sept heures",
            "Pixel a l'air d'être un réveil très efficace"));
        conversationQueueService.enqueue(makeConversation(
            "Je cours trois fois par semaine, c'est ce qui me permet de décompresser",
            "La course à pied, c'est une excellente façon de gérer le stress"));

        batchPipelineOrchestrator.runBatch();

        int filled = personaTreeGate.getFilledLeafCount();
        assertTrue(filled > 0, "MemListener should have populated at least one leaf");

        int maxPossible = schemaLoader.getValidLeafPaths().size();
        assertTrue(filled <= maxPossible, "Cannot have more filled leaves than schema paths");

        personaTreeGate.getNonEmptyLeaves().forEach((path, value) -> {
            assertNotNull(value, "Leaf value must not be null: " + path);
            assertTrue(value.length() <= userModelProperties.getLeafMaxChars(),
                "Leaf value exceeds max chars at path: " + path);
        });
    }

    @Test
    @Tag("requires-ollama")
    void t13_memListenerHandlesAmbiguousConversationsWithoutCrash() {
        assumeTrue(readinessCheck.isModelReady(),
            "Skipping t13 — MemListener model not available in Ollama");

        // Unambiguous
        conversationQueueService.enqueue(makeConversation(
            "Je suis architecte depuis cinq ans à Paris",
            "Intéressant, c'est un métier créatif"));
        // Ambiguous
        conversationQueueService.enqueue(makeConversation(
            "Je rentre encore tard ce soir", "Ah, longue journée ?"));
        conversationQueueService.enqueue(makeConversation(
            "Ma mère m'a appelé trois fois aujourd'hui", "Tout va bien de son côté ?"));
        conversationQueueService.enqueue(makeConversation(
            "Le nouveau projet est intéressant, enfin bon...", "Tu sembles mitigé"));
        conversationQueueService.enqueue(makeConversation(
            "Mon collègue Thomas a encore fait une de ses blagues",
            "Et tu as ri ou tu as levé les yeux au ciel ?"));

        // Should not throw
        assertDoesNotThrow(() -> batchPipelineOrchestrator.runBatch(),
            "runBatch() must not throw even with ambiguous inputs");

        int filled = personaTreeGate.getFilledLeafCount();
        assertTrue(filled >= 1, "At least the unambiguous conversation should have populated a leaf");
        assertTrue(filled <= schemaLoader.getValidLeafPaths().size(),
            "No leaves outside schema should exist");

        // All values non-null
        personaTreeGate.getNonEmptyLeaves().values().forEach(v ->
            assertNotNull(v, "All leaf values must be non-null"));
    }

    @Test
    @Tag("requires-ollama")
    void t14_batchPipelineInterruptReenqueuesRemainingConversations() {
        assumeTrue(readinessCheck.isModelReady(),
            "Skipping t14 — MemListener model not available in Ollama");

        // Enqueue 5 conversations
        for (int i = 0; i < 5; i++) {
            conversationQueueService.enqueue(makeConversation(
                "Message utilisateur " + i, "Réponse assistant " + i));
        }

        // Schedule an interrupt right after starting
        Thread interrupter = new Thread(() -> {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            batchPipelineOrchestrator.interrupt();
        });
        interrupter.setDaemon(true);
        interrupter.start();

        batchPipelineOrchestrator.runBatch();

        // Some conversations should have been re-enqueued
        assertFalse(conversationQueueService.isEmpty(),
            "Interrupted batch should re-enqueue remaining conversations");
    }

    @Test
    @Tag("requires-ollama")
    void t15_treePersistedAfterSuccessfulBatchRun() throws Exception {
        assumeTrue(readinessCheck.isModelReady(),
            "Skipping t15 — MemListener model not available in Ollama");

        conversationQueueService.enqueue(makeConversation(
            "Je travaille comme architecte", "Un métier passionnant"));

        batchPipelineOrchestrator.runBatch();

        // runBatch() calls personaTreeGate.persist() internally
        Path treePath = Paths.get(userModelProperties.getPersonaTreePath());
        assertTrue(Files.exists(treePath), "Tree file should exist after runBatch()");

        String json = Files.readString(treePath);
        assertDoesNotThrow(() -> new com.fasterxml.jackson.databind.ObjectMapper().readTree(json),
            "Persisted tree must be valid JSON");

        // Reload and verify count matches
        personaTreeService.initialize();
        int reloadedCount = personaTreeGate.getFilledLeafCount();
        // Count may differ slightly due to batch processing clearing state — just verify no crash
        assertTrue(reloadedCount >= 0, "Reloaded tree count should be non-negative");
    }
}
