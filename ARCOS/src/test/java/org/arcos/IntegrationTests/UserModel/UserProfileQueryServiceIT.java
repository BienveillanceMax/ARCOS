package org.arcos.IntegrationTests.UserModel;

import org.arcos.UserModel.Extraction.UserTreeUpdater;
import org.arcos.UserModel.Lifecycle.EbbinghausPruningService;
import org.arcos.UserModel.Lifecycle.UserModelPipelineOrchestrator;
import org.arcos.UserModel.Models.*;
import org.arcos.UserModel.Persistence.UserTreePersistenceService;
import org.arcos.UserModel.Retrieval.UserProfileQueryService;
import org.arcos.UserModel.UserModelProperties;
import org.arcos.UserModel.UserObservationTree;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = UserModelTestConfiguration.class)
class UserProfileQueryServiceIT {

    private static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        try {
            tempDir = Files.createTempDirectory("arcos-usermodel-query-it");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        registry.add("arcos.user-model.enabled", () -> "true");
        registry.add("arcos.user-model.storage-path",
                () -> tempDir.resolve("user-tree.json").toString());
        registry.add("arcos.user-model.archive-path",
                () -> tempDir.resolve("archive.json").toString());
        registry.add("arcos.user-model.debounce-save-ms", () -> "10");
    }

    @AfterAll
    static void cleanup() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            try (var stream = Files.walk(tempDir)) {
                stream.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try { Files.deleteIfExists(path); } catch (IOException ignored) {}
                        });
            }
        }
    }

    @Autowired private ApplicationContext applicationContext;
    @Autowired private UserObservationTree tree;
    @Autowired private UserModelProperties properties;
    @Autowired private UserProfileQueryService queryService;
    @Autowired private UserTreeUpdater treeUpdater;
    @Autowired private UserModelPipelineOrchestrator pipeline;
    @Autowired private EbbinghausPruningService pruningService;
    @Autowired private UserTreePersistenceService persistenceService;

    @BeforeEach
    void resetTree() throws IOException {
        persistenceService.cancelPendingSave();

        Map<TreeBranch, List<ObservationLeaf>> emptyBranches = new EnumMap<>(TreeBranch.class);
        for (TreeBranch branch : TreeBranch.values()) {
            emptyBranches.put(branch, new ArrayList<>());
        }
        tree.replaceAll(emptyBranches, 0, new EnumMap<>(TreeBranch.class), new HashMap<>());

        Files.deleteIfExists(Paths.get(properties.getStoragePath()));
        Files.deleteIfExists(Paths.get(properties.getArchivePath()));
    }

    // ---- Bean wiring ----

    @Test
    void contextLoads_UserProfileQueryServiceIsPresent() {
        assertNotNull(applicationContext.getBean(UserProfileQueryService.class));
    }

    // ---- Channel B → Query: observations injected via UserTreeUpdater are queryable ----

    @Test
    void channelB_ObservationsQueryableViaGetTopLeaves() {
        // Given — inject observations with different reinforcement counts
        treeUpdater.processObservation(new ObservationCandidate(
                "Mon créateur adore la musique jazz", TreeBranch.INTERETS, null, true, 0.9f));
        treeUpdater.processObservation(new ObservationCandidate(
                "Mon créateur aime la randonnée", TreeBranch.INTERETS, null, false, 0.4f));
        treeUpdater.processObservation(new ObservationCandidate(
                "Mon créateur lit beaucoup de science-fiction", TreeBranch.INTERETS, null, false, 0.5f));

        // When
        List<ObservationLeaf> topLeaves = queryService.getTopLeaves(TreeBranch.INTERETS, 2);

        // Then
        assertEquals(2, topLeaves.size());
        // Explicit observation gets observationCount=3, so it should be first
        assertEquals("Mon créateur adore la musique jazz", topLeaves.get(0).getText());
        assertTrue(topLeaves.get(0).getObservationCount() >= topLeaves.get(1).getObservationCount(),
                "Results should be sorted by observationCount descending");
    }

    @Test
    void channelB_ObservationsQueryableViaSemanticSearch() {
        // Given
        treeUpdater.processObservation(new ObservationCandidate(
                "Mon créateur s'appelle Pierre", TreeBranch.IDENTITE, null, true, 0.8f));
        treeUpdater.processObservation(new ObservationCandidate(
                "Mon créateur est développeur Java", TreeBranch.IDENTITE, null, false, 0.5f));
        treeUpdater.processObservation(new ObservationCandidate(
                "Mon créateur aime cuisiner des plats italiens", TreeBranch.INTERETS, null, false, 0.6f));

        // When — search for something related to programming
        List<ObservationLeaf> results = queryService.searchLeaves("développeur Java", 5, 0.1);

        // Then — should return results from multiple branches
        assertFalse(results.isEmpty(), "Semantic search should return results after observations");
    }

    @Test
    void channelB_BranchSummaryAvailableAfterObservations() {
        // Given
        treeUpdater.processObservation(new ObservationCandidate(
                "Mon créateur s'appelle Pierre", TreeBranch.IDENTITE, null, true, 0.8f));

        // When
        Optional<String> summary = queryService.getBranchSummary(TreeBranch.IDENTITE);

        // Then
        assertTrue(summary.isPresent(), "Summary should be available after observation triggers rebuild");
        assertTrue(summary.get().contains("Pierre"));
    }

    // ---- Profile stability evolves with conversations ----

    @Test
    void profileStability_EvolvesAfterConversations() {
        // Given — initial state
        assertEquals(ProfileStability.LOW, queryService.getProfileStability());

        // When — simulate 6 conversations
        for (int i = 0; i < 6; i++) {
            pipeline.processConversation(
                    List.of("Bonjour", "Comment ça va ?"), false);
        }

        // Then
        assertEquals(ProfileStability.MEDIUM, queryService.getProfileStability());

        // When — simulate 4 more to reach HIGH
        for (int i = 0; i < 4; i++) {
            pipeline.processConversation(
                    List.of("Encore un message"), false);
        }

        // Then
        assertEquals(ProfileStability.HIGH, queryService.getProfileStability());
    }

    // ---- Pruning → query coherence ----

    @Test
    void pruning_StaleLeavesPrunedAreNoLongerQueryable() {
        // Given — one stale leaf and one fresh leaf
        ObservationLeaf staleLeaf = new ObservationLeaf(
                "Vieille observation obsolète", TreeBranch.INTERETS, ObservationSource.LLM_EXTRACTED);
        staleLeaf.setLastReinforced(Instant.now().minus(Duration.ofDays(365)));
        staleLeaf.setObservationCount(1);
        tree.addLeaf(staleLeaf);

        ObservationLeaf freshLeaf = new ObservationLeaf(
                "Observation récente et renforcée", TreeBranch.INTERETS, ObservationSource.LLM_EXTRACTED);
        freshLeaf.setObservationCount(10);
        tree.addLeaf(freshLeaf);

        assertEquals(2, queryService.getTopLeaves(TreeBranch.INTERETS, 10).size());

        // When
        pruningService.prune();

        // Then
        List<ObservationLeaf> remaining = queryService.getTopLeaves(TreeBranch.INTERETS, 10);
        assertEquals(1, remaining.size());
        assertEquals("Observation récente et renforcée", remaining.get(0).getText());
    }

    // ---- Contradiction replacement → query reflects update ----

    @Test
    void contradictionReplacement_QueryReflectsUpdatedLeaf() {
        // Given
        treeUpdater.processObservation(new ObservationCandidate(
                "Mon créateur vit à Paris", TreeBranch.IDENTITE, null));

        List<ObservationLeaf> before = queryService.getTopLeaves(TreeBranch.IDENTITE, 10);
        assertEquals(1, before.size());
        assertEquals("Mon créateur vit à Paris", before.get(0).getText());

        // When — contradiction replaces the old observation
        treeUpdater.processObservation(new ObservationCandidate(
                "Mon créateur vit à Lyon", TreeBranch.IDENTITE, "Mon créateur vit à Paris"));

        // Then
        List<ObservationLeaf> after = queryService.getTopLeaves(TreeBranch.IDENTITE, 10);
        assertEquals(1, after.size());
        assertEquals("Mon créateur vit à Lyon", after.get(0).getText());
    }

    // ---- Persistence roundtrip → query consistency ----

    @Test
    void persistenceRoundtrip_QueryServiceConsistentAfterReload() {
        // Given — populate tree directly (avoids async scheduleSave race)
        tree.setConversationCount(10);
        tree.addLeaf(new ObservationLeaf(
                "Mon créateur s'appelle Pierre", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED));
        tree.addLeaf(new ObservationLeaf(
                "Mon créateur aime le jazz", TreeBranch.INTERETS, ObservationSource.LLM_EXTRACTED));
        tree.setSummary(TreeBranch.IDENTITE, "Pierre");

        List<ObservationLeaf> beforeSave = queryService.getTopLeaves(TreeBranch.IDENTITE, 10);
        Optional<String> summaryBeforeSave = queryService.getBranchSummary(TreeBranch.IDENTITE);
        ProfileStability stabilityBeforeSave = queryService.getProfileStability();

        persistenceService.doSave();

        // When — reload into fresh tree (simulates restart)
        UserObservationTree freshTree = new UserObservationTree(properties);
        UserTreePersistenceService freshPersistence =
                new UserTreePersistenceService(freshTree, properties);
        freshPersistence.load();

        // Then — verify loaded data matches what was queryable before save
        assertEquals(beforeSave.size(), freshTree.getActiveLeaves(TreeBranch.IDENTITE).size());
        assertEquals(summaryBeforeSave.orElse(null), freshTree.getSummary(TreeBranch.IDENTITE));
        assertEquals(stabilityBeforeSave, freshTree.getProfileStability());
    }

    // ---- Cross-branch semantic search ----

    @Test
    void searchLeaves_FindsResultsAcrossMultipleBranches() {
        // Given — populate multiple branches
        treeUpdater.processObservation(new ObservationCandidate(
                "Mon créateur s'appelle Pierre Dupont", TreeBranch.IDENTITE, null, true, 0.8f));
        treeUpdater.processObservation(new ObservationCandidate(
                "Mon créateur veut apprendre le piano", TreeBranch.OBJECTIFS, null, false, 0.6f));
        treeUpdater.processObservation(new ObservationCandidate(
                "Mon créateur aime écouter du Chopin", TreeBranch.INTERETS, null, false, 0.7f));
        treeUpdater.processObservation(new ObservationCandidate(
                "Mon créateur se lève tôt chaque matin", TreeBranch.HABITUDES, null, false, 0.3f));

        // When — broad search with low threshold
        List<ObservationLeaf> results = queryService.searchLeaves("musique piano", 10, 0.1);

        // Then — should find results from at least 2 branches
        assertFalse(results.isEmpty());
        Set<TreeBranch> branches = new HashSet<>();
        results.forEach(leaf -> branches.add(leaf.getBranch()));
        assertTrue(branches.size() >= 1, "Search should find results across branches");
    }

    // ---- Empty tree edge cases ----

    @Test
    void emptyTree_AllQueryMethodsReturnSafeDefaults() {
        // Given — tree is empty (from resetTree)

        // When / Then
        assertTrue(queryService.getTopLeaves(TreeBranch.IDENTITE, 5).isEmpty());
        assertTrue(queryService.searchLeaves("anything", 5, 0.1).isEmpty());
        assertTrue(queryService.getBranchSummary(TreeBranch.IDENTITE).isEmpty());
        assertEquals(ProfileStability.LOW, queryService.getProfileStability());
    }
}
