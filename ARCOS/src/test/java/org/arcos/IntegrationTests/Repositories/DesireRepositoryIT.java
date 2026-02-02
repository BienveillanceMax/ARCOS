package org.arcos.IntegrationTests.Repositories;

import org.arcos.Memory.LongTermMemory.Models.DesireEntry;
import org.arcos.Memory.LongTermMemory.Repositories.DesireRepository;
import org.arcos.common.utils.ObjectCreationUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.document.Document;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DesireRepositoryIT {

    @Autowired
    private DesireRepository desireRepository;

    private Document toDocument(DesireEntry desireEntry) {
        String content = desireEntry.getDescription();
        return new Document(desireEntry.getId(), content, desireEntry.getPayload());
    }

    @Test
    void saveAndFindById_ShouldReturnSavedEntry() {
        // Given
        DesireEntry desireEntry = ObjectCreationUtils.createIntensePendingDesireEntry(UUID.randomUUID().toString());
        desireRepository.save(toDocument(desireEntry));

        // When
        Optional<Document> result = desireRepository.findById(desireEntry.getId());

        // Then
        assertTrue(result.isPresent());
        Document doc = result.get();
        assertEquals(desireEntry.getId(), doc.getId());
        assertEquals(desireEntry.getDescription(), doc.getText());
        assertEquals(desireEntry.getLabel(), doc.getMetadata().get("label"));
        assertEquals(desireEntry.getReasoning(), doc.getMetadata().get("reasoning"));
        assertEquals(desireEntry.getIntensity(), (Double) doc.getMetadata().get("intensity"));
        assertEquals(desireEntry.getStatus().name(), doc.getMetadata().get("status"));
    }

    @Test
    void delete_ShouldRemoveEntry() {
        // Given
        DesireEntry desireEntry = ObjectCreationUtils.createIntensePendingDesireEntry(UUID.randomUUID().toString());
        desireRepository.save(toDocument(desireEntry));

        // When
        desireRepository.delete(List.of(desireEntry.getId()));
        Optional<Document> result = desireRepository.findById(desireEntry.getId());

        // Then
        assertFalse(result.isPresent());
    }


}


