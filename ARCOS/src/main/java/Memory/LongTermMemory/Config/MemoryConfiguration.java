package Memory.LongTermMemory.Config;

import Memory.LongTermMemory.service.EmbeddingGenerator;
import Memory.LongTermMemory.service.MemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    public EmbeddingGenerator embeddingGenerator(EmbeddingModel embeddingModel) {
        logger.info("Configuration du générateur d'embeddings Mistral AI");
        logger.info("  - Dimension configurée: {}", embeddingDimension);

        try {
            EmbeddingGenerator generator = new EmbeddingGenerator(embeddingDimension, embeddingModel, true);

            // Test de connectivité au démarrage
            if (generator.testConnection()) {
                logger.info("Mistral AI opérationnel - Embeddings haute qualité activés");
            } else {
                logger.warn("⚠️  Mistral AI non disponible - Fallback vers embeddings mock");
            }

            return generator;

        } catch (Exception e) {
            logger.error("Erreur lors de la configuration de Mistral AI: {}", e.getMessage());
            logger.info("Initialisation en mode mock uniquement");
            return new EmbeddingGenerator(embeddingDimension);
        }
    }

    /**
     * Bean pour le service principal de mémoire.
     */
    @Bean
    public MemoryService memoryService(EmbeddingGenerator embeddingGenerator) {
        logger.info("Configuration du service de mémoire");
        logger.info("  - Qdrant: {}:{}", qdrantHost, qdrantPort);
        logger.info("  - Dimension embeddings: {}", embeddingDimension);

        MemoryService memoryService = new MemoryService(qdrantHost, qdrantPort, embeddingGenerator);

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