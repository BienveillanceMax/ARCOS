package Memory.LongTermMemory.Repositories;

import LLM.service.RateLimiterService;
import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.Qdrant.QdrantClientProvider;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public class DesireRepository extends BaseVectorRepository<DesireEntry>
{

    @Autowired
    public DesireRepository(QdrantClientProvider provider, EmbeddingModel embeddingModel, RateLimiterService rateLimiterService) {
        super(provider.getClient(), embeddingModel, "Desires", rateLimiterService);
    }

    public List<Document> findPendingDesires() {
        acquirePermit();
        SearchRequest searchRequest = SearchRequest.builder().query("")
                .filterExpression("status == 'PENDING'").build();    //TODO : implement
        return vectorStore.similaritySearch(searchRequest);
    }
}
