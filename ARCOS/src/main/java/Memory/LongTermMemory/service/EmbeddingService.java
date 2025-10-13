package Memory.LongTermMemory.service;


import LLM.service.RateLimiterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;
import java.util.Random;

/**
 * Générateur d'embeddings pour les textes utilisant Mistral AI via Spring AI.
 * Supporte un mode fallback avec embeddings mock en cas d'indisponibilité de l'API.
 */
public class EmbeddingService
{

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingService.class);

    private final int embeddingDimension;
    private final EmbeddingModel embeddingModel;
    private final boolean fallbackToMock;
    private final Random random;
    private final RateLimiterService rateLimiterService;

    /**
     * Constructeur avec modèle Mistral AI.
     */
    public EmbeddingService(int embeddingDimension, EmbeddingModel embeddingModel, RateLimiterService rateLimiterService) {
        this(embeddingDimension, embeddingModel, true, rateLimiterService);
    }

    /**
     * Constructeur complet avec option de fallback.
     */
    public EmbeddingService(int embeddingDimension, EmbeddingModel embeddingModel, boolean fallbackToMock, RateLimiterService rateLimiterService) {
        this.embeddingDimension = embeddingDimension;
        this.embeddingModel = embeddingModel;
        this.fallbackToMock = fallbackToMock;
        this.random = new Random();
        this.rateLimiterService = rateLimiterService;

        logger.info("EmbeddingGenerator initialisé avec Mistral AI - dimension: {}, fallback: {}",
                embeddingDimension, fallbackToMock);
    }

    /**
     * Constructeur pour mode mock uniquement (compatibilité descendante).
     */
    public EmbeddingService(int embeddingDimension, RateLimiterService rateLimiterService) {
        this.embeddingDimension = embeddingDimension;
        this.embeddingModel = null;
        this.fallbackToMock = true;
        this.random = new Random();
        this.rateLimiterService = rateLimiterService;

        logger.warn("EmbeddingGenerator initialisé en mode MOCK uniquement - dimension: {}", embeddingDimension);
    }

    /**
     * Génère un embedding pour le texte donné.
     * Utilise Mistral AI en priorité, avec fallback vers mock si nécessaire.
     */
    public float[] generateEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            logger.warn("Texte vide fourni pour l'embedding, génération d'un vecteur zéro");
            return new float[embeddingDimension];
        }

        // Tentative avec Mistral AI
        if (embeddingModel != null) {
            try {
                return generateMistralEmbedding(text);
            } catch (Exception e) {
                logger.warn("Erreur lors de la génération d'embedding avec Mistral AI: {}", e.getMessage());

                if (!fallbackToMock) {
                    throw new RuntimeException("Impossible de générer l'embedding avec Mistral AI", e);
                }

                logger.info("Utilisation du fallback mock pour le texte: '{}'",
                        text.length() > 50 ? text.substring(0, 47) + "..." : text);
            }
        }

        // Fallback vers mock
        return generateMockEmbedding(text);
    }

    /**
     * Génère un embedding via l'API Mistral AI.
     */
    private float[] generateMistralEmbedding(String text) {
        logger.debug("Génération d'embedding Mistral AI pour: '{}'",
                text.length() > 50 ? text.substring(0, 47) + "..." : text);

        // Préparation de la requête
        EmbeddingRequest request = new EmbeddingRequest(List.of(text), null);

        // Appel à l'API Mistral
        rateLimiterService.acquirePermit();
        EmbeddingResponse response = embeddingModel.call(request);

        if (response.getResults().isEmpty()) {
            throw new RuntimeException("Aucun résultat retourné par Mistral AI");
        }

        // Extraction de l'embedding
        float[] embeddingList = response.getResults().get(0).getOutput();
        float[] embedding = new float[embeddingList.length];

        for (int i = 0; i < embeddingList.length; i++) {
            embedding[i] = embeddingList[i];
        }

        logger.debug("Embedding Mistral généré avec succès - dimension: {}", embedding.length);

        // Vérification de la dimension attendue
        if (embedding.length != embeddingDimension) {
            logger.warn("Dimension d'embedding inattendue: {} au lieu de {}", embedding.length, embeddingDimension);
            // Pas d'erreur, on adapte la dimension configurée
        }

        return embedding;
    }

    /**
     * Génère un embedding mock (fallback).
     * MOCK IMPLEMENTATION: génère un vecteur aléatoire normalisé basé sur le hash du texte.
     */
    private float[] generateMockEmbedding(String text) {
        logger.debug("Génération d'embedding MOCK pour: '{}'",
                text.length() > 50 ? text.substring(0, 47) + "..." : text);

        // Génération d'un vecteur aléatoire basé sur le hash du texte pour la reproductibilité
        Random textRandom = new Random(text.hashCode());
        float[] embedding = new float[embeddingDimension];

        // Génération de valeurs aléatoires
        double norm = 0.0;
        for (int i = 0; i < embeddingDimension; i++) {
            embedding[i] = (float) textRandom.nextGaussian();
            norm += embedding[i] * embedding[i];
        }

        // Normalisation du vecteur pour avoir une norme de 1
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < embeddingDimension; i++) {
                embedding[i] /= norm;
            }
        }

        return embedding;
    }

    /**
     * Génère des embeddings pour plusieurs textes en une seule requête (batch).
     * Optimisation pour les opérations en lot.
     */
    public float[][] generateBatchEmbeddings(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new float[0][];
        }

        if (embeddingModel != null) {
            try {
                return generateMistralBatchEmbeddings(texts);
            } catch (Exception e) {
                logger.warn("Erreur lors de la génération batch avec Mistral AI: {}", e.getMessage());

                if (!fallbackToMock) {
                    throw new RuntimeException("Impossible de générer les embeddings batch avec Mistral AI", e);
                }
            }
        }

        // Fallback : génération individuelle
        float[][] embeddings = new float[texts.size()][];
        for (int i = 0; i < texts.size(); i++) {
            embeddings[i] = generateMockEmbedding(texts.get(i));
        }

        return embeddings;
    }

    /**
     * Génère des embeddings batch via Mistral AI.
     */
    private float[][] generateMistralBatchEmbeddings(List<String> texts) {
        logger.debug("Génération d'embeddings batch Mistral AI pour {} textes", texts.size());

        EmbeddingRequest request = new EmbeddingRequest(texts, null);
        rateLimiterService.acquirePermit();
        EmbeddingResponse response = embeddingModel.call(request);

        if (response.getResults().size() != texts.size()) {
            throw new RuntimeException("Nombre de résultats incompatible avec le nombre de textes");
        }

        float[][] embeddings = new float[texts.size()][];

        for (int i = 0; i < response.getResults().size(); i++) {
            float[] embeddingList = response.getResults().get(i).getOutput();
            float[] embedding = new float[embeddingList.length];

            for (int j = 0; j < embeddingList.length; j++) {
                embedding[j] = embeddingList[j];
            }

            embeddings[i] = embedding;
        }

        logger.debug("Batch embeddings Mistral générés avec succès");
        return embeddings;
    }

    /**
     * Calcule la similarité cosine entre deux embeddings.
     */
    public double cosineSimilarity(float[] embedding1, float[] embedding2) {
        if (embedding1.length != embedding2.length) {
            throw new IllegalArgumentException("Les embeddings doivent avoir la même dimension");
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < embedding1.length; i++) {
            dotProduct += embedding1[i] * embedding2[i];
            norm1 += embedding1[i] * embedding1[i];
            norm2 += embedding2[i] * embedding2[i];
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * Teste la connectivité avec Mistral AI.
     */
    public boolean testConnection() {
        if (embeddingModel == null) {
            logger.info("Pas de modèle Mistral configuré - mode mock uniquement");
            return false;
        }

        try {
            generateMistralEmbedding("Test de connectivité");
            logger.info("Connexion Mistral AI : ✅ OK");
            return true;
        } catch (Exception e) {
            logger.warn("Connexion Mistral AI : ❌ Échec - {}", e.getMessage());
            return false;
        }
    }

    public int getEmbeddingDimension() {
        return embeddingDimension;
    }

    public boolean isMistralAvailable() {
        return embeddingModel != null;
    }

    public boolean isFallbackEnabled() {
        return fallbackToMock;
    }
}