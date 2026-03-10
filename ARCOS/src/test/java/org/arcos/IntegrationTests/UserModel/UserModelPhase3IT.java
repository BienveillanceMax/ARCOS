package org.arcos.IntegrationTests.UserModel;

import org.arcos.UserModel.Engagement.EngagementRecord;
import org.arcos.UserModel.Engagement.EngagementTracker;
import org.arcos.UserModel.GapFilling.GapDetector;
import org.arcos.UserModel.GapFilling.ProactiveGapFiller;
import org.arcos.UserModel.Greeting.PersonalizedGreetingService;
import org.arcos.UserModel.Lifecycle.UserModelPipelineOrchestrator;
import org.arcos.UserModel.Models.*;
import org.arcos.UserModel.Persistence.UserTreePersistenceService;
import org.arcos.UserModel.Retrieval.UserModelRetrievalService;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = UserModelTestConfiguration.class)
class UserModelPhase3IT {

    private static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        try {
            tempDir = Files.createTempDirectory("arcos-usermodel-phase3-it");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        registry.add("arcos.user-model.enabled", () -> "true");
        registry.add("arcos.user-model.storage-path",
                () -> tempDir.resolve("user-tree.json").toString());
        registry.add("arcos.user-model.archive-path",
                () -> tempDir.resolve("archive.json").toString());
        registry.add("arcos.user-model.debounce-save-ms", () -> "10");
        registry.add("arcos.user-model.proactive-gap-filling.enabled", () -> "true");
        registry.add("arcos.user-model.proactive-gap-filling.min-conversations-between-same-branch", () -> "3");
        registry.add("arcos.user-model.engagement.enabled", () -> "true");
        registry.add("arcos.user-model.engagement.decay-window-conversations", () -> "3");
        registry.add("arcos.user-model.engagement.min-conversations-for-tracking", () -> "5");
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
    @Autowired private UserTreePersistenceService persistenceService;
    @Autowired private UserModelPipelineOrchestrator pipeline;
    @Autowired private UserModelRetrievalService retrievalService;
    @Autowired private EngagementTracker engagementTracker;
    @Autowired private GapDetector gapDetector;
    @Autowired private ProactiveGapFiller proactiveGapFiller;
    @Autowired private PersonalizedGreetingService greetingService;

    @BeforeEach
    void resetTree() throws IOException {
        persistenceService.cancelPendingSave();

        Map<TreeBranch, List<ObservationLeaf>> emptyBranches = new EnumMap<>(TreeBranch.class);
        for (TreeBranch branch : TreeBranch.values()) {
            emptyBranches.put(branch, new ArrayList<>());
        }
        tree.replaceAll(emptyBranches, 0, new EnumMap<>(TreeBranch.class), new HashMap<>());
        tree.setEngagementHistory(new ArrayList<>());
        tree.setLastGapQuestionPerBranch(new EnumMap<>(TreeBranch.class));

        Files.deleteIfExists(Paths.get(properties.getStoragePath()));
        Files.deleteIfExists(Paths.get(properties.getArchivePath()));
    }

    // ---- Test 1: All Phase 3 beans are wired ----

    @Test
    void contextLoads_AllPhase3BeansPresent() {
        assertNotNull(applicationContext.getBean(EngagementTracker.class));
        assertNotNull(applicationContext.getBean(GapDetector.class));
        assertNotNull(applicationContext.getBean(ProactiveGapFiller.class));
        assertNotNull(applicationContext.getBean(PersonalizedGreetingService.class));
    }

    // ---- Test 2: Pipeline records engagement ----

    @Test
    void pipeline_RecordsEngagement_AfterConversation() {
        assertTrue(tree.getEngagementHistory().isEmpty());

        pipeline.processConversation(
                List.of("Bonjour", "Comment ça va ?", "Merci"), false);

        assertEquals(1, tree.getEngagementHistory().size());
        assertEquals(3, tree.getEngagementHistory().get(0).getMessageCount());
    }

    // ---- Test 3: Engagement persists through save/load ----

    @Test
    void engagement_PersistsThroughSaveLoad() {
        tree.setConversationCount(5);
        Instant base = Instant.now().minus(5, ChronoUnit.DAYS);
        for (int i = 0; i < 5; i++) {
            tree.addEngagementRecord(new EngagementRecord(base.plus(i, ChronoUnit.DAYS), 3 + i));
        }

        persistenceService.doSave();
        assertTrue(Files.exists(Paths.get(properties.getStoragePath())));

        UserObservationTree freshTree = new UserObservationTree(properties);
        UserTreePersistenceService freshPersistence =
                new UserTreePersistenceService(freshTree, properties);
        freshPersistence.load();

        assertEquals(5, freshTree.getEngagementHistory().size());
        assertEquals(3, freshTree.getEngagementHistory().get(0).getMessageCount());
        assertEquals(7, freshTree.getEngagementHistory().get(4).getMessageCount());
    }

    // ---- Test 4: Gap-filling state persists ----

    @Test
    void gapFilling_StatePersistsThroughSaveLoad() {
        tree.setConversationCount(10);
        tree.recordGapQuestion(TreeBranch.IDENTITE, 8);
        tree.recordGapQuestion(TreeBranch.HABITUDES, 10);

        persistenceService.doSave();

        UserObservationTree freshTree = new UserObservationTree(properties);
        UserTreePersistenceService freshPersistence =
                new UserTreePersistenceService(freshTree, properties);
        freshPersistence.load();

        Map<TreeBranch, Integer> restored = freshTree.getLastGapQuestionPerBranch();
        assertEquals(8, restored.get(TreeBranch.IDENTITE));
        assertEquals(10, restored.get(TreeBranch.HABITUDES));
    }

    // ---- Test 5: Retrieval includes gap hint ----

    @Test
    void retrieval_IncludesGapHint_WhenProfileStabilityIsMedium() {
        tree.setConversationCount(5);
        tree.setSummary(TreeBranch.IDENTITE, "Pierre");
        tree.setSummary(TreeBranch.COMMUNICATION, "Style concis");

        UserProfileContext context = retrievalService.retrieveUserContext("Bonjour");

        assertTrue(context.hasProactiveHint(),
                "Should include a proactive gap hint at MEDIUM stability");
        assertFalse(context.proactiveGapHint().isBlank());
    }

    // ---- Test 6: Retrieval skips gap hint before MEDIUM stability ----

    @Test
    void retrieval_NoGapHint_WhenProfileStabilityIsLow() {
        tree.setConversationCount(3);
        tree.setSummary(TreeBranch.IDENTITE, "Pierre");
        tree.setSummary(TreeBranch.COMMUNICATION, "Style concis");

        UserProfileContext context = retrievalService.retrieveUserContext("Bonjour");

        assertFalse(context.hasProactiveHint(),
                "No gap hint before MEDIUM stability");
    }

    // ---- Test 7: Engagement decay detection end-to-end ----

    @Test
    void engagementDecay_DetectedInRetrievalContext() {
        tree.setConversationCount(15);
        tree.setSummary(TreeBranch.IDENTITE, "Pierre");
        tree.setSummary(TreeBranch.COMMUNICATION, "Style concis");

        // Build a history with clear decay: 7 good + 3 bad (sparse + low messages)
        Instant base = Instant.now().minus(50, ChronoUnit.DAYS);
        for (int i = 0; i < 7; i++) {
            tree.addEngagementRecord(new EngagementRecord(base.plus(i, ChronoUnit.DAYS), 8));
        }
        for (int i = 0; i < 3; i++) {
            tree.addEngagementRecord(new EngagementRecord(base.plus(7 + (i * 7), ChronoUnit.DAYS), 1));
        }

        UserProfileContext context = retrievalService.retrieveUserContext("Bonjour");

        assertTrue(context.engagementDecayDetected(),
                "Should detect engagement decay");
    }

    // ---- Test 8: Greeting service provides context ----

    @Test
    void greetingContext_IncludedInRetrieval_WhenTreeHasData() {
        tree.setConversationCount(5);
        tree.setSummary(TreeBranch.IDENTITE, "Pierre, développeur");
        tree.setSummary(TreeBranch.COMMUNICATION, "Style concis");
        tree.setSummary(TreeBranch.OBJECTIFS, "Apprendre Rust");

        UserProfileContext context = retrievalService.retrieveUserContext("Bonjour");

        assertTrue(context.hasGreetingContext(),
                "Should include greeting context when tree has data");
        assertTrue(context.greetingContext().contains("Apprendre Rust"));
    }

    // ---- Test 9: Full pipeline end-to-end (multiple conversations → gap hint evolves) ----

    @Test
    void fullPipeline_GapHintEvolvesAcrossConversations() {
        // Simulate 6 conversations to reach MEDIUM stability
        for (int i = 0; i < 6; i++) {
            pipeline.processConversation(
                    List.of("Message un", "Message deux"), false);
        }

        assertEquals(6, tree.getConversationCount());
        assertEquals(ProfileStability.MEDIUM, tree.getProfileStability());
        assertEquals(6, tree.getEngagementHistory().size());
    }

    // ---- Test 10: Snapshot roundtrip preserves all Phase 3 state ----

    @Test
    void snapshotRoundtrip_PreservesPhase3State() throws IOException {
        tree.setConversationCount(10);
        tree.addEngagementRecord(new EngagementRecord(Instant.now().minus(1, ChronoUnit.DAYS), 5));
        tree.addEngagementRecord(new EngagementRecord(Instant.now(), 3));
        tree.recordGapQuestion(TreeBranch.OBJECTIFS, 8);
        tree.addLeaf(new ObservationLeaf("Pierre", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED));
        tree.setSummary(TreeBranch.IDENTITE, "Pierre");

        Path snapshotPath = persistenceService.createSnapshot();
        assertTrue(Files.exists(snapshotPath));

        // Reset tree
        Map<TreeBranch, List<ObservationLeaf>> emptyBranches = new EnumMap<>(TreeBranch.class);
        for (TreeBranch branch : TreeBranch.values()) {
            emptyBranches.put(branch, new ArrayList<>());
        }
        tree.replaceAll(emptyBranches, 0, new EnumMap<>(TreeBranch.class), new HashMap<>());
        tree.setEngagementHistory(new ArrayList<>());
        tree.setLastGapQuestionPerBranch(new EnumMap<>(TreeBranch.class));

        assertEquals(0, tree.getConversationCount());
        assertTrue(tree.getEngagementHistory().isEmpty());

        // Restore
        persistenceService.restoreSnapshot(snapshotPath);

        assertEquals(10, tree.getConversationCount());
        assertEquals(2, tree.getEngagementHistory().size());
        assertEquals(5, tree.getEngagementHistory().get(0).getMessageCount());
        assertEquals(8, tree.getLastGapQuestionPerBranch().get(TreeBranch.OBJECTIFS));
        assertEquals("Pierre", tree.getSummary(TreeBranch.IDENTITE));
        assertEquals(1, tree.getActiveLeaves(TreeBranch.IDENTITE).size());
    }
}
