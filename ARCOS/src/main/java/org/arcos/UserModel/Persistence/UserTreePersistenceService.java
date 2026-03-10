package org.arcos.UserModel.Persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.arcos.UserModel.Engagement.EngagementRecord;
import org.arcos.UserModel.Models.ObservationLeaf;
import org.arcos.UserModel.Models.TreeBranch;
import org.arcos.UserModel.UserModelProperties;
import org.arcos.UserModel.UserObservationTree;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

@Slf4j
@Component
public class UserTreePersistenceService {

    private final UserObservationTree tree;
    private final UserModelProperties properties;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;

    private volatile ScheduledFuture<?> pendingSave;

    public UserTreePersistenceService(UserObservationTree tree, UserModelProperties properties) {
        this.tree = tree;
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.addMixIn(ObservationLeaf.class, EmbeddingIgnoreMixin.class);

        ThreadFactory daemonFactory = r -> {
            Thread t = new Thread(r, "user-tree-persistence");
            t.setDaemon(true);
            return t;
        };
        this.scheduler = Executors.newSingleThreadScheduledExecutor(daemonFactory);
    }

    @PostConstruct
    public void load() {
        Path storagePath = Paths.get(properties.getStoragePath());
        if (!Files.exists(storagePath)) {
            log.info("No user tree file found at {}, starting with empty tree", storagePath);
            return;
        }

        try {
            byte[] content = Files.readAllBytes(storagePath);
            TreeSnapshot snapshot = objectMapper.readValue(content, TreeSnapshot.class);

            Map<TreeBranch, List<ObservationLeaf>> branches = snapshot.getBranches() != null
                    ? snapshot.getBranches()
                    : new EnumMap<>(TreeBranch.class);

            tree.replaceAll(
                    branches,
                    snapshot.getConversationCount(),
                    snapshot.getSummaries(),
                    snapshot.getHeuristicBaselines()
            );
            tree.setEngagementHistory(snapshot.getEngagementHistory());
            tree.setLastGapQuestionPerBranch(snapshot.getLastGapQuestionPerBranch());

            int totalLeaves = branches.values().stream().mapToInt(List::size).sum();
            log.info("Loaded user tree from {}: {} leaves, {} conversations",
                    storagePath, totalLeaves, snapshot.getConversationCount());
        } catch (IOException e) {
            log.warn("Failed to load user tree from {} (corrupted or unreadable), continuing with empty tree: {}",
                    storagePath, e.getMessage());
        }
    }

    public void scheduleSave() {
        ScheduledFuture<?> existing = pendingSave;
        if (existing != null) {
            existing.cancel(false);
        }
        pendingSave = scheduler.schedule(this::doSave, properties.getDebounceSaveMs(), TimeUnit.MILLISECONDS);
    }

    public void cancelPendingSave() {
        ScheduledFuture<?> existing = pendingSave;
        if (existing != null) {
            existing.cancel(false);
            pendingSave = null;
        }
    }

    public void doSave() {
        Path targetPath = Paths.get(properties.getStoragePath());
        Path tmpPath = Paths.get(properties.getStoragePath() + ".tmp");

        try {
            Files.createDirectories(targetPath.getParent());

            TreeSnapshot snapshot = buildSnapshot();

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmpPath.toFile(), snapshot);
            Files.move(tmpPath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

            int totalLeaves = snapshot.getBranches().values().stream().mapToInt(List::size).sum();
            log.debug("Saved user tree to {}: {} leaves", targetPath, totalLeaves);
        } catch (IOException e) {
            log.error("Failed to save user tree to {}", targetPath, e);
        }
    }

    public Path createSnapshot() throws IOException {
        Path storagePath = Paths.get(properties.getStoragePath());
        Path snapshotDir = storagePath.getParent();
        if (snapshotDir == null) snapshotDir = Paths.get(".");
        Files.createDirectories(snapshotDir);

        String timestamp = Instant.now().toString().replace(":", "-");
        Path snapshotPath = snapshotDir.resolve("user-tree-snapshot-" + timestamp + ".json");
        Path tmpPath = Paths.get(snapshotPath + ".tmp");

        TreeSnapshot snapshot = buildSnapshot();
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmpPath.toFile(), snapshot);
        Files.move(tmpPath, snapshotPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

        int totalLeaves = snapshot.getBranches().values().stream().mapToInt(List::size).sum();
        log.info("Created snapshot at {}: {} leaves, {} conversations", snapshotPath, totalLeaves, snapshot.getConversationCount());
        return snapshotPath;
    }

    public void restoreSnapshot(Path snapshotPath) throws IOException {
        if (!Files.exists(snapshotPath)) {
            throw new IOException("Snapshot file not found: " + snapshotPath);
        }

        byte[] content = Files.readAllBytes(snapshotPath);
        TreeSnapshot snapshot = objectMapper.readValue(content, TreeSnapshot.class);

        Map<TreeBranch, List<ObservationLeaf>> branches = snapshot.getBranches() != null
                ? snapshot.getBranches()
                : new EnumMap<>(TreeBranch.class);

        tree.replaceAll(
                branches,
                snapshot.getConversationCount(),
                snapshot.getSummaries(),
                snapshot.getHeuristicBaselines()
        );
        tree.setEngagementHistory(snapshot.getEngagementHistory());
        tree.setLastGapQuestionPerBranch(snapshot.getLastGapQuestionPerBranch());

        int totalLeaves = branches.values().stream().mapToInt(List::size).sum();
        log.info("Restored snapshot from {}: {} leaves, {} conversations", snapshotPath, totalLeaves, snapshot.getConversationCount());
    }

    public void cleanupSnapshots(int retentionCount, int retentionDays) {
        Path storagePath = Paths.get(properties.getStoragePath());
        Path snapshotDir = storagePath.getParent();
        if (snapshotDir == null) snapshotDir = Paths.get(".");

        try (Stream<Path> files = Files.list(snapshotDir)) {
            List<Path> snapshots = files
                    .filter(p -> p.getFileName().toString().startsWith("user-tree-snapshot-"))
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.<Path>comparingLong(p -> {
                        try { return Files.getLastModifiedTime(p).toMillis(); }
                        catch (IOException e) { return 0L; }
                    }).reversed())
                    .toList();

            Instant retentionCutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

            for (int i = 0; i < snapshots.size(); i++) {
                Path snapshot = snapshots.get(i);
                boolean withinCount = i < retentionCount;
                boolean withinDays;
                try {
                    withinDays = Files.getLastModifiedTime(snapshot).toInstant().isAfter(retentionCutoff);
                } catch (IOException e) {
                    withinDays = false;
                }

                // Keep if within either limit (most permissive wins)
                if (!withinCount && !withinDays) {
                    try {
                        Files.delete(snapshot);
                        log.debug("Deleted old snapshot: {}", snapshot);
                    } catch (IOException e) {
                        log.warn("Failed to delete snapshot {}: {}", snapshot, e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to list snapshots for cleanup: {}", e.getMessage());
        }
    }

    private TreeSnapshot buildSnapshot() {
        TreeSnapshot snapshot = new TreeSnapshot();
        Map<TreeBranch, List<ObservationLeaf>> branchesMap = new EnumMap<>(TreeBranch.class);
        for (TreeBranch branch : TreeBranch.values()) {
            branchesMap.put(branch, tree.getActiveLeaves(branch));
        }
        snapshot.setBranches(branchesMap);
        snapshot.setConversationCount(tree.getConversationCount());
        snapshot.setSummaries(tree.getAllSummaries());
        snapshot.setHeuristicBaselines(tree.getHeuristicBaselines());
        snapshot.setEngagementHistory(new ArrayList<>(tree.getEngagementHistory()));
        snapshot.setLastGapQuestionPerBranch(tree.getLastGapQuestionPerBranch());
        return snapshot;
    }

    public void archiveLeaf(ObservationLeaf leaf, String reason) {
        Path archivePath = Paths.get(properties.getArchivePath());
        Path tmpPath = Paths.get(properties.getArchivePath() + ".tmp");

        try {
            Files.createDirectories(archivePath.getParent());

            List<ArchivedLeaf> archive;
            if (Files.exists(archivePath)) {
                archive = objectMapper.readValue(
                        archivePath.toFile(),
                        new TypeReference<List<ArchivedLeaf>>() {}
                );
            } else {
                archive = new ArrayList<>();
            }

            ArchivedLeaf entry = new ArchivedLeaf();
            entry.setText(leaf.getText());
            entry.setBranch(leaf.getBranch());
            entry.setReason(reason);
            entry.setArchivedAt(Instant.now());

            archive.add(entry);

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmpPath.toFile(), archive);
            Files.move(tmpPath, archivePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

            log.debug("Archived leaf '{}' from branch {} — reason: {}", leaf.getText(), leaf.getBranch(), reason);
        } catch (IOException e) {
            log.error("Failed to archive leaf to {}", archivePath, e);
        }
    }

    @Data
    public static class TreeSnapshot {
        private Map<TreeBranch, List<ObservationLeaf>> branches;
        private int conversationCount;
        private Map<TreeBranch, String> summaries;
        private Map<String, Double> heuristicBaselines;
        private List<EngagementRecord> engagementHistory = new ArrayList<>();
        private Map<TreeBranch, Integer> lastGapQuestionPerBranch = new EnumMap<>(TreeBranch.class);
    }

    @Data
    public static class ArchivedLeaf {
        private String text;
        private TreeBranch branch;
        private String reason;
        private Instant archivedAt;
    }

    /**
     * Jackson mixin to exclude the embedding field from serialization.
     * Embeddings are recomputable from leaf text and should not bloat the persistence file.
     */
    abstract static class EmbeddingIgnoreMixin {
        @JsonIgnore
        abstract float[] getEmbedding();
    }
}
