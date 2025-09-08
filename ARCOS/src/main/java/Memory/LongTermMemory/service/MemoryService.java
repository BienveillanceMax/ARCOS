package Memory.LongTermMemory.service;


import Exceptions.ResponseParsingException;
import LLM.LLMClient;
import LLM.LLMResponseParser;
import LLM.Prompts.PromptBuilder;
import Memory.LongTermMemory.Models.*;
import Memory.LongTermMemory.Models.SearchResult.SearchResult;
import Memory.LongTermMemory.Qdrant.QdrantClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.function.Function;

/**
 * Service principal pour la gestion de la mémoire à long terme.
 * Orchestre les interactions entre le client Qdrant et le générateur d'embeddings.
 */
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

    private final QdrantClient qdrantClient;
    private final EmbeddingService embeddingService;
    private final LLMClient llmClient;
    private final PromptBuilder promptBuilder;
    private final LLMResponseParser llmResponseParser;

    private final int embeddingDimension;

    /**
     * Constructeur principal du service de mémoire.
     */
    public MemoryService(QdrantClient qdrantClient, EmbeddingService embeddingService, LLMClient llmClient, PromptBuilder promptBuilder, LLMResponseParser llmResponseParser) {
        this.embeddingDimension = embeddingService.getEmbeddingDimension();
        this.qdrantClient = qdrantClient;
        this.embeddingService = embeddingService;
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.llmResponseParser = llmResponseParser;

        // Log du type d'embedding utilisé
        if (embeddingService.isMistralAvailable()) {
            log.info("Utilisation des embeddings Mistral AI (haute qualité sémantique)");
        } else {
            log.info("Utilisation des embeddings mock (développement/test)");
        }
    }

    /**
     * Initialise les collections nécessaires dans Qdrant.
     * À appeler au démarrage du service.
     */
    public boolean initializeCollections() {

        boolean memoriesOk = initializeCollection(MEMORIES_COLLECTION);
        boolean summariesOk = initializeCollection(SUMMARIES_COLLECTION);
        boolean opinionsOk = initializeCollection(OPINIONS_COLLECTION);
        boolean desiresOk = initializeCollection(DESIRES_COLLECTION);

        if (memoriesOk && summariesOk && opinionsOk && desiresOk) {
            log.info("Toutes les collections ont été initialisées avec succès");
            return true;
        } else {
            log.error("Échec de l'initialisation d'une ou plusieurs collections");
            return false;
        }
    }

    private boolean initializeCollection(String collectionName) {
        if (qdrantClient.collectionExists(collectionName)) {
            return true;
        } else {
            return qdrantClient.createCollection(collectionName, embeddingDimension);
        }
    }

    /**
     * Enregistre un souvenir dans la collection Memories.
     */
    public boolean storeMemory(MemoryEntry memoryEntry) {
        return storeEntry(MEMORIES_COLLECTION, memoryEntry, memoryEntry.getContent());
    }


    public boolean storeDesire(DesireEntry createdDesire) {
        String textToEmbed = createdDesire.getLabel() + ". " + createdDesire.getDescription();
        return storeEntry(DESIRES_COLLECTION, createdDesire, textToEmbed);
    }


    public DesireEntry getDesire(String associatedDesireId) {
        return qdrantClient.getPoint(DESIRES_COLLECTION, associatedDesireId, (jsonNode) -> qdrantClient.parseDesireEntry(jsonNode).getEntry());
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<DesireEntry> getPendingDesires() {
        ObjectNode filter = objectMapper.createObjectNode();
        ObjectNode must = filter.putArray("must").addObject();
        must.put("key", "status");
        ObjectNode match = must.putObject("match");
        match.put("value", DesireEntry.Status.PENDING.name());

        return qdrantClient.scroll(DESIRES_COLLECTION, filter, qdrantClient::parseDesireEntryFromPoint);
    }


    /**
     * Enregistre un résumé dans la collection Summaries.
     */
    public boolean storeSummary(String content, Subject subject, double satisfaction) {
        MemoryEntry entry = new MemoryEntry(content, subject, satisfaction);
        return storeEntry(SUMMARIES_COLLECTION, entry, content);
    }


    /**
     * Méthode privée pour enregistrer une entrée de mémoire dans une collection donnée.
     */
    public boolean storeOpinion(OpinionEntry opinionEntry) {
        return storeEntry(OPINIONS_COLLECTION, opinionEntry, opinionEntry.getSummary());
    }


    private <T extends QdrantEntry> boolean storeEntry(String collectionName, T entry, String textToEmbed) {
        try {
            // Génération de l'embedding
            float[] embedding = embeddingService.generateEmbedding(textToEmbed);
            entry.setEmbedding(embedding);

            // Stockage dans Qdrant
            return qdrantClient.upsertPoint(collectionName, entry);

        } catch (Exception e) {
            log.error("Erreur de persistance de la collection {}", collectionName, e);
            return false;
        }
    }

    /**
     * Effectue une recherche vectorielle dans la collection Opinions.
     */
    public List<SearchResult<OpinionEntry>> searchOpinions(String query) {
        return searchOpinions(query, DEFAULT_TOP_K);
    }

    public List<SearchResult<OpinionEntry>> searchOpinions(String query, int topK) {
        return searchInCollection(OPINIONS_COLLECTION, query, topK, qdrantClient::parseOpinionEntry);
    }

    public List<SearchResult<DesireEntry>> searchDesires(String query) {
        return searchDesires(query, DEFAULT_TOP_K);
    }

    public List<SearchResult<DesireEntry>> searchDesires(String query, int topK) {
        return searchInCollection(DESIRES_COLLECTION, query, topK, qdrantClient::parseDesireEntry);
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
        return searchInCollection(MEMORIES_COLLECTION, query, topK, qdrantClient::parseMemoryEntry);
    }

    /**
     * Effectue une recherche vectorielle dans la collection Summaries.
     */
    public List<SearchResult<MemoryEntry>> searchSummaries(String query) {
        return searchSummaries(query, DEFAULT_TOP_K);
    }

    /**
     * Effectue une recherche vectorielle dans la collection Summaries avec un nombre de résultats personnalisé.
     */
    public List<SearchResult<MemoryEntry>> searchSummaries(String query, int topK) {
        return searchInCollection(SUMMARIES_COLLECTION, query, topK, qdrantClient::parseMemoryEntry);
    }


    /**
     * Méthode privée pour effectuer une recherche dans une collection donnée.
     */
    private <T> List<SearchResult<T>> searchInCollection(String collectionName, String query, int topK, Function<com.fasterxml.jackson.databind.JsonNode, SearchResult<T>> parser) {
        try {
            // Génération de l'embedding pour la requête
            float[] queryEmbedding = embeddingService.generateEmbedding(query);

            // Recherche vectorielle
            return qdrantClient.search(collectionName, queryEmbedding, topK, parser);

        } catch (Exception e) {
            log.error("Exception lors de la recherche dans la collection {} : {}", collectionName, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Récupère une entrée spécifique par son ID dans la collection Memories.
     */
    public MemoryEntry getMemory(String memoryId) {
        return qdrantClient.getPoint(MEMORIES_COLLECTION, memoryId, (jsonNode) -> qdrantClient.parseMemoryEntry(jsonNode).getEntry());
    }

    /**
     * Récupère un résumé spécifique par son ID dans la collection Summaries.
     */
    public MemoryEntry getSummary(String summaryId) {
        return qdrantClient.getPoint(SUMMARIES_COLLECTION, summaryId, (jsonNode) -> qdrantClient.parseMemoryEntry(jsonNode).getEntry());
    }

    /**
     * Supprime une entrée de la collection Memories.
     */
    public boolean deleteMemory(String memoryId) {
        return qdrantClient.deletePoint(MEMORIES_COLLECTION, memoryId);
    }

    /**
     * Supprime une entrée de la collection Opinions.
     */
    public boolean deleteOpinion(String opinionId) {
        return qdrantClient.deletePoint(OPINIONS_COLLECTION, opinionId);
    }

    /**
     * Supprime un résumé de la collection Summaries.
     */
    public boolean deleteSummary(String summaryId) {

        return qdrantClient.deletePoint(SUMMARIES_COLLECTION, summaryId);
    }

    /**
     * Recherche dans les deux collections simultanément et combine les résultats.
     */
    public CombinedSearchResults searchAll(String query, int topKPerCollection) {
        List<SearchResult<MemoryEntry>> memories = searchMemories(query, topKPerCollection);
        List<SearchResult<MemoryEntry>> summaries = searchSummaries(query, topKPerCollection);

        return new CombinedSearchResults(memories, summaries);
    }

    /**
     * Recherche dans les deux collections avec les paramètres par défaut.
     */
    public CombinedSearchResults searchAll(String query) {
        return searchAll(query, DEFAULT_TOP_K);
    }

    /**
     * Ferme proprement le service et libère les ressources.
     */
    public void close() {
        if (qdrantClient != null) {
            qdrantClient.close();
        }
    }

    public int getEmbeddingDimension() {
        return embeddingDimension;
    }

    public MemoryEntry memorizeConversation(String conversation) throws ResponseParsingException {

        MemoryEntry memoryEntry = llmResponseParser.parseMemoryFromMistralResponse(llmClient.generateMemoryResponse(promptBuilder.buildMemoryPrompt(conversation)));
        memoryEntry.setEmbedding(embeddingService.generateEmbedding(memoryEntry.getContent()));
        storeMemory(memoryEntry);
        return memoryEntry;
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