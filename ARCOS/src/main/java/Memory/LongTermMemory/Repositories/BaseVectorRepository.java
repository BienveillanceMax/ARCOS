package Memory.LongTermMemory.Repositories;

import LLM.service.RateLimiterService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.RetrievedPoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.*;
import java.util.Collections.*;
import java.util.concurrent.ExecutionException;


@Slf4j
public abstract class BaseVectorRepository<T>
{

    protected final VectorStore vectorStore;
    protected final QdrantClient qdrantClient;
    protected final String collectionName;


    protected BaseVectorRepository(QdrantClient client, EmbeddingModel embeddingModel, String collectionName) {
        Boolean collectionInitialized = false;

        try {
            collectionInitialized = client.collectionExistsAsync(collectionName).get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            log.error(e.getCause().getMessage());
        }

        if (!collectionInitialized) {
            try {
                client.createCollectionAsync(collectionName,
                                Collections.VectorParams.newBuilder()
                                        .setDistance(Collections.Distance.Cosine)
                                        .setSize(1024)
                                        .build())
                        .get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            log.info("Created collection {}", collectionName);
        }

        this.vectorStore = QdrantVectorStore.builder(client, embeddingModel)
                .collectionName(collectionName)
                .build();
        this.qdrantClient = client;
        this.collectionName = collectionName;
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

    public abstract Optional<Document> findById(String id);
}

