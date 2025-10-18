package Memory.LongTermMemory.service;


import Exceptions.ResponseParsingException;
import LLM.LLMClient;
import LLM.LLMResponseParser;
import Memory.LongTermMemory.Models.*;
import Memory.LongTermMemory.Models.SearchResult.SearchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service principal pour la gestion de la mémoire à long terme.
 * Orchestre les interactions entre le client Qdrant et le générateur d'embeddings.
 */
@Component
@Slf4j
public class MemoryService
{


    // Noms des collections
    public static final String MEMORIES_COLLECTION = "Memories";
    public static final String SUMMARIES_COLLECTION = "Summaries";
    public static final String OPINIONS_COLLECTION = "Opinions";
    public static final String DESIRES_COLLECTION = "Desires";

    // Configuration par défaut
    private static final int DEFAULT_EMBEDDING_DIMENSION = 768;
    private static final int DEFAULT_TOP_K = 10;

    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final LLMClient llmClient;
    private final LLMResponseParser llmResponseParser;

    /**
     * Constructeur principal du service de mémoire.
     */
    public MemoryService(VectorStore vectorStore, EmbeddingModel embeddingModel, LLMClient llmClient, LLMResponseParser llmResponseParser) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.llmClient = llmClient;
        this.llmResponseParser = llmResponseParser;
    }


    /**
     * Enregistre un souvenir dans la collection Memories.
     */
    public void storeMemory(MemoryEntry memoryEntry) {
        vectorStore.add(List.of(toDocument(memoryEntry)));
    }

    private Document toDocument(MemoryEntry memoryEntry) {
        return new Document(memoryEntry.getContent(), Map.of(
                "subject", memoryEntry.getSubject().toString(),
                "satisfaction", memoryEntry.getSatisfaction(),
                "timestamp", memoryEntry.getTimestamp().toString()
        ));
    }




    /**
     * Effectue une recherche vectorielle dans la collection Memories.
     */
    public List<SearchResult<MemoryEntry>> searchMemories(String query) {
        return searchMemories(query, DEFAULT_TOP_K);
    }

    /**
     * Effectue une recherche vectorielle dans la collection Memories avec un nombre de résultats personnalisé.
     */
    public List<SearchResult<MemoryEntry>> searchMemories(String query, int topK) {
        return vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(topK).build())
                .stream()
                .map(this::toMemoryEntry)
                .map(entry -> new SearchResult<>(entry, 0.0))
                .collect(Collectors.toList());
    }

    private MemoryEntry toMemoryEntry(Document document) {
        MemoryEntry entry = new MemoryEntry();
        entry.setContent(document.getText());
        entry.setSubject(Subject.fromString((String) document.getMetadata().get("subject")));
        entry.setSatisfaction((Double) document.getMetadata().get("satisfaction"));
        entry.setTimestamp(java.time.LocalDateTime.parse((String) document.getMetadata().get("timestamp")));
        return entry;
    }




    /**
     * Classe interne pour encapsuler les résultats de recherche combinés.
     */
    public static class CombinedSearchResults
    {
        private final List<SearchResult<MemoryEntry>> memories;
        private final List<SearchResult<MemoryEntry>> summaries;

        public CombinedSearchResults(List<SearchResult<MemoryEntry>> memories, List<SearchResult<MemoryEntry>> summaries) {
            this.memories = memories;
            this.summaries = summaries;
        }

        public List<SearchResult<MemoryEntry>> getMemories() {
            return memories;
        }

        public List<SearchResult<MemoryEntry>> getSummaries() {
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