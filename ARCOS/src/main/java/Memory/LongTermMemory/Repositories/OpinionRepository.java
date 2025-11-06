package Memory.LongTermMemory.Repositories;

import Memory.LongTermMemory.Qdrant.QdrantClientProvider;
import org.springframework.ai.embedding.EmbeddingModel;

public class OpinionRepository extends BaseVectorRepository
{
    public OpinionRepository(QdrantClientProvider provider, EmbeddingModel embeddingModel) {
        super(provider.getClient(), embeddingModel, "Opinions");
    }
}
