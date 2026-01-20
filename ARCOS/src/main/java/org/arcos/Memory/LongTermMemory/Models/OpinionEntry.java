package org.arcos.Memory.LongTermMemory.Models;

import org.arcos.LLM.Client.ResponseObject.OpinionResponse;
import org.arcos.Personality.Values.Entities.DimensionSchwartz;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import org.springframework.ai.document.Document;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class OpinionEntry implements QdrantEntry
{
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @JsonProperty("id")
    private String id;

    @JsonProperty("subject")
    private String subject;         // Sujet concerné (chiens, politique, utilisateur...), un enum n'est pas suffisant ici

    @JsonProperty("summary")
    private String summary;         // Résumé court

    @JsonProperty("narrative")
    private String narrative;       // Version narrative plus longue

    @JsonProperty("polarity")
    private double polarity;        // [-1, 1] (négatif/positif)

    @JsonProperty("confidence")
    private double confidence;      // [0, 1] (degré de certitude)

    @JsonProperty("stability")
    private double stability;       // [0, 1] (résistance au changement)

    @JsonProperty("associatedMemories")
    private List<String> associatedMemories; // IDs des souvenirs liés

    @JsonProperty("associatedDesire")
    private String associatedDesire;

    @JsonProperty("embedding")
    private float[] embedding;      // Pour recherche vectorielle

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;

    @JsonProperty("mainDimension")
    private DimensionSchwartz mainDimension;

    public OpinionEntry() {
        associatedMemories = new ArrayList<>();
        associatedDesire = "";
    }

    public static OpinionEntry fromOpinionResponse(OpinionResponse response) {
        OpinionEntry entry = new OpinionEntry();
        entry.id = UUID.randomUUID().toString();
        entry.subject = response.getSubject();
        entry.summary = response.getSummary();
        entry.narrative = response.getNarrative();
        entry.polarity = response.getPolarity();
        entry.confidence = response.getConfidence();
        entry.stability = response.getStability();
        entry.associatedMemories = new ArrayList<>();
        entry.associatedDesire = "";
        entry.createdAt = LocalDateTime.now();
        entry.updatedAt = LocalDateTime.now();
        entry.mainDimension = response.getMainDimension();
        return entry;
    }

    public DimensionSchwartz getMainDimension() {
        return mainDimension;
    }

    public void setMainDimension(DimensionSchwartz mainDimension) {
        this.mainDimension = mainDimension;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getNarrative() {
        return narrative;
    }

    public void setNarrative(String narrative) {
        this.narrative = narrative;
    }

    public double getPolarity() {
        return polarity;
    }

    public void setPolarity(double polarity) {
        this.polarity = polarity;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public double getStability() {
        return stability;
    }

    public void setStability(double stability) {
        this.stability = stability;
    }

    public List<String> getAssociatedMemories() {
        return associatedMemories;
    }

    public void setAssociatedMemories(List<String> associatedMemories) {
        this.associatedMemories = associatedMemories;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getAssociatedDesire() {
        return associatedDesire;
    }

    public void setAssociatedDesire(String associatedDesire) {
        this.associatedDesire = associatedDesire;
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("subject", this.getSubject());
        payload.put("summary", this.getSummary());
        payload.put("narrative", this.getNarrative());
        payload.put("polarity", this.getPolarity());
        payload.put("confidence", this.getConfidence());
        payload.put("stability", this.getStability());
        payload.put("associatedMemories", this.getAssociatedMemories());
        payload.put("associatedDesire", this.getAssociatedDesire());
        if (this.getMainDimension() != null) {
            payload.put("mainDimension", this.getMainDimension().name());
        }

        if (this.getCreatedAt() != null) {
            payload.put("createdAt", this.getCreatedAt().format(TIMESTAMP_FORMATTER));
        }
        if (this.getUpdatedAt() != null) {
            payload.put("updatedAt", this.getUpdatedAt().format(TIMESTAMP_FORMATTER));
        }

        return payload;
    }

    public static Document fromOpinionPoint(Points.RetrievedPoint point) {
        Map<String, JsonWithInt.Value> payloadMap = point.getPayloadMap();

        String narrative = payloadMap.get("narrative").getStringValue();

        Map<String, Object> metadata = payloadMap.entrySet().stream()
                .filter(entry -> !entry.getKey().equals("narrative")) // Exclude the narrative from metadata
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                    JsonWithInt.Value value = entry.getValue();
                    switch (value.getKindCase()) {
                        case STRING_VALUE:
                            return value.getStringValue();
                        case DOUBLE_VALUE:
                            return value.getDoubleValue();
                        case BOOL_VALUE:
                            return value.getBoolValue();
                        case LIST_VALUE:
                            return value.getListValue().getValuesList().stream()
                                    .map(JsonWithInt.Value::getStringValue)
                                    .collect(Collectors.toList());
                        case NULL_VALUE:
                        default:
                            return null;
                    }
                }));

        List<Float> vector = point.getVectors().getVector().getDataList();
        metadata.put("embedding", vector);
        String id = point.getId().getUuid();
        return new Document(id, narrative, metadata);
    }
}