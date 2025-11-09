package Memory.LongTermMemory.Repositories;

import LLM.service.RateLimiterService;
import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.Models.MemoryEntry;
import Memory.LongTermMemory.Qdrant.QdrantClientProvider;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class MemoryRepository extends BaseVectorRepository<MemoryEntry>
{
    @Autowired
    public MemoryRepository(QdrantClientProvider provider, EmbeddingModel embeddingModel, RateLimiterService rateLimiterService) {
        super(provider.getClient(), embeddingModel, "Memories", rateLimiterService);
    }
}