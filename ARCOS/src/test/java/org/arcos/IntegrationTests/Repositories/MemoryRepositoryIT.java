package org.arcos.IntegrationTests.Repositories;

import Memory.LongTermMemory.Models.MemoryEntry;
import Memory.LongTermMemory.Models.Subject;
import Memory.LongTermMemory.Repositories.MemoryRepository;
import common.utils.ObjectCreationUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.document.Document;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MemoryRepositoryIT {

    @Autowired
    private MemoryRepository memoryRepository;

    private Document toDocument(MemoryEntry memoryEntry) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("subject", memoryEntry.getSubject().name());
        metadata.put("satisfaction", memoryEntry.getSatisfaction());
        metadata.put("timestamp", memoryEntry.getTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return new Document(memoryEntry.getId(), memoryEntry.getContent(), metadata);
    }

    @Test
    void saveAndFindById_ShouldReturnSavedEntry() {
        // Given
        MemoryEntry memoryEntry = ObjectCreationUtils.createMemoryEntry();
        memoryRepository.save(toDocument(memoryEntry));

        // When
        Optional<Document> result = memoryRepository.findById(memoryEntry.getId());

        // Then
        assertTrue(result.isPresent());
        Document doc = result.get();
        assertEquals(memoryEntry.getId(), doc.getId());
        assertEquals(memoryEntry.getContent(), doc.getText());
        assertEquals(memoryEntry.getSubject().name(), doc.getMetadata().get("subject"));
        assertEquals(memoryEntry.getSatisfaction(), (Double) doc.getMetadata().get("satisfaction"));
    }

    @Test
    void delete_ShouldRemoveEntry() {
        // Given
        MemoryEntry memoryEntry = new MemoryEntry("test content", Subject.SELF, 0.9);
        memoryRepository.save(toDocument(memoryEntry));

        // When
        memoryRepository.delete(List.of(memoryEntry.getId()));
        Optional<Document> result = memoryRepository.findById(memoryEntry.getId());

        // Then
        assertFalse(result.isPresent());
    }
}
