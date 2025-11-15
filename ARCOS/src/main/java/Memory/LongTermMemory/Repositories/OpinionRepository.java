package Memory.LongTermMemory.Repositories;

import LLM.service.RateLimiterService;
import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.Models.OpinionEntry;
import Memory.LongTermMemory.Qdrant.QdrantClientProvider;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class OpinionRepository extends BaseVectorRepository<OpinionEntry>
{
    @Autowired
    public OpinionRepository(QdrantClientProvider provider, EmbeddingModel embeddingModel) {
        super(provider.getClient(), embeddingModel, "Opinions");
    }
}
