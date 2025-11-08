package Memory.LongTermMemory.service;

import Exceptions.ResponseParsingException;
import LLM.LLMClient;
import LLM.Prompts.PromptBuilder;
import Memory.LongTermMemory.Models.MemoryEntry;
import Memory.LongTermMemory.Models.Subject;
import Memory.LongTermMemory.Repositories.MemoryRepository;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MemoryService {

    private final MemoryRepository memoryRepository;
    private final LLMClient llmClient;
    private final PromptBuilder promptBuilder;
    private final Gson gson = new Gson();

    public MemoryService(MemoryRepository memoryRepository, LLMClient llmClient, PromptBuilder promptBuilder) {
        this.memoryRepository = memoryRepository;
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
    }

    public void storeMemory(MemoryEntry memoryEntry) {
        memoryRepository.save(toDocument(memoryEntry));
    }

    public void storeSummary(String content, Subject subject, double satisfaction) {
        MemoryEntry entry = new MemoryEntry(content, subject, satisfaction);
        memoryRepository.save(toDocument(entry));
    }

    public List<MemoryEntry> searchMemories(String query) {
        return searchMemories(query, 10);
    }

    public List<MemoryEntry> searchMemories(String query, int topK) {
        SearchRequest searchRequest = SearchRequest.query(query).withTopK(topK);
        return memoryRepository.search(searchRequest).stream().map(this::fromDocument).collect(Collectors.toList());
    }

    public List<MemoryEntry> searchSummaries(String query) {
        return searchSummaries(query, 10);
    }

    public List<MemoryEntry> searchSummaries(String query, int topK) {
        SearchRequest searchRequest = SearchRequest.query(query).withTopK(topK);
        // This assumes summaries are in a different collection, which is handled by a different repository.
        // For this refactoring, we'll assume a single memory repository for simplicity.
        // If summaries need to be in a separate collection, a SummaryRepository would be needed.
        return memoryRepository.search(searchRequest).stream().map(this::fromDocument).collect(Collectors.toList());
    }

    public MemoryEntry getMemory(String memoryId) {
        return memoryRepository.findById(memoryId).map(this::fromDocument).orElse(null);
    }

    public void deleteMemory(String memoryId) {
        memoryRepository.delete(List.of(memoryId));
    }

    public CombinedSearchResults searchAll(String query, int topKPerCollection) {
        List<MemoryEntry> memories = searchMemories(query, topKPerCollection);
        List<MemoryEntry> summaries = searchSummaries(query, topKPerCollection);
        return new CombinedSearchResults(memories, summaries);
    }

    public CombinedSearchResults searchAll(String query) {
        return searchAll(query, 10);
    }

    public MemoryEntry memorizeConversation(String conversation) throws ResponseParsingException {
        // This method's implementation depends on how the LLM client generates a memory entry.
        // For this refactoring, we'll assume the LLM client returns a MemoryEntry object.
        MemoryEntry memoryEntry = llmClient.generateMemoryResponse(promptBuilder.buildMemoryPrompt(conversation));
        storeMemory(memoryEntry);
        return memoryEntry;
    }

    private Document toDocument(MemoryEntry memoryEntry) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("entry", gson.toJson(memoryEntry));
        return new Document(memoryEntry.getId(), memoryEntry.getContent(), metadata);
    }

    private MemoryEntry fromDocument(Document document) {
        String json = (String) document.getMetadata().get("entry");
        return gson.fromJson(json, MemoryEntry.class);
    }

    public static class CombinedSearchResults {
        private final List<MemoryEntry> memories;
        private final List<MemoryEntry> summaries;

        public CombinedSearchResults(List<MemoryEntry> memories, List<MemoryEntry> summaries) {
            this.memories = memories;
            this.summaries = summaries;
        }

        public List<MemoryEntry> getMemories() {
            return memories;
        }

        public List<MemoryEntry> getSummaries() {
            return summaries;
        }

        public int getTotalResults() {
            return memories.size() + summaries.size();
        }

        @Override
        public String toString() {
            return String.format("CombinedSearchResults{memories=%d, summaries=%d}",
                    memories.size(), summaries.size());
        }
    }
}
