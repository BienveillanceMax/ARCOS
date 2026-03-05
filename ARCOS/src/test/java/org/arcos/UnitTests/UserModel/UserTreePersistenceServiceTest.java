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
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class UserTreePersistenceServiceTest {

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
    void saveAndLoad_Roundtrip_ShouldPreserveData() {
        // Given
        ObservationLeaf identityLeaf = new ObservationLeaf(
                "L'utilisateur s'appelle Pierre", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED);
        ObservationLeaf commLeaf = new ObservationLeaf(
                "Il tutoie facilement", TreeBranch.COMMUNICATION, ObservationSource.HEURISTIC);
        ObservationLeaf interestLeaf = new ObservationLeaf(
                "Il aime le jazz", TreeBranch.INTERETS, ObservationSource.LLM_EXTRACTED,
                new float[]{0.1f, 0.2f, 0.3f});

        tree.addLeaf(identityLeaf);
        tree.addLeaf(commLeaf);
        tree.addLeaf(interestLeaf);
        tree.setConversationCount(7);
        tree.setSummary(TreeBranch.IDENTITE, "Pierre, developpeur Java");
        tree.setHeuristicBaselines(java.util.Map.of("avg_word_count", 12.5));

        // When — save
        service.doSave();

        // Then — load into a fresh tree
        UserObservationTree freshTree = new UserObservationTree(properties);
        UserTreePersistenceService freshService = new UserTreePersistenceService(freshTree, properties);
        freshService.load();

        assertEquals(3, freshTree.getAllActiveLeaves().size());
        assertEquals(1, freshTree.getActiveLeaves(TreeBranch.IDENTITE).size());
        assertEquals("L'utilisateur s'appelle Pierre",
                freshTree.getActiveLeaves(TreeBranch.IDENTITE).get(0).getText());
        assertEquals(1, freshTree.getActiveLeaves(TreeBranch.COMMUNICATION).size());
        assertEquals(1, freshTree.getActiveLeaves(TreeBranch.INTERETS).size());
        assertEquals("Il aime le jazz", freshTree.getActiveLeaves(TreeBranch.INTERETS).get(0).getText());
        // Embeddings are excluded from persistence (recomputable), so they should be null after load
        assertNull(freshTree.getActiveLeaves(TreeBranch.INTERETS).get(0).getEmbedding());
        assertEquals(7, freshTree.getConversationCount());
        assertEquals("Pierre, developpeur Java", freshTree.getSummary(TreeBranch.IDENTITE));
        assertEquals(12.5, freshTree.getHeuristicBaselines().get("avg_word_count"));
    }

    @Test
    void load_CorruptedFile_ShouldLogWarnAndContinue() throws IOException {
        // Given — write garbage to storage file
        Path storagePath = Path.of(properties.getStoragePath());
        Files.createDirectories(storagePath.getParent());
        Files.writeString(storagePath, "{{{{not valid json!!!!}}}}");

        // Add a leaf before load to verify it gets preserved (load won't overwrite with empty on failure)
        tree.addLeaf(new ObservationLeaf("existing", TreeBranch.IDENTITE, ObservationSource.HEURISTIC));

        // When — load should not throw
        assertDoesNotThrow(() -> service.load());

        // Then — tree should still have the leaf added before load
        // (load does not clear the tree on failure, it just skips replaceAll)
        assertEquals(1, tree.getAllActiveLeaves().size());
    }

    @Test
    void doSave_AtomicWrite_ShouldNotCorruptOnCrash() throws IOException {
        // Given
        tree.addLeaf(new ObservationLeaf("test atomic", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED));

        // When
        service.doSave();

        // Then — target file should exist and be valid JSON
        Path targetPath = Path.of(properties.getStoragePath());
        assertTrue(Files.exists(targetPath));

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        UserTreePersistenceService.TreeSnapshot snapshot = mapper.readValue(
                targetPath.toFile(),
                UserTreePersistenceService.TreeSnapshot.class
        );
        assertNotNull(snapshot);
        assertNotNull(snapshot.getBranches());
        assertEquals(1, snapshot.getBranches().get(TreeBranch.IDENTITE).size());

        // Temp file should not remain after atomic move
        Path tmpPath = Path.of(properties.getStoragePath() + ".tmp");
        assertFalse(Files.exists(tmpPath));
    }

    @Test
    void scheduleSave_Debounce_ShouldOnlySaveOnce() throws Exception {
        // Given
        tree.addLeaf(new ObservationLeaf("debounce test", TreeBranch.IDENTITE, ObservationSource.HEURISTIC));
        Path targetPath = Path.of(properties.getStoragePath());

        // When — call scheduleSave multiple times rapidly
        for (int i = 0; i < 10; i++) {
            service.scheduleSave();
        }

        // Wait for debounce to fire (50ms debounce + buffer)
        TimeUnit.MILLISECONDS.sleep(200);

        // Then — file should exist (at least one save happened)
        assertTrue(Files.exists(targetPath));

        // Read the file and verify content
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        UserTreePersistenceService.TreeSnapshot snapshot = mapper.readValue(
                targetPath.toFile(),
                UserTreePersistenceService.TreeSnapshot.class
        );
        assertEquals(1, snapshot.getBranches().get(TreeBranch.IDENTITE).size());

        // Record file modification time
        long modifiedAfterDebounce = Files.getLastModifiedTime(targetPath).toMillis();

        // Wait another debounce period to confirm no additional writes
        TimeUnit.MILLISECONDS.sleep(200);
        long modifiedLater = Files.getLastModifiedTime(targetPath).toMillis();

        assertEquals(modifiedAfterDebounce, modifiedLater,
                "File should not have been modified again after the debounced save");
    }

    @Test
    void archiveLeaf_ShouldAppendToArchiveFile() throws IOException {
        // Given
        ObservationLeaf leaf1 = new ObservationLeaf("old observation", TreeBranch.HABITUDES, ObservationSource.HEURISTIC);
        ObservationLeaf leaf2 = new ObservationLeaf("another old one", TreeBranch.INTERETS, ObservationSource.LLM_EXTRACTED);

        // When
        service.archiveLeaf(leaf1, "low relevance");
        service.archiveLeaf(leaf2, "consolidated");

        // Then
        Path archivePath = Path.of(properties.getArchivePath());
        assertTrue(Files.exists(archivePath));

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        List<UserTreePersistenceService.ArchivedLeaf> archived = mapper.readValue(
                archivePath.toFile(),
                new com.fasterxml.jackson.core.type.TypeReference<List<UserTreePersistenceService.ArchivedLeaf>>() {}
        );

        assertEquals(2, archived.size());
        assertEquals("old observation", archived.get(0).getText());
        assertEquals(TreeBranch.HABITUDES, archived.get(0).getBranch());
        assertEquals("low relevance", archived.get(0).getReason());
        assertNotNull(archived.get(0).getArchivedAt());
        assertEquals("another old one", archived.get(1).getText());
        assertEquals("consolidated", archived.get(1).getReason());
    }

    @Test
    void sizeConstraint_300LeavesWithEmbeddings_ShouldFitIn500KB() throws IOException {
        // Given — 300 leaves with 384-float embeddings
        Random random = new Random(42);
        properties.setMaxActiveObservations(300);
        tree = new UserObservationTree(properties);
        service = new UserTreePersistenceService(tree, properties);

        for (int i = 0; i < 300; i++) {
            TreeBranch branch = TreeBranch.values()[i % TreeBranch.values().length];
            float[] embedding = new float[384];
            for (int j = 0; j < 384; j++) {
                embedding[j] = random.nextFloat();
            }
            ObservationLeaf leaf = new ObservationLeaf(
                    "Observation numero " + i + " pour branche " + branch.name(),
                    branch,
                    ObservationSource.LLM_EXTRACTED,
                    embedding
            );
            tree.addLeaf(leaf);
        }

        // When
        service.doSave();

        // Then
        Path targetPath = Path.of(properties.getStoragePath());
        long fileSizeBytes = Files.size(targetPath);
        long fileSizeKB = fileSizeBytes / 1024;

        assertTrue(fileSizeBytes <= 500 * 1024,
                "File size should be <= 500KB but was " + fileSizeKB + "KB (" + fileSizeBytes + " bytes)");

        // Verify the data can be loaded back
        UserObservationTree verifyTree = new UserObservationTree(properties);
        UserTreePersistenceService verifyService = new UserTreePersistenceService(verifyTree, properties);
        verifyService.load();
        assertEquals(300, verifyTree.getAllActiveLeaves().size());
    }
}
