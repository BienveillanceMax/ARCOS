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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;


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


        // 1. Création du filtre pour status = "PENDING"
        Points.Filter filter = Points.Filter.newBuilder()
                .addMust(Points.Condition.newBuilder()
                        .setField(Points.FieldCondition.newBuilder()
                                .setKey("status") // Le nom de la clé dans vos métadata
                                .setMatch(Points.Match.newBuilder()
                                        .setKeyword("PENDING") // Utiliser 'Keyword' pour une correspondance exacte
                                        .build())
                                .build())
                        .build())
                .build();

        // 2. Création de la requête de Scroll
        // Note : Si vous avez beaucoup de documents, il faudra gérer la pagination avec l'offset
        Points.ScrollPoints scrollRequest = Points.ScrollPoints.newBuilder()
                .setCollectionName("votre_collection")
                .setFilter(filter)
                .setLimit(100)
                .setWithPayload(io.qdrant.client.grpc.Points.WithPayloadSelector.newBuilder().setEnable(true).build())
                .build();

        List<Points.RetrievedPoint> pendingDesirePoints = new ArrayList<>();
        List<Document> pendingDesiresDocuments = new ArrayList<>();;

        // 3. Exécution
        try {
            pendingDesirePoints = qdrantClient.scrollAsync(scrollRequest).get().getResultList();
        } catch (Exception e) {
            log.error("Failed to get Pending Desires : " + e.getMessage());
        }


        //4. Traitement
        for (Points.RetrievedPoint point : pendingDesirePoints){
            pendingDesiresDocuments.add(DesireEntry.fromDesirePoint(point));
        }
        return pendingDesiresDocuments;
    }


    public Optional<Document> findById(String id) {
        try {
            Points.PointId pointId = Points.PointId.newBuilder().setUuid(id).build();

            Points.ReadConsistency readConsistency = Points.ReadConsistency.newBuilder()
                    .setFactor(1)
                    .build();

            List<Points.RetrievedPoint> points = qdrantClient.retrieveAsync(
                    collectionName,
                    pointId,
                    true,
                    true,
                    readConsistency
            ).get();

            if (points.isEmpty()) {
                return Optional.empty();
            }

            Points.RetrievedPoint point = points.get(0);
            Document document = DesireEntry.fromDesirePoint(point);

            return Optional.of(document);

        } catch (Exception e) {
            log.error("Erreur lors de la récupération du document avec l'id: " + id, e);
            return Optional.empty();
        }
    }

}
