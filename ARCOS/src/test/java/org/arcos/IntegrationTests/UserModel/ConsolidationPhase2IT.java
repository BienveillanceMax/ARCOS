package org.arcos.IntegrationTests.UserModel;

import org.arcos.UserModel.Consolidation.ConsolidationPromptBuilder;
import org.arcos.UserModel.Consolidation.ConsolidationService;
import org.arcos.UserModel.Heuristics.*;
import org.arcos.UserModel.Models.ObservationCandidate;
import org.arcos.UserModel.Models.ObservationLeaf;
import org.arcos.UserModel.Models.ObservationSource;
import org.arcos.UserModel.Models.TreeBranch;
import org.arcos.UserModel.Retrieval.*;
import org.arcos.UserModel.UserObservationTree;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
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
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = UserModelTestConfiguration.class)
class ConsolidationPhase2IT {

    private static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        try {
            tempDir = Files.createTempDirectory("arcos-phase2-it");
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

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private UserObservationTree tree;

    // ---- Phase 2 conditional beans should NOT be created when local LLM is disabled ----

    @Test
    void withLocalLlmDisabled_shouldNotCreateGeneratorBeans() {
        assertThrows(NoSuchBeanDefinitionException.class,
                () -> applicationContext.getBean(BranchSummaryGenerator.class),
                "BranchSummaryGenerator requires arcos.local-llm.enabled=true");
        assertThrows(NoSuchBeanDefinitionException.class,
                () -> applicationContext.getBean(HeuristicNarrativeGenerator.class),
                "HeuristicNarrativeGenerator requires arcos.local-llm.enabled=true");
    }

    @Test
    void withConsolidationDisabled_shouldNotCreateConsolidationService() {
        assertThrows(NoSuchBeanDefinitionException.class,
                () -> applicationContext.getBean(ConsolidationService.class),
                "ConsolidationService requires arcos.user-model.consolidation.enabled=true");
    }

    // ---- Phase 1 beans and chain beans SHOULD be present ----

    @Test
    void phase1Beans_shouldBePresent() {
        assertNotNull(applicationContext.getBean(BranchSummaryBuilder.class));
        assertNotNull(applicationContext.getBean(HeuristicTextTemplates.class));
        assertNotNull(applicationContext.getBean(ConsolidationPromptBuilder.class));
    }

    @Test
    void chainBeans_shouldBePresentAndAvailable() {
        BranchSummaryProviderChain summaryChain =
                applicationContext.getBean(BranchSummaryProviderChain.class);
        assertNotNull(summaryChain);
        assertTrue(summaryChain.isAvailable());

        HeuristicLeafProviderChain heuristicChain =
                applicationContext.getBean(HeuristicLeafProviderChain.class);
        assertNotNull(heuristicChain);
        assertTrue(heuristicChain.isAvailable());
    }

    @Test
    void primaryProvider_shouldResolveToChain() {
        // @Primary chains should be injected when requesting the interface
        BranchSummaryProvider summaryProvider =
                applicationContext.getBean(BranchSummaryProvider.class);
        assertInstanceOf(BranchSummaryProviderChain.class, summaryProvider);

        HeuristicLeafProvider leafProvider =
                applicationContext.getBean(HeuristicLeafProvider.class);
        assertInstanceOf(HeuristicLeafProviderChain.class, leafProvider);
    }

    // ---- Chain fallback: chains should delegate to Phase 1 in real Spring context ----

    @Test
    void summaryChain_shouldFallBackToBuilder_whenGeneratorAbsent() {
        tree.addLeaf(new ObservationLeaf(
                "Mon créateur s'appelle Pierre", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED));

        BranchSummaryProvider provider =
                applicationContext.getBean(BranchSummaryProvider.class);

        String summary = provider.rebuild(TreeBranch.IDENTITE);

        assertNotNull(summary, "Chain should produce a summary via Phase 1 builder fallback");
        assertTrue(summary.contains("Pierre"));
    }

    // ---- Isolated AnnotationConfigApplicationContext: verify bean presence/absence ----

    @Test
    void isolatedContext_withLocalLlmDisabled_shouldNotCreateOllamaDependentBeans() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("arcos.user-model.enabled", "true");
        env.setProperty("arcos.user-model.storage-path",
                tempDir.resolve("ctx-test.json").toString());
        env.setProperty("arcos.user-model.archive-path",
                tempDir.resolve("ctx-archive.json").toString());

        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.setEnvironment(env);
        ctx.register(UserModelTestConfiguration.class);
        ctx.refresh();

        // Phase 2 generators and service should NOT exist
        assertFalse(ctx.containsBean("branchSummaryGenerator"),
                "BranchSummaryGenerator should not be created when local LLM is disabled");
        assertFalse(ctx.containsBean("heuristicNarrativeGenerator"),
                "HeuristicNarrativeGenerator should not be created when local LLM is disabled");
        assertFalse(ctx.containsBean("consolidationService"),
                "ConsolidationService should not be created when consolidation is disabled");

        // Phase 1 + chains SHOULD exist
        assertTrue(ctx.containsBean("branchSummaryBuilder"),
                "BranchSummaryBuilder (Phase 1) should be created");
        assertTrue(ctx.containsBean("heuristicTextTemplates"),
                "HeuristicTextTemplates (Phase 1) should be created");
        assertTrue(ctx.containsBean("branchSummaryProviderChain"),
                "BranchSummaryProviderChain should be created even without generator");
        assertTrue(ctx.containsBean("heuristicLeafProviderChain"),
                "HeuristicLeafProviderChain should be created even without generator");
        assertTrue(ctx.containsBean("consolidationPromptBuilder"),
                "ConsolidationPromptBuilder should always be created");

        ctx.close();
    }

    @Test
    void isolatedContext_existingUserModelBeans_shouldStillWork() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("arcos.user-model.enabled", "true");
        env.setProperty("arcos.user-model.storage-path",
                tempDir.resolve("ctx-test2.json").toString());
        env.setProperty("arcos.user-model.archive-path",
                tempDir.resolve("ctx-archive2.json").toString());

        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.setEnvironment(env);
        ctx.register(UserModelTestConfiguration.class);
        ctx.refresh();

        // Pre-existing Phase 1 beans should be unaffected by Phase 2 additions
        assertTrue(ctx.containsBean("userObservationTree"));
        assertTrue(ctx.containsBean("userTreePersistenceService"));
        assertTrue(ctx.containsBean("userTreeUpdater"));
        assertTrue(ctx.containsBean("userModelPipelineOrchestrator"));
        assertTrue(ctx.containsBean("userModelRetrievalService"));
        assertTrue(ctx.containsBean("ebbinghausPruningService"));

        ctx.close();
    }
}
