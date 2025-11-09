package org.arcos.Memory.LongTermMemory.Repositories;

import Memory.LongTermMemory.Models.MemoryEntry;
import Memory.LongTermMemory.Models.Subject;
import Memory.LongTermMemory.Repositories.MemoryRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import common.gson.LocalDateTimeAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.document.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MemoryRepositoryIT {

    @Autowired
    private MemoryRepository memoryRepository;

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    private Document toDocument(MemoryEntry memoryEntry) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("entry", gson.toJson(memoryEntry));
        return new Document(memoryEntry.getId(), memoryEntry.getContent(), metadata);
    }

    @Test
    void saveAndFindById_ShouldReturnSavedEntry() {
        // Given
        MemoryEntry memoryEntry = new MemoryEntry("test content", Subject.SELF, 0.9);
        memoryRepository.save(toDocument(memoryEntry));

        // When
        Optional<Document> result = memoryRepository.findById(memoryEntry.getId());

        // Then
        assertTrue(result.isPresent());
        Document doc = result.get();
        assertEquals(memoryEntry.getContent(), doc.getMetadata().get("content"));
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

