package Memory.LongTermMemory.Repositories;

import Memory.LongTermMemory.Qdrant.QdrantClientProvider;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Repository;

@Repository
public class MemoryRepository extends BaseVectorRepository
{
    public MemoryRepository(QdrantClientProvider provider, EmbeddingModel embeddingModel) {
        super(provider.getClient(), embeddingModel, "Memories");
    }
}