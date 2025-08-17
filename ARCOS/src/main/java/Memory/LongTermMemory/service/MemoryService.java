package Memory.LongTermMemory.service;


import Memory.LongTermMemory.Models.MemoryEntry;
import Memory.LongTermMemory.Models.OpinionEntry;
import Memory.LongTermMemory.Models.SearchResult;
import Memory.LongTermMemory.Models.Subject;
import Memory.LongTermMemory.Qdrant.QdrantClient;

import java.util.List;

/**
 * Service principal pour la gestion de la mémoire à long terme.
 * Orchestre les interactions entre le client Qdrant et le générateur d'embeddings.
 */
public class MemoryService
{


    // Noms des collections
    public static final String MEMORIES_COLLECTION = "Memories";
    public static final String SUMMARIES_COLLECTION = "Summaries";
    public static final String OPINIONS_COLLECTION = "Opinions";

    // Configuration par défaut
    private static final int DEFAULT_EMBEDDING_DIMENSION = 768;
    private static final int DEFAULT_TOP_K = 10;

    private final QdrantClient qdrantClient;
    private final EmbeddingGenerator embeddingGenerator;
    private final int embeddingDimension;

    /**
     * Constructeur avec configuration par défaut.
     */
    public MemoryService(String qdrantHost, int qdrantPort) {
        this(qdrantHost, qdrantPort, DEFAULT_EMBEDDING_DIMENSION);
    }

    /**
     * Constructeur avec dimension d'embedding personnalisée (compatible descendante).
     */
    public MemoryService(String qdrantHost, int qdrantPort, int embeddingDimension) {
        this.embeddingDimension = embeddingDimension;
        this.qdrantClient = new QdrantClient(qdrantHost, qdrantPort);
        this.embeddingGenerator = new EmbeddingGenerator(embeddingDimension);
    }

    /**
     * Constructeur avec générateur d'embeddings personnalisé (recommandé avec Spring AI).
     */
    public MemoryService(String qdrantHost, int qdrantPort, EmbeddingGenerator embeddingGenerator) {
        this.embeddingDimension = embeddingGenerator.getEmbeddingDimension();
        this.qdrantClient = new QdrantClient(qdrantHost, qdrantPort);
        this.embeddingGenerator = embeddingGenerator;


        // Log du type d'embedding utilisé
        if (embeddingGenerator.isMistralAvailable()) {
            System.out.println("Utilisation des embeddings Mistral AI (haute qualité sémantique)");
        } else {
            System.out.println("Utilisation des embeddings mock (développement/test)");
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

        if (memoriesOk && summariesOk && opinionsOk) {
            System.out.println("Toutes les collections ont été initialisées avec succès");
            return true;
        } else {
            System.out.println("Échec de l'initialisation d'une ou plusieurs collections");
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
    public boolean storeMemory(String content, Subject subject, double satisfaction) {
        return storeMemoryEntry(MEMORIES_COLLECTION, content, subject, satisfaction);
    }

    /**
     * Enregistre un résumé dans la collection Summaries.
     */
    public boolean storeSummary(String content, Subject subject, double satisfaction) {

        return storeMemoryEntry(SUMMARIES_COLLECTION, content, subject, satisfaction);
    }

    public boolean storeOpinion(OpinionEntry opinionEntry) {

        return storeOpinionEntry(OPINIONS_COLLECTION,opinionEntry);
    }


    /**
     * Méthode privée pour enregistrer une entrée de mémoire dans une collection donnée.
     */
    private boolean storeOpinionEntry(String collectionName, OpinionEntry opinionEntry) {
        try {

            // Génération de l'embedding
            float[] embedding = embeddingGenerator.generateEmbedding(opinionEntry.getSummary());
            opinionEntry.setEmbedding(embedding);

            // Stockage dans Qdrant

            return qdrantClient.upsertPoint(collectionName, opinionEntry);

        } catch (Exception e) {
            System.out.println("Erreur de persistance de la collection " + collectionName);
            return false;
        }
    }


    /**
     * Méthode privée pour enregistrer une entrée de mémoire dans une collection donnée.
     */
    private boolean storeMemoryEntry(String collectionName, String content, Subject subject, double satisfaction) {
        try {
            // Création de l'entrée mémoire
            MemoryEntry entry = new MemoryEntry(content, subject, satisfaction);

            // Génération de l'embedding
            float[] embedding = embeddingGenerator.generateEmbedding(content);
            entry.setEmbedding(embedding);

            // Stockage dans Qdrant

            return qdrantClient.upsertPoint(collectionName, entry);

        } catch (Exception e) {
            System.out.println("Erreur de persistance de la collection " + collectionName);
            return false;
        }
    }

    /**
     * Effectue une recherche vectorielle dans la collection Memories.
     */
    public List<SearchResult> searchMemories(String query) {
        return searchMemories(query, DEFAULT_TOP_K);
    }

    /**
     * Effectue une recherche vectorielle dans la collection Memories avec un nombre de résultats personnalisé.
     */
    public List<SearchResult> searchMemories(String query, int topK) {
        return searchInCollection(MEMORIES_COLLECTION, query, topK);
    }

    /**
     * Effectue une recherche vectorielle dans la collection Summaries.
     */
    public List<SearchResult> searchSummaries(String query) {
        return searchSummaries(query, DEFAULT_TOP_K);
    }

    /**
     * Effectue une recherche vectorielle dans la collection Summaries avec un nombre de résultats personnalisé.
     */
    public List<SearchResult> searchSummaries(String query, int topK) {
        return searchInCollection(SUMMARIES_COLLECTION, query, topK);
    }

    /**
     * Méthode privée pour effectuer une recherche dans une collection donnée.
     */
    private List<SearchResult> searchInCollection(String collectionName, String query, int topK) {
        try {
            // Génération de l'embedding pour la requête
            float[] queryEmbedding = embeddingGenerator.generateEmbedding(query);

            // Recherche vectorielle
            List<SearchResult> results = qdrantClient.searchVector(collectionName, queryEmbedding, topK);
            return results;

        } catch (Exception e) {
            System.out.println("Exception lors de la recherche dans la collection " + collectionName + " : " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Récupère une entrée spécifique par son ID dans la collection Memories.
     */
    public MemoryEntry getMemory(String memoryId) {
        return qdrantClient.getPoint(MEMORIES_COLLECTION, memoryId);
    }

    /**
     * Récupère un résumé spécifique par son ID dans la collection Summaries.
     */
    public MemoryEntry getSummary(String summaryId) {
        return qdrantClient.getPoint(SUMMARIES_COLLECTION, summaryId);
    }

    /**
     * Supprime une entrée de la collection Memories.
     */
    public boolean deleteMemory(String memoryId) {
        return qdrantClient.deletePoint(MEMORIES_COLLECTION, memoryId);
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
        List<SearchResult> memories = searchMemories(query, topKPerCollection);
        List<SearchResult> summaries = searchSummaries(query, topKPerCollection);

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

    /**
     * Classe interne pour encapsuler les résultats de recherche combinés.
     */
    public static class CombinedSearchResults
    {
        private final List<SearchResult> memories;
        private final List<SearchResult> summaries;

        public CombinedSearchResults(List<SearchResult> memories, List<SearchResult> summaries) {
            this.memories = memories;
            this.summaries = summaries;
        }

        public List<SearchResult> getMemories() {
            return memories;
        }

        public List<SearchResult> getSummaries() {
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