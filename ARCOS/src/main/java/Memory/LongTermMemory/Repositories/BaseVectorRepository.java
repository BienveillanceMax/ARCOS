package Memory.LongTermMemory.Repositories;

import LLM.service.RateLimiterService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
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


    protected BaseVectorRepository(QdrantClient client, EmbeddingModel embeddingModel, String collectionName) {

        this.vectorStore  = QdrantVectorStore.builder(client,embeddingModel)
                .collectionName(collectionName)
                .build();
    }

    @RateLimiter(name = "mistral_free")
    public void save(Document document) {
        vectorStore.add(List.of(document));
    }

    public void delete(List<String> ids) {
        vectorStore.delete(ids);
    }

    public List<Document> search(SearchRequest searchRequest) {
        return vectorStore.similaritySearch(searchRequest);
    }

    @RateLimiter(name = "mistral_free")
    public Optional<Document> findById(String id) {
        SearchRequest searchRequest = SearchRequest.builder().query("")
                .topK(1)
                .filterExpression("id == '" + id + "'").build();    //tema le scotch :((((((((((( (en vrai y a moyen que ça soit hyper okay(ou pas))
        return vectorStore.similaritySearch(searchRequest).stream().findFirst();
    }

}

