package org.arcos.IntegrationTests.Repositories;

import Memory.LongTermMemory.Models.OpinionEntry;
import Memory.LongTermMemory.Repositories.OpinionRepository;
import common.utils.ObjectCreationUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OpinionRepositoryIT {

    @Autowired
    private OpinionRepository opinionRepository;

    private Document toDocument(OpinionEntry opinionEntry) {
        String content = opinionEntry.getNarrative() != null ? opinionEntry.getNarrative() : "Default content";
        return new Document(opinionEntry.getId(), content, opinionEntry.getPayload());
    }

    @Test
    void saveAndFindById_ShouldReturnSavedEntry() {
        // Given
        OpinionEntry opinionEntry = ObjectCreationUtils.createOpinionEntry();
        opinionRepository.save(toDocument(opinionEntry));

        // When
        Optional<Document> result = opinionRepository.findById(opinionEntry.getId());

        // Then
        assertTrue(result.isPresent());
        Document doc = result.get();
        assertEquals(opinionEntry.getId(), doc.getId());
        assertEquals(opinionEntry.getNarrative(), doc.getText());
        assertEquals(opinionEntry.getSubject(), doc.getMetadata().get("subject"));
        assertEquals(opinionEntry.getSummary(), doc.getMetadata().get("summary"));
        assertEquals(opinionEntry.getPolarity(), (Double) doc.getMetadata().get("polarity"));
        assertEquals(opinionEntry.getConfidence(), (Double) doc.getMetadata().get("confidence"));
        assertEquals(opinionEntry.getStability(), (Double) doc.getMetadata().get("stability"));
        assertEquals(opinionEntry.getMainDimension().name(), doc.getMetadata().get("mainDimension"));
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
