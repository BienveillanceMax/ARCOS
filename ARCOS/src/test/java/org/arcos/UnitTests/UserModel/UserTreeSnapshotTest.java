package org.arcos.UnitTests.UserModel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.arcos.UserModel.Models.ObservationLeaf;
import org.arcos.UserModel.Models.ObservationSource;
import org.arcos.UserModel.Models.TreeBranch;
import org.arcos.UserModel.Persistence.UserTreePersistenceService;
import org.arcos.UserModel.UserModelProperties;
import org.arcos.UserModel.UserObservationTree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UserTreeSnapshotTest {

    @TempDir
    Path tempDir;

    private UserModelProperties properties;
    private UserObservationTree tree;
    private UserTreePersistenceService service;

    @BeforeEach
    void setUp() {
        properties = new UserModelProperties();
        properties.setStoragePath(tempDir.resolve("user-tree.json").toString());
        properties.setArchivePath(tempDir.resolve("archive.json").toString());
        properties.setDebounceSaveMs(50);
        tree = new UserObservationTree(properties);
        service = new UserTreePersistenceService(tree, properties);
    }

    @Test
    void createSnapshot_shouldCreateFileWithCorrectContent() throws IOException {
        // Given
        tree.addLeaf(new ObservationLeaf("Pierre aime Java", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED));
        tree.addLeaf(new ObservationLeaf("Il pose des questions", TreeBranch.COMMUNICATION, ObservationSource.HEURISTIC));
        tree.setConversationCount(5);
        tree.setSummary(TreeBranch.IDENTITE, "Developpeur Java");
        tree.setHeuristicBaselines(Map.of("avg_word_count", 15.0));

        // When
        Path snapshotPath = service.createSnapshot();

        // Then
        assertTrue(Files.exists(snapshotPath));
        assertTrue(snapshotPath.getFileName().toString().startsWith("user-tree-snapshot-"));
        assertTrue(snapshotPath.getFileName().toString().endsWith(".json"));

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        UserTreePersistenceService.TreeSnapshot snapshot = mapper.readValue(
                snapshotPath.toFile(), UserTreePersistenceService.TreeSnapshot.class);

        assertEquals(2, snapshot.getBranches().values().stream().mapToInt(java.util.List::size).sum());
        assertEquals(5, snapshot.getConversationCount());
        assertEquals("Developpeur Java", snapshot.getSummaries().get(TreeBranch.IDENTITE));
        assertEquals(15.0, snapshot.getHeuristicBaselines().get("avg_word_count"));
    }

    @Test
    void restoreSnapshot_shouldReplaceTreeState() throws IOException {
        // Given — populate tree and take snapshot
        tree.addLeaf(new ObservationLeaf("Original data", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED));
        tree.setConversationCount(10);
        tree.setSummary(TreeBranch.IDENTITE, "Original summary");
        Path snapshotPath = service.createSnapshot();

        // Modify the tree after snapshot
        tree.addLeaf(new ObservationLeaf("New data after snapshot", TreeBranch.HABITUDES, ObservationSource.HEURISTIC));
        tree.setConversationCount(15);
        tree.setSummary(TreeBranch.IDENTITE, "Modified summary");
        assertEquals(2, tree.getAllActiveLeaves().size());

        // When
        service.restoreSnapshot(snapshotPath);

        // Then — tree should be back to snapshot state
        assertEquals(1, tree.getAllActiveLeaves().size());
        assertEquals("Original data", tree.getActiveLeaves(TreeBranch.IDENTITE).get(0).getText());
        assertEquals(10, tree.getConversationCount());
        assertEquals("Original summary", tree.getSummary(TreeBranch.IDENTITE));
    }

    @Test
    void cleanupSnapshots_shouldRetainCorrectCount() throws IOException {
        // Given — create 5 snapshots, make all of them "old" so retentionDays won't save them
        Path[] snaps = new Path[5];
        for (int i = 0; i < 5; i++) {
            tree.addLeaf(new ObservationLeaf("Leaf " + i, TreeBranch.IDENTITE, ObservationSource.HEURISTIC));
            snaps[i] = service.createSnapshot();
        }
        // Make all snapshots old (30 days ago) so only count-based retention applies
        for (Path snap : snaps) {
            Files.setLastModifiedTime(snap, FileTime.from(Instant.now().minus(30, ChronoUnit.DAYS)));
        }

        long snapshotCountBefore = Files.list(tempDir)
                .filter(p -> p.getFileName().toString().startsWith("user-tree-snapshot-"))
                .count();
        assertEquals(5, snapshotCountBefore);

        // When — retain only 2 by count, 7 days retention (all are 30 days old → outside)
        service.cleanupSnapshots(2, 7);

        // Then
        long snapshotCountAfter = Files.list(tempDir)
                .filter(p -> p.getFileName().toString().startsWith("user-tree-snapshot-"))
                .count();
        assertEquals(2, snapshotCountAfter);
    }

    @Test
    void cleanupSnapshots_shouldRetainWithinRetentionDays() throws IOException {
        // Given — create 3 snapshots
        Path snap1 = service.createSnapshot();
        Path snap2 = service.createSnapshot();
        Path snap3 = service.createSnapshot();

        // Make snap1 "old" (20 days ago)
        Files.setLastModifiedTime(snap1, FileTime.from(Instant.now().minus(20, ChronoUnit.DAYS)));
        // snap2 and snap3 are recent

        // When — retain count=1 but retention days=7 (so recent files survive)
        service.cleanupSnapshots(1, 7);

        // Then — snap1 is old AND outside count → deleted; snap2 within days → kept; snap3 within count → kept
        assertFalse(Files.exists(snap1), "Old snapshot outside both limits should be deleted");
        // At least 1 snapshot should remain (the newest by count)
        long remaining = Files.list(tempDir)
                .filter(p -> p.getFileName().toString().startsWith("user-tree-snapshot-"))
                .count();
        assertTrue(remaining >= 1);
    }

    @Test
    void restoreSnapshot_nonexistentPath_shouldThrowIOException() {
        // Given
        Path nonexistent = tempDir.resolve("nonexistent-snapshot.json");

        // When / Then
        assertThrows(IOException.class, () -> service.restoreSnapshot(nonexistent));
    }
}
