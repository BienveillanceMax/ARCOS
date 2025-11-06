package Memory.LongTermMemory.Repositories;

import Memory.LongTermMemory.Qdrant.QdrantClientProvider;
import org.springframework.ai.embedding.EmbeddingModel;

public class DesireRepository extends BaseVectorRepository
{
    public DesireRepository(QdrantClientProvider provider, EmbeddingModel embeddingModel) {
        super(provider.getClient(), embeddingModel, "Memories");
    }
}
