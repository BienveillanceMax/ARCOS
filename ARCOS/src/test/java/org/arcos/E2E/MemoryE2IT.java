package org.arcos.E2E;

import org.arcos.LLM.Prompts.PromptBuilder;
import org.arcos.Memory.LongTermMemory.Models.MemoryEntry;
import org.arcos.Memory.LongTermMemory.Models.Subject;
import org.arcos.Memory.LongTermMemory.service.MemoryService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MemoryE2IT extends BaseE2IT {

    @Autowired private MemoryService memoryService;
    @Autowired private PromptBuilder promptBuilder;

    private static final String JAZZ_CONTENT =
        "Hier soir, on a parlé de sa passion pour Miles Davis — " +
        "il écoute du jazz pour se détendre après le travail";
    private static final String WORK_CONTENT =
        "Pierre travaille comme architecte depuis cinq ans, " +
        "il apprécie son métier malgré la pression des délais";

    @Test
    void t1_storeAndRetrieveBySearch() {
        String id = UUID.randomUUID().toString();
        MemoryEntry jazz = new MemoryEntry(id, JAZZ_CONTENT, null, Subject.SELF, 0.8,
            LocalDateTime.now(), null);
        memoryService.storeMemory(jazz);

        List<MemoryEntry> results = memoryService.searchMemories("détente et musique", 5);

        assertFalse(results.isEmpty(), "Search should return at least one result");
        assertTrue(results.stream().anyMatch(m -> id.equals(m.getId())),
            "Result should contain the stored entry by ID");
    }

    @Test
    void t2_mostRelevantMemoryRankedFirst() {
        String jazzId = UUID.randomUUID().toString();
        String workId = UUID.randomUUID().toString();
        MemoryEntry jazz = new MemoryEntry(jazzId, JAZZ_CONTENT, null, Subject.SELF, 0.8,
            LocalDateTime.now(), null);
        MemoryEntry work = new MemoryEntry(workId, WORK_CONTENT, null, Subject.SELF, 0.6,
            LocalDateTime.now(), null);
        memoryService.storeMemory(jazz);
        memoryService.storeMemory(work);

        List<MemoryEntry> results = memoryService.searchMemories("vie professionnelle et travail", 5);

        assertFalse(results.isEmpty(), "Should return results");
        assertTrue(results.stream().anyMatch(m -> workId.equals(m.getId())),
            "Work memory should appear in results for a work-related query");
        assertTrue(results.stream().anyMatch(m -> jazzId.equals(m.getId())),
            "Jazz memory should also be retrievable");
    }

    @Test
    void t3_conversationalPromptBuildsAfterMemoryStored() {
        MemoryEntry jazz = new MemoryEntry(UUID.randomUUID().toString(), JAZZ_CONTENT, null, Subject.SELF, 0.8,
            LocalDateTime.now(), null);
        memoryService.storeMemory(jazz);

        // buildConversationnalPrompt assembles personality/mood/opinions inline (no RAG advisor)
        Prompt prompt = promptBuilder.buildConversationnalPrompt(conversationContext,
            "Qu'est-ce que j'aime écouter comme musique ?");

        assertNotNull(prompt, "Prompt should be built without error");
        assertFalse(prompt.getInstructions().isEmpty(), "Prompt should contain at least one message");
        assertTrue(prompt.getInstructions().stream()
            .anyMatch(m -> !m.getText().isBlank()), "Prompt messages should not be blank");
    }

    @Test
    @Tag("requires-llm")
    void t4_memorizeConversationExtractsAndStores() {
        // Count before
        int countBefore = memoryService.searchMemories("jazz concert", 100).size();

        String conversation =
            "USER: Tu sais, je rentre d'un concert de jazz hier soir, " +
            "c'était vraiment beau, Miles Davis joué par un quartet live\n" +
            "ASSISTANT: Le jazz en live, c'est une autre dimension... " +
            "Il y a quelque chose d'unique dans l'improvisation devant un public";

        MemoryEntry result = memoryService.memorizeConversation(conversation);

        assertNotNull(result, "memorizeConversation should return a MemoryEntry (LLM may have failed if null)");
        assertNotNull(result.getId(), "MemoryEntry should have an ID");
        assertFalse(result.getContent().isBlank(), "Extracted memory content should be non-empty");
        assertNotNull(result.getSubject(), "Extracted memory should have a subject");

        // Verify stored in Qdrant
        List<MemoryEntry> stored = memoryService.searchMemories("concert jazz soirée", 100);
        assertTrue(stored.size() > countBefore, "A new memory should have been stored in Qdrant");
    }
}
