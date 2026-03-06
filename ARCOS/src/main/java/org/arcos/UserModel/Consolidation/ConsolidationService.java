package org.arcos.UserModel.Consolidation;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.arcos.LLM.Local.LocalLlmService;
import org.arcos.UserModel.Consolidation.Models.ConsolidationAction;
import org.arcos.UserModel.Consolidation.Models.ConsolidationDecision;
import org.arcos.UserModel.Consolidation.Models.ConsolidationResult;
import org.arcos.UserModel.Embedding.LocalEmbeddingService;
import org.arcos.UserModel.Models.ObservationLeaf;
import org.arcos.UserModel.Models.TreeBranch;
import org.arcos.UserModel.Persistence.UserTreePersistenceService;
import org.arcos.UserModel.Retrieval.BranchSummaryProvider;
import org.arcos.UserModel.UserModelProperties;
import org.arcos.UserModel.UserObservationTree;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@ConditionalOnProperty(name = "arcos.user-model.consolidation.enabled", havingValue = "true")
public class ConsolidationService {

    private final LocalLlmService localLlmService;
    private final UserObservationTree tree;
    private final UserTreePersistenceService persistenceService;
    private final ConsolidationPromptBuilder promptBuilder;
    private final LocalEmbeddingService embeddingService;
    private final BranchSummaryProvider summaryBuilder;
    private final UserModelProperties properties;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public ConsolidationService(LocalLlmService localLlmService,
                                UserObservationTree tree,
                                UserTreePersistenceService persistenceService,
                                ConsolidationPromptBuilder promptBuilder,
                                LocalEmbeddingService embeddingService,
                                BranchSummaryProvider summaryBuilder,
                                UserModelProperties properties) {
        this.localLlmService = localLlmService;
        this.tree = tree;
        this.persistenceService = persistenceService;
        this.promptBuilder = promptBuilder;
        this.embeddingService = embeddingService;
        this.summaryBuilder = summaryBuilder;
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
    }

    @Scheduled(cron = "${arcos.user-model.consolidation.batch-cron}")
    public ConsolidationResult runConsolidation() {
        long startTime = System.currentTimeMillis();
        var config = properties.getConsolidation();

        // Pre-flight: skip if LLM unavailable or already running
        if (!localLlmService.isAvailable()) {
            log.info("Consolidation skipped: local LLM unavailable");
            return emptyResult(startTime);
        }
        if (!isRunning.compareAndSet(false, true)) {
            log.info("Consolidation skipped: already running");
            return emptyResult(startTime);
        }

        Path snapshotPath = null;
        int totalScanned = 0;
        int conflictsResolved = 0;
        int duplicatesMerged = 0;
        int leavesArchived = 0;
        int leavesRewritten = 0;
        int parseErrors = 0;
        int timeouts = 0;
        boolean rolledBack = false;
        Set<TreeBranch> modifiedBranches = new HashSet<>();

        try {
            // Create snapshot before processing
            snapshotPath = persistenceService.createSnapshot();
            int initialLeafCount = tree.getAllActiveLeaves().size();

            // Scan: collect work items
            List<WorkItem> workItems = collectWorkItems();
            totalScanned = workItems.size();
            log.info("Consolidation cycle started: {} work items, {} initial leaves", totalScanned, initialLeafCount);

            // Processing loop
            int processed = 0;
            for (WorkItem item : workItems) {
                if (processed >= config.getMaxLeavesPerRun()) break;
                if (parseErrors >= config.getMaxParseErrors()) {
                    log.warn("Consolidation: max parse errors ({}) reached, rolling back", parseErrors);
                    rollbackToSnapshot(snapshotPath);
                    rolledBack = true;
                    break;
                }
                if (timeouts >= config.getMaxTimeoutsPerRun()) {
                    log.warn("Consolidation: max timeouts ({}) reached, aborting cycle", timeouts);
                    break;
                }

                try {
                    ConsolidationDecision decision = processWorkItem(item);
                    if (decision == null) {
                        parseErrors++;
                        continue;
                    }
                    if (decision.confidence() < config.getDecisionConfidenceThreshold()) {
                        log.debug("Consolidation: low confidence ({:.2f}), skipping", decision.confidence());
                        continue;
                    }

                    switch (decision.decision()) {
                        case MERGE -> {
                            executeMerge(decision, item);
                            duplicatesMerged++;
                            modifiedBranches.add(item.leafA.getBranch());
                            if (item.leafB != null) modifiedBranches.add(item.leafB.getBranch());
                        }
                        case REBRANCH -> {
                            executeRebranch(decision, item);
                            conflictsResolved++;
                            modifiedBranches.add(item.leafA.getBranch());
                            if (item.leafB != null) modifiedBranches.add(item.leafB.getBranch());
                        }
                        case REWRITE -> {
                            executeRewrite(decision, item);
                            leavesRewritten++;
                            modifiedBranches.add(item.leafA.getBranch());
                        }
                        case ARCHIVE -> {
                            executeArchive(decision, item);
                            leavesArchived++;
                            modifiedBranches.add(item.leafA.getBranch());
                        }
                        case KEEP_BOTH -> {
                            clearConsolidationFlags(item);
                            if (item.leafB != null) conflictsResolved++;
                        }
                        default -> log.debug("Consolidation: no action for decision {}", decision.decision());
                    }

                    processed++;

                } catch (java.util.concurrent.ExecutionException e) {
                    if (e.getCause() instanceof TimeoutException) {
                        timeouts++;
                        log.warn("Consolidation: timeout processing work item");
                    } else {
                        parseErrors++;
                        log.warn("Consolidation: error processing work item: {}", e.getMessage());
                    }
                } catch (TimeoutException e) {
                    timeouts++;
                    log.warn("Consolidation: timeout processing work item");
                } catch (Exception e) {
                    parseErrors++;
                    log.warn("Consolidation: error processing work item: {}", e.getMessage());
                }

                // Rollback check: leaf count drop
                if (!rolledBack) {
                    int currentLeafCount = tree.getAllActiveLeaves().size();
                    if (shouldRollback(initialLeafCount, currentLeafCount, config)) {
                        log.warn("Consolidation: excessive leaf removal (initial={}, current={}), rolling back",
                                initialLeafCount, currentLeafCount);
                        rollbackToSnapshot(snapshotPath);
                        rolledBack = true;
                        break;
                    }
                }
            }

            // Post-processing
            if (!rolledBack) {
                // Rebuild summaries for modified branches
                for (TreeBranch branch : modifiedBranches) {
                    summaryBuilder.rebuild(branch);
                }
                persistenceService.doSave();

                // Cleanup old snapshots
                persistenceService.cleanupSnapshots(
                        config.getSnapshotRetentionCount(),
                        config.getSnapshotRetentionDays()
                );
            }

        } catch (Exception e) {
            log.error("Consolidation: uncaught exception, rolling back", e);
            if (snapshotPath != null && !rolledBack) {
                try {
                    rollbackToSnapshot(snapshotPath);
                    rolledBack = true;
                } catch (IOException rollbackEx) {
                    log.error("Consolidation: rollback failed", rollbackEx);
                }
            }
        } finally {
            isRunning.set(false);
        }

        long durationMs = System.currentTimeMillis() - startTime;
        ConsolidationResult result = new ConsolidationResult(
                totalScanned, conflictsResolved, duplicatesMerged,
                leavesArchived, leavesRewritten, parseErrors, timeouts,
                rolledBack, snapshotPath, durationMs
        );

        log.info("Consolidation cycle complete: {} scanned, {} conflicts, {} merged, {} archived, {} rewritten, {} errors, {} timeouts, rollback={}, {}ms",
                totalScanned, conflictsResolved, duplicatesMerged, leavesArchived,
                leavesRewritten, parseErrors, timeouts, rolledBack, durationMs);

        return result;
    }

    public void rollbackToSnapshot(Path snapshotPath) throws IOException {
        persistenceService.restoreSnapshot(snapshotPath);
        persistenceService.doSave();
        log.info("Rolled back to snapshot: {}", snapshotPath);
    }

    List<WorkItem> collectWorkItems() {
        List<WorkItem> items = new ArrayList<>();
        List<ObservationLeaf> allLeaves = tree.getAllActiveLeaves();

        // Priority 1: conflictsWith pairs
        for (ObservationLeaf leaf : allLeaves) {
            if (leaf.getConflictsWith() != null) {
                Optional<ObservationLeaf> other = tree.findLeafById(leaf.getConflictsWith());
                if (other.isPresent()) {
                    items.add(new WorkItem(WorkItemType.CROSS_BRANCH_CONFLICT, leaf, other.get()));
                }
            }
        }

        // Priority 2: needsConsolidation leaves
        for (ObservationLeaf leaf : allLeaves) {
            if (leaf.isNeedsConsolidation() && leaf.getConflictsWith() == null) {
                items.add(new WorkItem(WorkItemType.INTRA_BRANCH, leaf, null));
            }
        }

        return items;
    }

    private ConsolidationDecision processWorkItem(WorkItem item) throws Exception {
        String prompt;
        if (item.type == WorkItemType.CROSS_BRANCH_CONFLICT && item.leafB != null) {
            prompt = promptBuilder.buildCrossBranchConflict(item.leafA, item.leafB, 0.0);
        } else {
            List<ObservationLeaf> similar = findSimilarLeaves(item.leafA);
            if (similar.isEmpty()) {
                clearConsolidationFlags(item);
                return null;
            }
            prompt = promptBuilder.buildIntraBranchConsolidation(item.leafA, similar);
        }

        String response = localLlmService.generateComplexAsync(prompt)
                .get(properties.getConsolidation().getTimeoutMs(), java.util.concurrent.TimeUnit.MILLISECONDS);

        if (response == null || response.isBlank()) return null;

        // Extract JSON from response (may contain thinking tags)
        String json = extractJson(response);
        if (json == null) return null;

        try {
            return objectMapper.readValue(json, ConsolidationDecision.class);
        } catch (Exception e) {
            log.debug("Consolidation: failed to parse decision JSON: {}", e.getMessage());
            return null;
        }
    }

    private List<ObservationLeaf> findSimilarLeaves(ObservationLeaf target) {
        if (target.getEmbedding() == null) return Collections.emptyList();

        List<ObservationLeaf> branchLeaves = tree.getActiveLeaves(target.getBranch());
        List<ObservationLeaf> similar = new ArrayList<>();
        for (ObservationLeaf leaf : branchLeaves) {
            if (leaf.getId().equals(target.getId())) continue;
            if (leaf.getEmbedding() == null) continue;
            double sim = embeddingService.cosineSimilarity(target.getEmbedding(), leaf.getEmbedding());
            if (sim >= 0.5) {
                similar.add(leaf);
            }
        }
        return similar;
    }

    private void executeMerge(ConsolidationDecision decision, WorkItem item) {
        String loserId = decision.winnerId() != null && decision.winnerId().equals(item.leafA.getId())
                ? (item.leafB != null ? item.leafB.getId() : null)
                : item.leafA.getId();

        if (loserId != null) {
            tree.findLeafById(loserId).ifPresent(loser ->
                    persistenceService.archiveLeaf(loser, "Consolidated: MERGE"));
            tree.removeLeaf(loserId);
        }
        clearConsolidationFlags(item);
    }

    private void executeRebranch(ConsolidationDecision decision, WorkItem item) {
        if (decision.targetBranch() != null) {
            try {
                TreeBranch newBranch = TreeBranch.valueOf(decision.targetBranch());
                item.leafA.setBranch(newBranch);
            } catch (IllegalArgumentException e) {
                log.debug("Invalid target branch: {}", decision.targetBranch());
            }
        }
        clearConsolidationFlags(item);
    }

    private void executeRewrite(ConsolidationDecision decision, WorkItem item) {
        if (decision.newText() != null && !decision.newText().isBlank()) {
            item.leafA.setText(decision.newText());
            // Re-embed the rewritten text
            float[] newEmbedding = embeddingService.embed(decision.newText());
            item.leafA.setEmbedding(newEmbedding);
        }
        clearConsolidationFlags(item);
    }

    private void executeArchive(ConsolidationDecision decision, WorkItem item) {
        ObservationLeaf toArchive = item.leafB != null ? item.leafB : item.leafA;
        persistenceService.archiveLeaf(toArchive, "Consolidated: ARCHIVE");
        tree.removeLeaf(toArchive.getId());
        clearConsolidationFlags(item);
    }

    private void clearConsolidationFlags(WorkItem item) {
        item.leafA.setNeedsConsolidation(false);
        item.leafA.setConflictsWith(null);
        if (item.leafB != null) {
            item.leafB.setNeedsConsolidation(false);
            item.leafB.setConflictsWith(null);
        }
    }

    private boolean shouldRollback(int initialCount, int currentCount, UserModelProperties.Consolidation config) {
        int removed = initialCount - currentCount;
        if (removed <= 0) return false;

        if (initialCount >= config.getMinLeavesForPercentageCheck()) {
            double dropPercent = (double) removed / initialCount;
            return dropPercent > 0.10;
        } else {
            return removed > config.getMaxAbsoluteRemovalWhenBelowFloor();
        }
    }

    String extractJson(String response) {
        if (response == null) return null;
        // Remove thinking tags if present
        String cleaned = response.replaceAll("(?s)<think>.*?</think>", "").trim();
        // Find first { and last }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }
        return null;
    }

    private ConsolidationResult emptyResult(long startTime) {
        return new ConsolidationResult(0, 0, 0, 0, 0, 0, 0, false, null,
                System.currentTimeMillis() - startTime);
    }

    enum WorkItemType {
        CROSS_BRANCH_CONFLICT,
        INTRA_BRANCH
    }

    record WorkItem(WorkItemType type, ObservationLeaf leafA, ObservationLeaf leafB) {}
}
