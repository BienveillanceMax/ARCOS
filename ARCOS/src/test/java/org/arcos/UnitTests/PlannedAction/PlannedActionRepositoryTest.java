package org.arcos.UnitTests.PlannedAction;

import org.arcos.Configuration.PlannedActionProperties;
import org.arcos.PlannedAction.Models.ActionStatus;
import org.arcos.PlannedAction.Models.PlannedActionEntry;
import org.arcos.PlannedAction.PlannedActionRepository;
import org.arcos.common.utils.ObjectCreationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlannedActionRepositoryTest {

    private PlannedActionRepository repository;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        PlannedActionProperties properties = new PlannedActionProperties();
        properties.setStoragePath(tempDir.resolve("planned-actions.json").toString());
        repository = new PlannedActionRepository(properties);
    }

    @Test
    void save_ShouldStoreEntry() {
        // Given
        PlannedActionEntry entry = ObjectCreationUtils.createSimpleReminderEntry();

        // When
        repository.save(entry);

        // Then
        PlannedActionEntry found = repository.findById(entry.getId());
        assertNotNull(found);
        assertEquals("Appeler le dentiste", found.getLabel());
    }

    @Test
    void delete_ShouldRemoveAndReturnEntry() {
        // Given
        PlannedActionEntry entry = ObjectCreationUtils.createSimpleReminderEntry();
        repository.save(entry);

        // When
        PlannedActionEntry removed = repository.delete(entry.getId());

        // Then
        assertNotNull(removed);
        assertEquals("Appeler le dentiste", removed.getLabel());
        assertNull(repository.findById(entry.getId()));
    }

    @Test
    void delete_NonExistent_ShouldReturnNull() {
        // When
        PlannedActionEntry removed = repository.delete("non-existent-id");

        // Then
        assertNull(removed);
    }

    @Test
    void findById_ShouldReturnCorrectEntry() {
        // Given
        PlannedActionEntry entry = ObjectCreationUtils.createSimpleReminderEntry();
        repository.save(entry);

        // When
        PlannedActionEntry found = repository.findById(entry.getId());

        // Then
        assertNotNull(found);
        assertEquals(entry.getId(), found.getId());
    }

    @Test
    void findById_NonExistent_ShouldReturnNull() {
        // When
        PlannedActionEntry found = repository.findById("non-existent-id");

        // Then
        assertNull(found);
    }

    @Test
    void findActiveByLabelContaining_ShouldFindMatch() {
        // Given
        PlannedActionEntry entry = ObjectCreationUtils.createSimpleReminderEntry();
        repository.save(entry);

        // When
        PlannedActionEntry found = repository.findActiveByLabelContaining("dentiste");

        // Then
        assertNotNull(found);
        assertEquals("Appeler le dentiste", found.getLabel());
    }

    @Test
    void findActiveByLabelContaining_ShouldIgnoreInactive() {
        // Given
        PlannedActionEntry entry = ObjectCreationUtils.createSimpleReminderEntry();
        entry.setStatus(ActionStatus.COMPLETED);
        repository.save(entry);

        // When
        PlannedActionEntry found = repository.findActiveByLabelContaining("dentiste");

        // Then
        assertNull(found);
    }

    @Test
    void findActiveByLabelContaining_NoMatch_ShouldReturnNull() {
        // Given
        PlannedActionEntry entry = ObjectCreationUtils.createSimpleReminderEntry();
        repository.save(entry);

        // When
        PlannedActionEntry found = repository.findActiveByLabelContaining("inexistant");

        // Then
        assertNull(found);
    }

    @Test
    void findAllActive_ShouldOnlyReturnActiveEntries() {
        // Given
        PlannedActionEntry active = ObjectCreationUtils.createSimpleReminderEntry();
        PlannedActionEntry completed = ObjectCreationUtils.createSimpleReminderEntry();
        completed.setLabel("Completed action");
        completed.setStatus(ActionStatus.COMPLETED);
        repository.save(active);
        repository.save(completed);

        // When
        List<PlannedActionEntry> result = repository.findAllActive();

        // Then
        assertEquals(1, result.size());
        assertEquals("Appeler le dentiste", result.get(0).getLabel());
    }

    @Test
    void findAll_ShouldReturnAllEntries() {
        // Given
        PlannedActionEntry entry1 = ObjectCreationUtils.createSimpleReminderEntry();
        PlannedActionEntry entry2 = ObjectCreationUtils.createDeadlineEntry();
        repository.save(entry1);
        repository.save(entry2);

        // When
        List<PlannedActionEntry> result = repository.findAll();

        // Then
        assertEquals(2, result.size());
    }

    @Test
    void persistence_ShouldSurviveReload() {
        // Given
        PlannedActionEntry entry = ObjectCreationUtils.createSimpleReminderEntry();
        repository.save(entry);

        // When — create a new repository pointing to the same file
        PlannedActionProperties properties = new PlannedActionProperties();
        properties.setStoragePath(tempDir.resolve("planned-actions.json").toString());
        PlannedActionRepository reloaded = new PlannedActionRepository(properties);
        reloaded.init();

        // Then
        PlannedActionEntry found = reloaded.findById(entry.getId());
        assertNotNull(found);
        assertEquals("Appeler le dentiste", found.getLabel());
    }
}
