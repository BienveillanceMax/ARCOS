package org.arcos.IntegrationTests.Repositories;

import Memory.LongTermMemory.Models.OpinionEntry;
import Memory.LongTermMemory.Repositories.OpinionRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import common.gson.LocalDateTimeAdapter;
import common.utils.ObjectCreationUtils;
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
class OpinionRepositoryIT {

    @Autowired
    private OpinionRepository opinionRepository;

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    private Document toDocument(OpinionEntry opinionEntry) {
        String content = opinionEntry.getSummary() != null ? opinionEntry.getSummary() : opinionEntry.getSubject();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("entry", gson.toJson(opinionEntry));
        return new Document(opinionEntry.getId(), content, metadata);
    }

    @Test
    void saveAndFindById_ShouldReturnSavedEntry() throws InterruptedException {

        // Given
        OpinionEntry opinionEntry = ObjectCreationUtils.createOpinionEntry();
        opinionRepository.save(toDocument(opinionEntry));

        // When
        Optional<Document> result = opinionRepository.findById(opinionEntry.getId());

        // Then
        assertTrue(result.isPresent());
    }

    @Test
    void delete_ShouldRemoveEntry() throws InterruptedException {

        // Given
        OpinionEntry opinionEntry = ObjectCreationUtils.createOpinionEntry();
        opinionRepository.save(toDocument(opinionEntry));

        // When
        opinionRepository.delete(List.of(opinionEntry.getId()));
        Optional<Document> result = opinionRepository.findById(opinionEntry.getId());

        // Then
        assertFalse(result.isPresent());
    }
}
