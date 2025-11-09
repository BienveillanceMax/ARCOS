package org.arcos.Memory.LongTermMemory.Repositories;

import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.Repositories.DesireRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import common.gson.LocalDateTimeAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.document.Document;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DesireRepositoryIT {

    @Autowired
    private DesireRepository desireRepository;

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    private Document toDocument(DesireEntry desireEntry) {
        String content = desireEntry.getLabel() + ". " + desireEntry.getDescription();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("entry", gson.toJson(desireEntry));
        return new Document(desireEntry.getId(), content, metadata);
    }

    @Test
    void saveAndFindById_ShouldReturnSavedEntry() {
        // Given
        DesireEntry desireEntry = createDesireEntry();
        desireRepository.save(toDocument(desireEntry));

        // When
        Optional<Document> result = desireRepository.findById(desireEntry.getId());

        // Then
        assertTrue(result.isPresent());
    }

    @Test
    void delete_ShouldRemoveEntry() {
        // Given
        DesireEntry desireEntry = createDesireEntry();
        desireRepository.save(toDocument(desireEntry));

        // When
        desireRepository.delete(List.of(desireEntry.getId()));
        Optional<Document> result = desireRepository.findById(desireEntry.getId());

        // Then
        assertFalse(result.isPresent());
    }

    DesireEntry createDesireEntry() {
        DesireEntry desireEntry = new DesireEntry();
        desireEntry.setId(UUID.randomUUID().toString());
        desireEntry.setLabel("test label");
        desireEntry.setDescription("description");
        desireEntry.setEmbedding(new float[1024]); //todo change to correct embedding dimension
        desireEntry.setReasoning("reasoning");
        desireEntry.setIntensity(0.55);
        desireEntry.setStatus(DesireEntry.Status.ACTIVE);
        desireEntry.setCreatedAt(LocalDateTime.now());
        return desireEntry;
    }
}

