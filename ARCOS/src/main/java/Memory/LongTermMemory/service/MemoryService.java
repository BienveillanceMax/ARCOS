package Memory.LongTermMemory.service;

import Exceptions.ResponseParsingException;
import LLM.LLMClient;
import LLM.Prompts.PromptBuilder;
import Memory.LongTermMemory.Models.MemoryEntry;
import Memory.LongTermMemory.Models.Subject;
import Memory.LongTermMemory.Repositories.MemoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MemoryService {

    private final MemoryRepository memoryRepository;
    private final LLMClient llmClient;
    private final PromptBuilder promptBuilder;
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;


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
        return memoryRepository.search(searchRequest).stream().map(this::fromDocument).collect(Collectors.toList());
    }

    public MemoryEntry getMemory(String memoryId) {
        return memoryRepository.findById(memoryId).map(this::fromDocument).orElse(null);
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
        return new Document(memoryEntry.getId(), memoryEntry.getContent(), memoryEntry.getPayload());
    }

    public MemoryEntry fromDocument(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        String id = document.getId();
        String content = document.getText();
        Subject subject = Subject.fromString((String) metadata.get("subject"));
        double satisfaction = (double) metadata.get("satisfaction");
        LocalDateTime timestamp = LocalDateTime.parse((String) metadata.get("timestamp"), TIMESTAMP_FORMATTER);
        List<Double> embeddingDouble = (List<Double>) metadata.get("embedding");
        float[] embedding = null;
        if (embeddingDouble != null) {
            embedding = new float[embeddingDouble.size()];
            for (int i = 0; i < embeddingDouble.size(); i++) {
                embedding[i] = embeddingDouble.get(i).floatValue();
            }
        }

        return new MemoryEntry(id, content, subject, satisfaction, timestamp, embedding);
    }
}
