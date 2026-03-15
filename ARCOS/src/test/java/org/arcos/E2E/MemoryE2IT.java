package org.arcos.E2E;

import org.arcos.LLM.Prompts.PromptBuilder;
import org.arcos.Memory.ConversationContext;
import org.arcos.Memory.LongTermMemory.Models.MemoryEntry;
import org.arcos.Memory.LongTermMemory.Models.Subject;
import org.arcos.Memory.LongTermMemory.service.MemoryService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MemoryE2IT extends BaseE2IT {

    @Autowired private MemoryService memoryService;
    @Autowired private PromptBuilder promptBuilder;
    @Autowired private ConversationContext context;

    private static final String JAZZ_CONTENT =
        "Hier soir, on a parlé de sa passion pour Miles Davis — " +
        "il écoute du jazz pour se détendre après le travail";
    private static final String WORK_CONTENT =
        "Pierre travaille comme architecte depuis cinq ans, " +
        "il apprécie son métier malgré la pression des délais";

    @Test
    void t1_storeAndRetrieveBySearch() {
        MemoryEntry jazz = new MemoryEntry("mem-jazz-001", JAZZ_CONTENT, Subject.SELF, 0.8,
            LocalDateTime.now(), null);
        memoryService.storeMemory(jazz);

        List<MemoryEntry> results = memoryService.searchMemories("détente et musique", 5);

        assertFalse(results.isEmpty(), "Search should return at least one result");
        assertTrue(results.stream().anyMatch(m -> "mem-jazz-001".equals(m.getId())),
            "Result should contain the stored entry by ID");
    }

    @Test
    void t2_mostRelevantMemoryRankedFirst() {
        MemoryEntry jazz = new MemoryEntry("mem-jazz-002", JAZZ_CONTENT, Subject.SELF, 0.8,
            LocalDateTime.now(), null);
        MemoryEntry work = new MemoryEntry("mem-work-002", WORK_CONTENT, Subject.SELF, 0.6,
            LocalDateTime.now(), null);
        memoryService.storeMemory(jazz);
        memoryService.storeMemory(work);

        List<MemoryEntry> results = memoryService.searchMemories("vie professionnelle et travail", 5);

        assertFalse(results.isEmpty(), "Should return results");
        assertTrue(results.stream().anyMatch(m -> "mem-work-002".equals(m.getId())),
            "Work memory should appear in results");
        // Ranking: work memory should be first (most relevant to the query)
        assertEquals("mem-work-002", results.get(0).getId(),
            "Most relevant memory should be ranked first");
    }

    @Test
    void t3_memoryInjectsIntoConversationalPrompt() {
        MemoryEntry jazz = new MemoryEntry("mem-jazz-003", JAZZ_CONTENT, Subject.SELF, 0.8,
            LocalDateTime.now(), null);
        memoryService.storeMemory(jazz);

        Prompt prompt = promptBuilder.buildConversationnalPrompt(context,
            "Qu'est-ce que j'aime écouter comme musique ?");

        assertTrue(prompt.toString().contains(JAZZ_CONTENT),
            "Prompt should contain the stored memory content (injected by QuestionAnswerAdvisor)");
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
