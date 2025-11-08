package Memory.LongTermMemory.Repositories;

import io.qdrant.client.QdrantClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;

import java.util.List;
import java.util.Optional;


public abstract class BaseVectorRepository<T> {

    protected final VectorStore vectorStore;

    protected BaseVectorRepository(QdrantClient client, EmbeddingModel embeddingModel, String collectionName) {
        this.vectorStore  = QdrantVectorStore.builder(client,embeddingModel)
                .collectionName(collectionName)
                .build();
    }

    public void save(Document document) {
        vectorStore.add(List.of(document));
    }

    public void delete(List<String> ids) {
        vectorStore.delete(ids);
    }

    public List<Document> search(SearchRequest searchRequest) {
        return vectorStore.similaritySearch(searchRequest);
    }

    public Optional<Document> findById(String id) {
        var searchRequest = SearchRequest.defaults()
                .withFilterExpression("id == '" + id + "'");
        return vectorStore.similaritySearch(searchRequest).stream().findFirst();
    }
}

