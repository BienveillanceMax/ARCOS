package Memory.LongTermMemory.Repositories;

import io.qdrant.client.QdrantClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;



public abstract class BaseVectorRepository {

    protected final QdrantVectorStore vectorStore;
    protected BaseVectorRepository(QdrantClient client, EmbeddingModel embeddingModel, String collectionName) {
        this.vectorStore  = QdrantVectorStore.builder(client,embeddingModel)
                .collectionName(collectionName)
                .build();
    }
}

