package Memory.LongTermMemory.Config;

import LLM.LLMClient;
import LLM.Prompts.PromptBuilder;
import Memory.LongTermMemory.Qdrant.QdrantClient;
import Memory.LongTermMemory.service.EmbeddingService;
import Memory.LongTermMemory.service.MemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import LLM.service.RateLimiterService;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration Spring pour le module de mémoire à long terme.
 * Gère l'injection de dépendances et la configuration des services.
 */
@Configuration
public class MemoryConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(MemoryConfiguration.class);

    @Value("${memory.qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${memory.qdrant.port:6333}")
    private int qdrantPort;

    @Value("${memory.embedding.dimension:1024}")
    private int embeddingDimension;

    /**
     * Bean pour le générateur d'embeddings avec Mistral AI.
     */
    @Bean
    public EmbeddingService embeddingGenerator(EmbeddingModel embeddingModel, RateLimiterService rateLimiterService) {
        logger.info("Configuration du générateur d'embeddings Mistral AI");
        logger.info("  - Dimension configurée: {}", embeddingDimension);

        try {
            EmbeddingService generator = new EmbeddingService(embeddingDimension, embeddingModel, true, rateLimiterService);
            return generator;

        } catch (Exception e) {
            logger.error("Erreur lors de la configuration de Mistral AI: {}", e.getMessage());
            logger.info("Initialisation en mode mock uniquement");
            return new EmbeddingService(embeddingDimension, rateLimiterService);
        }
    }

    @Bean
    public QdrantClient qdrantClient() {
        logger.info("Configuration du client Qdrant: {}:{}", qdrantHost, qdrantPort);
        return new QdrantClient(qdrantHost, qdrantPort);
    }

    /**
     * Bean pour le service principal de mémoire.
     */
    @Bean
    public MemoryService memoryService(QdrantClient qdrantClient, EmbeddingService embeddingService, LLMClient llmClient, PromptBuilder promptBuilder) {
        logger.info("Configuration du service de mémoire");
        logger.info("  - Dimension embeddings: {}", embeddingDimension);

        MemoryService memoryService = new MemoryService(qdrantClient, embeddingService, llmClient, promptBuilder);

        // Initialisation des collections au démarrage
        try {
            boolean initialized = memoryService.initializeCollections();
            if (initialized) {
                logger.info("✅ Collections Qdrant initialisées avec succès");
            } else {
                logger.error("❌ Échec de l'initialisation des collections Qdrant");
            }
        } catch (Exception e) {
            logger.error("Erreur lors de l'initialisation des collections: {}", e.getMessage());
        }

        return memoryService;
    }
}