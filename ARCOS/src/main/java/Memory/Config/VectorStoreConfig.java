package Memory.Config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class VectorStoreConfig {

    private static final int DEFAULT_EMBEDDING_DIMENSION = 768;
    private static final int DEFAULT_TOP_K = 10;

    public static final String MEMORIES_COLLECTION = "Memories";
    public static final String SUMMARIES_COLLECTION = "Summaries";
    public static final String OPINIONS_COLLECTION = "Opinions";
    public static final String DESIRES_COLLECTION = "Desires";

    @Value("${spring.ai.vectorstore.qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${spring.ai.vectorstore.qdrant.port:6334}")
    private int qdrantPort;

    @Value("${spring.ai.vectorstore.qdrant.use-tls:false}")
    private boolean useTls;

    @Value("${spring.ai.vectorstore.qdrant.api-key:}")
    private String apiKey;

    @Bean
    public QdrantClient qdrantClient() {
        QdrantGrpcClient.Builder builder = QdrantGrpcClient.newBuilder(qdrantHost, qdrantPort, useTls);

        if (apiKey != null && !apiKey.isEmpty()) {
            builder.withApiKey(apiKey);
        }

        return new QdrantClient(builder.build());
    }

    @Bean(name = "memoriesVectorStore")
    public VectorStore memoriesVectorStore(EmbeddingModel embeddingModel, QdrantClient qdrantClient) {
        return createVectorStore(embeddingModel, qdrantClient, MEMORIES_COLLECTION);
    }

    @Bean(name = "summariesVectorStore")
    public VectorStore summariesVectorStore(EmbeddingModel embeddingModel, QdrantClient qdrantClient) {
        return createVectorStore(embeddingModel, qdrantClient, SUMMARIES_COLLECTION);
    }

    @Bean(name = "opinionsVectorStore")
    public VectorStore opinionsVectorStore(EmbeddingModel embeddingModel, QdrantClient qdrantClient) {
        return createVectorStore(embeddingModel, qdrantClient, OPINIONS_COLLECTION);
    }

    @Bean(name = "desiresVectorStore")
    public VectorStore desiresVectorStore(EmbeddingModel embeddingModel, QdrantClient qdrantClient) {
        return createVectorStore(embeddingModel, qdrantClient, DESIRES_COLLECTION);
    }

    private VectorStore createVectorStore(EmbeddingModel embeddingModel,
                                          QdrantClient qdrantClient,
                                          String collectionName) {
        QdrantVectorStore.QdrantVectorStoreConfig config = QdrantVectorStore.QdrantVectorStoreConfig.builder()
                .withCollectionName(collectionName)
                .withEmbeddingDimension(DEFAULT_EMBEDDING_DIMENSION)
                .build();

        return new QdrantVectorStore(qdrantClient, config, embeddingModel);
    }
}