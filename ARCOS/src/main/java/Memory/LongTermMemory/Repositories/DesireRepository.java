package Memory.LongTermMemory.Repositories;

import LLM.service.RateLimiterService;
import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.Models.MemoryEntry;
import Memory.LongTermMemory.Qdrant.QdrantClientProvider;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.qdrant.client.grpc.Points;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Slf4j
@Repository
public class DesireRepository extends BaseVectorRepository<DesireEntry>
{

    @Autowired
    public DesireRepository(QdrantClientProvider provider, EmbeddingModel embeddingModel) {
        super(provider.getClient(), embeddingModel, "Desires");
    }

    @RateLimiter(name = "mistral_free")
    public List<Document> findPendingDesires() {
        SearchRequest searchRequest = SearchRequest.builder().query("")
                .filterExpression("status == 'PENDING'").build();    //TODO : implement
        return vectorStore.similaritySearch(searchRequest);
    }

    public Optional<Document> findById(String id) {
        try {
            // Créer l'ID du point
            Points.PointId pointId = Points.PointId.newBuilder().setUuid(id).build();

            // Récupérer le point de Qdrant
            List<Points.RetrievedPoint> points = qdrantClient.retrieveAsync(collectionName,
                    pointId,
                    true,
                    true,
                    Points.ReadConsistency.newBuilder().build()).get();

            // Vérifier si le point existe
            if (points.isEmpty()) {
                return Optional.empty();
            }

            // Convertir le point récupéré en Document
            Points.RetrievedPoint point = points.get(0);
            Document document = DesireEntry.fromDesirePoint(point);

            return Optional.of(document);


        } catch (Exception e) {
            log.error("Erreur lors de la récupération du document avec l'id: " + id, e);
            return Optional.empty();
        }
    }

}
