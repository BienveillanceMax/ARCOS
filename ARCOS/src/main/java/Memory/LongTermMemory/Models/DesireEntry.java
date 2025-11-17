package Memory.LongTermMemory.Models;

import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import org.springframework.ai.document.Document;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DesireEntry implements QdrantEntry
{
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public enum Status
    {PENDING, ACTIVE, SATISFIED, ABANDONED}

    private String id;
    private String opinionId;      // link to opinion
    private String label;       // title of the desire
    private String description;
    private double intensity;    // 0..1
    private String reasoning;
    private Status status;
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdated;
    private float[] embedding;

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOpinionId() {
        return opinionId;
    }

    public void setOpinionId(String opinionId) {
        this.opinionId = opinionId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getIntensity() {
        return intensity;
    }

    public void setIntensity(double intensity) {
        this.intensity = intensity;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("label", this.getLabel());
        payload.put("description", this.getDescription());
        payload.put("reasoning", this.getReasoning());
        payload.put("intensity", this.getIntensity());
        payload.put("opinionId", this.getOpinionId());
        payload.put("status", this.getStatus().name());


        if (this.getCreatedAt() != null) {
            payload.put("createdAt", this.getCreatedAt().format(TIMESTAMP_FORMATTER));
        }
        if (this.getLastUpdated() != null) {
            payload.put("lastUpdated", this.getLastUpdated().format(TIMESTAMP_FORMATTER));
        }

        return payload;
    }

    public static Document fromDesirePoint(Points.RetrievedPoint point) {
        Map<String, JsonWithInt.Value> payloadMap = point.getPayloadMap();

        String description = payloadMap.get("description").getStringValue();

        Map<String, Object> metadata = payloadMap.entrySet().stream()
                .filter(entry -> !entry.getKey().equals("description"))
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                    JsonWithInt.Value value = entry.getValue();
                    switch (value.getKindCase()) {
                        case STRING_VALUE:
                            return value.getStringValue();
                        case DOUBLE_VALUE:
                            return value.getDoubleValue();
                        case BOOL_VALUE:
                            return value.getBoolValue();
                        case NULL_VALUE:
                        default:
                            return null;
                    }
                }));

        List<Float> vector = point.getVectors().getVector().getDataList();
        metadata.put("embedding", vector);

        String id = point.getId().getUuid();

        return new Document(id, description, metadata);
    }
}



