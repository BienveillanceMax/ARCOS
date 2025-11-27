package Memory.LongTermMemory.Qdrant;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class QdrantClientProvider {

    private final QdrantClient client;

    public QdrantClientProvider(
            @Value("${qdrant.host}") String host,
            @Value("${qdrant.port}") int port
    ) {
        QdrantGrpcClient grpcClient = QdrantGrpcClient.newBuilder(host, port, false).build();
        this.client = new QdrantClient(grpcClient);
    }
}