package Memory.LongTermMemory.Repositories;

import LLM.service.RateLimiterService;
import io.qdrant.client.QdrantClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Optional;


public abstract class BaseVectorRepository<T> {

    protected final VectorStore vectorStore;
    protected final RateLimiterService rateLimiterService;

    protected BaseVectorRepository(QdrantClient client, EmbeddingModel embeddingModel, String collectionName, RateLimiterService rateLimiterService) {

        this.rateLimiterService = rateLimiterService;
        this.vectorStore  = QdrantVectorStore.builder(client,embeddingModel)
                .collectionName(collectionName)
                .build();
    }

    public void save(Document document) {
        acquirePermit();
        vectorStore.add(List.of(document));
    }

    public void delete(List<String> ids) {
        vectorStore.delete(ids);
    }

    public List<Document> search(SearchRequest searchRequest) {
        acquirePermit();
        return vectorStore.similaritySearch(searchRequest);
    }

    public Optional<Document> findById(String id) {
        acquirePermit();  //in case even an empty strings query needs to generate an embedding
        SearchRequest searchRequest = SearchRequest.builder().query("")
                .topK(1)
                .filterExpression("id == '" + id + "'").build();    //tema le scotch :((((((((((( (en vrai y a moyen que ça soit hyper okay(ou pas))
        return vectorStore.similaritySearch(searchRequest).stream().findFirst();
    }

    protected void acquirePermit() {
        rateLimiterService.acquirePermit();
    }

}

