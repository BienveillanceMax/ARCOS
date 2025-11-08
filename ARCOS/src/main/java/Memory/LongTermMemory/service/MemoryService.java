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
        SearchRequest searchRequest = SearchRequest.builder().query(query).topK(topK).build();
        return (List<MemoryEntry>) memoryRepository.search(searchRequest).stream().map(doc -> this.fromDocument((Document) doc)).collect(Collectors.toList()); //TODO test
    }

    public MemoryEntry getMemory(String memoryId) {
        return (MemoryEntry) memoryRepository.findById(memoryId).map(doc -> this.fromDocument((Document) doc)).orElse(null); //TODO test
    }

    public void deleteMemory(String memoryId) {
        memoryRepository.delete(List.of(memoryId));
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


}
