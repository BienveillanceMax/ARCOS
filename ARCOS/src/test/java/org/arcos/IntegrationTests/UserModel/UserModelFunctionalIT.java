package org.arcos.IntegrationTests.UserModel;

import org.arcos.UserModel.Embedding.LocalEmbeddingService;
import org.arcos.UserModel.Extraction.UserTreeUpdater;
import org.arcos.UserModel.Lifecycle.EbbinghausPruningService;
import org.arcos.UserModel.Lifecycle.UserModelPipelineOrchestrator;
import org.arcos.UserModel.Models.*;
import org.arcos.UserModel.Persistence.UserTreePersistenceService;
import org.arcos.UserModel.Retrieval.BranchSummaryBuilder;
import org.arcos.UserModel.Retrieval.UserModelRetrievalService;
import org.arcos.UserModel.UserModelAutoConfiguration;
import org.arcos.UserModel.UserModelProperties;
import org.arcos.UserModel.UserObservationTree;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;
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
class UserModelFunctionalIT {

    private static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        try {
            tempDir = Files.createTempDirectory("arcos-usermodel-func-it");
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
    @Autowired private LocalEmbeddingService embeddingService;
    @Autowired private UserTreePersistenceService persistenceService;
    @Autowired private BranchSummaryBuilder summaryBuilder;
    @Autowired private UserTreeUpdater treeUpdater;
    @Autowired private UserModelRetrievalService retrievalService;
    @Autowired private UserModelPipelineOrchestrator pipeline;
    @Autowired private EbbinghausPruningService pruningService;

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
        Files.deleteIfExists(Paths.get(properties.getStoragePath() + ".tmp"));
        Files.deleteIfExists(Paths.get(properties.getArchivePath() + ".tmp"));
    }

    // ---- Test 1: All expected beans are wired ----

    @Test
    void contextLoads_AllUserModelBeansPresent() {
        assertNotNull(applicationContext.getBean(UserObservationTree.class));
        assertNotNull(applicationContext.getBean(UserModelProperties.class));
        assertNotNull(applicationContext.getBean(LocalEmbeddingService.class));
        assertNotNull(applicationContext.getBean(UserTreePersistenceService.class));
        assertNotNull(applicationContext.getBean(BranchSummaryBuilder.class));
        assertNotNull(applicationContext.getBean(UserTreeUpdater.class));
        assertNotNull(applicationContext.getBean(UserModelRetrievalService.class));
        assertNotNull(applicationContext.getBean(UserModelPipelineOrchestrator.class));
        assertNotNull(applicationContext.getBean(EbbinghausPruningService.class));
    }

    // ---- Test 2: Channel A end-to-end ----

    @Test
    void channelA_FullPipeline_ConversationToObservationLeaves() {
        assertEquals(0, tree.getConversationCount());
        assertEquals(ProfileStability.LOW, tree.getProfileStability());

        for (int i = 0; i < 6; i++) {
            pipeline.processConversation(
                    List.of("Bonjour comment vas-tu ?",
                            "J'aimerais savoir quelque chose",
                            "Merci beaucoup"),
                    false);
        }

        assertEquals(6, tree.getConversationCount());
        assertEquals(ProfileStability.MEDIUM, tree.getProfileStability());
        assertFalse(tree.getHeuristicBaselines().isEmpty(),
                "Heuristic baselines should be populated after conversations");
    }

    // ---- Test 3: Channel B end-to-end ----

    @Test
    void channelB_ObservationInjection_UpdatesTreeAndSummary() {
        ObservationCandidate explicit = new ObservationCandidate(
                "Mon créateur s'appelle Pierre", TreeBranch.IDENTITE, null, true, 0.8f);
        ObservationCandidate implicit = new ObservationCandidate(
                "Mon créateur est développeur Java", TreeBranch.IDENTITE, null, false, 0.5f);

        treeUpdater.processObservation(explicit);
        treeUpdater.processObservation(implicit);

        List<ObservationLeaf> identityLeaves = tree.getActiveLeaves(TreeBranch.IDENTITE);
        assertEquals(2, identityLeaves.size());
        assertNotNull(tree.getSummary(TreeBranch.IDENTITE),
                "Summary should be rebuilt after observations");

        ObservationLeaf pierreLeaf = identityLeaves.stream()
                .filter(l -> l.getText().contains("Pierre"))
                .findFirst()
                .orElse(null);
        assertNotNull(pierreLeaf);
        assertEquals(3, pierreLeaf.getObservationCount(),
                "Explicit observation should have observationCount=3");
        assertEquals(0.8f, pierreLeaf.getEmotionalImportance(), 0.01f,
                "Explicit observation should have emotionalImportance=0.8");
    }

    // ---- Test 4: Cold-start gate then populated retrieval ----

    @Test
    void retrieval_ColdStartGate_ThenProfileAfterMinConversations() {
        tree.setConversationCount(1);
        UserProfileContext coldContext = retrievalService.retrieveUserContext("Bonjour");
        assertTrue(coldContext.isEmpty(), "Should return empty context during cold start");
        assertEquals(1, coldContext.conversationCount());

        tree.setConversationCount(5);
        treeUpdater.processObservation(new ObservationCandidate(
                "Pierre, développeur Java senior", TreeBranch.IDENTITE, null, true, 0.8f));
        treeUpdater.processObservation(new ObservationCandidate(
                "S'exprime de manière concise", TreeBranch.COMMUNICATION, null, true, 0.8f));

        UserProfileContext warmContext = retrievalService.retrieveUserContext("Bonjour");
        assertFalse(warmContext.isEmpty(), "Should return populated context after min conversations");
        assertNotNull(warmContext.identitySummary());
        assertNotNull(warmContext.communicationSummary());
        assertEquals(5, warmContext.conversationCount());
    }

    // ---- Test 5: Contradiction replacement with archiving ----

    @Test
    void contradictionReplacement_ArchivesOldAndUpdatesTree() {
        treeUpdater.processObservation(new ObservationCandidate(
                "Mon créateur vit à Paris", TreeBranch.IDENTITE, null));

        assertEquals(1, tree.getActiveLeaves(TreeBranch.IDENTITE).size());

        treeUpdater.processObservation(new ObservationCandidate(
                "Mon créateur vit à Lyon", TreeBranch.IDENTITE, "Mon créateur vit à Paris"));

        assertEquals(1, tree.getActiveLeaves(TreeBranch.IDENTITE).size());
        assertEquals("Mon créateur vit à Lyon",
                tree.getActiveLeaves(TreeBranch.IDENTITE).get(0).getText());

        Path archivePath = Paths.get(properties.getArchivePath());
        assertTrue(Files.exists(archivePath),
                "Archive file should exist after contradiction replacement");
    }

    // ---- Test 6: Persistence save + load round-trip ----

    @Test
    void persistenceRoundtrip_SpringManagedSaveAndLoad() {
        tree.setConversationCount(7);
        tree.addLeaf(new ObservationLeaf(
                "Mon créateur s'appelle Pierre", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED));
        tree.addLeaf(new ObservationLeaf(
                "Mon créateur est concis", TreeBranch.COMMUNICATION, ObservationSource.HEURISTIC));
        tree.setSummary(TreeBranch.IDENTITE, "Pierre");
        tree.setHeuristicBaselines(Map.of("avg_word_count", 12.5));

        persistenceService.doSave();

        assertTrue(Files.exists(Paths.get(properties.getStoragePath())),
                "Storage file should exist after save");

        UserObservationTree freshTree = new UserObservationTree(properties);
        UserTreePersistenceService freshPersistence =
                new UserTreePersistenceService(freshTree, properties);
        freshPersistence.load();

        assertEquals(7, freshTree.getConversationCount());
        assertEquals(ProfileStability.MEDIUM, freshTree.getProfileStability());
        assertEquals(2, freshTree.getAllActiveLeaves().size());
        assertEquals("Pierre", freshTree.getSummary(TreeBranch.IDENTITE));
        assertEquals(12.5, freshTree.getHeuristicBaselines().get("avg_word_count"));
    }

    // ---- Test 7: Ebbinghaus pruning removes stale leaves ----

    @Test
    void pruning_RemovesLowRetentionLeaves() {
        ObservationLeaf oldLeaf = new ObservationLeaf(
                "Vieille observation obsolète", TreeBranch.INTERETS, ObservationSource.LLM_EXTRACTED);
        oldLeaf.setLastReinforced(Instant.now().minus(Duration.ofDays(365)));
        oldLeaf.setObservationCount(1);
        tree.addLeaf(oldLeaf);

        ObservationLeaf freshLeaf = new ObservationLeaf(
                "Observation récente", TreeBranch.INTERETS, ObservationSource.LLM_EXTRACTED);
        freshLeaf.setObservationCount(5);
        tree.addLeaf(freshLeaf);

        assertEquals(2, tree.getActiveLeaves(TreeBranch.INTERETS).size());

        pruningService.prune();

        List<ObservationLeaf> remaining = tree.getActiveLeaves(TreeBranch.INTERETS);
        assertEquals(1, remaining.size());
        assertEquals("Observation récente", remaining.get(0).getText());

        Path archivePath = Paths.get(properties.getArchivePath());
        assertTrue(Files.exists(archivePath),
                "Archive file should exist after pruning");
    }

    // ---- Test 8: Disabled module creates no beans ----

    @Test
    void disabledModule_NoBeans() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("arcos.user-model.enabled", "false");

        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.setEnvironment(env);
        ctx.register(UserModelAutoConfiguration.class);
        ctx.refresh();

        assertFalse(ctx.containsBean("userObservationTree"),
                "UserObservationTree should not exist when module disabled");
        assertFalse(ctx.containsBean("userTreePersistenceService"),
                "UserTreePersistenceService should not exist when module disabled");
        assertFalse(ctx.containsBean("userTreeUpdater"),
                "UserTreeUpdater should not exist when module disabled");
        assertFalse(ctx.containsBean("userModelPipelineOrchestrator"),
                "UserModelPipelineOrchestrator should not exist when module disabled");
        assertFalse(ctx.containsBean("userModelRetrievalService"),
                "UserModelRetrievalService should not exist when module disabled");
        assertFalse(ctx.containsBean("ebbinghausPruningService"),
                "EbbinghausPruningService should not exist when module disabled");

        ctx.close();
    }
}
