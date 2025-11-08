package Memory.LongTermMemory.Repositories;

import Memory.LongTermMemory.Qdrant.QdrantClientProvider;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Repository;

@Repository
public class DesireRepository extends BaseVectorRepository
{
    public DesireRepository(QdrantClientProvider provider, EmbeddingModel embeddingModel) {
        super(provider.getClient(), embeddingModel, "Desires");
    }
}
