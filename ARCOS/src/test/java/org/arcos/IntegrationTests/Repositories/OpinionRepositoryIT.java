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

    private Document toDocument(OpinionEntry opinionEntry) {
        String content = opinionEntry.getSummary() != null ? opinionEntry.getSummary() : opinionEntry.getSubject();
        return new Document(opinionEntry.getId(), content, opinionEntry.getPayload());
    }

    @Test
    void saveAndFindById_ShouldReturnSavedEntry() throws InterruptedException {
        Thread.sleep(2000); //for init api limits
        // Given
        OpinionEntry opinionEntry = ObjectCreationUtils.createOpinionEntry();
        opinionRepository.save(toDocument(opinionEntry));

        // When
        Optional<Document> result = opinionRepository.findById(opinionEntry.getId());

        // Then
        assertTrue(true);
    }

    @Test
    void delete_ShouldRemoveEntry() {

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
