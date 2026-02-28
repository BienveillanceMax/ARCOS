package org.arcos.Memory.LongTermMemory.Repositories;

import org.arcos.Configuration.QdrantProperties;
import org.arcos.Memory.LongTermMemory.Models.OpinionEntry;
import org.arcos.Memory.LongTermMemory.Qdrant.QdrantClientProvider;
import io.qdrant.client.grpc.Points;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
public class OpinionRepository extends BaseVectorRepository<OpinionEntry>
{
    @Autowired
    public OpinionRepository(QdrantClientProvider provider, EmbeddingModel embeddingModel, QdrantProperties qdrantProperties) {
        super(provider.getClient(), embeddingModel, "Opinions",
                qdrantProperties.getEmbeddingDimension(),
                parseDistanceMetric(qdrantProperties.getDistanceMetric()));
    }

    public Optional<Document> findById(String id) {
        try {
            // Créer l'ID du point
            Points.PointId pointId = Points.PointId.newBuilder().setUuid(id).build();
            Points.ReadConsistency readConsistency = Points.ReadConsistency.newBuilder()
                    .setFactor(1)
                    .build();

            // Récupérer le point de Qdrant
            List<Points.RetrievedPoint> points = qdrantClient.retrieveAsync(collectionName,
                    pointId,
                    true,
                    true,
                    readConsistency).get();
            // Vérifier si le point existe
            if (points.isEmpty()) {
                return Optional.empty();
            }

            // Convertir le point récupéré en Document
            Points.RetrievedPoint point = points.get(0);
            Document document = OpinionEntry.fromOpinionPoint(point);

            return Optional.of(document);


        } catch (Exception e) {
            log.error("Erreur lors de la récupération du document avec l'id: " + id, e);
            return Optional.empty();
        }
    }
}
