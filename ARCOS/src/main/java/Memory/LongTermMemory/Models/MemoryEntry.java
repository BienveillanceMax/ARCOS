package Memory.LongTermMemory.Models;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import org.springframework.ai.document.Document;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Représente un souvenir/entrée mémoire avec tous les attributs nécessaires
 * pour le stockage et la recherche vectorielle dans Qdrant.
 */
public class MemoryEntry implements QdrantEntry {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @JsonProperty("id")
    private String id;

    @JsonProperty("content")
    private String content;

    @JsonProperty("subject")
    private Subject subject;

    @JsonProperty("satisfaction")
    private double satisfaction;

    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    @JsonProperty("embedding")
    private float[] embedding;

    // Constructeur par défaut pour Jackson
    public MemoryEntry() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Constructeur principal pour créer une nouvelle entrée mémoire.
     */
    public MemoryEntry(String content, Subject subject, double satisfaction) {
        this.id = UUID.randomUUID().toString();
        this.content = content;
        this.subject = subject;
        this.satisfaction = Math.max(-1.0, Math.min(1.0, satisfaction)); // Clamp entre -1 et 1
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Constructeur complet avec ID personnalisé.
     */
    public MemoryEntry(String id, String content, Subject subject, double satisfaction,
                       LocalDateTime timestamp, float[] embedding) {
        this.id = id;
        this.content = content;
        this.subject = subject;
        this.satisfaction = Math.max(-1.0, Math.min(1.0, satisfaction));
        this.timestamp = timestamp;
        this.embedding = embedding;
    }

    // Getters et Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Subject getSubject() {
        return subject;
    }

    public void setSubject(Subject subject) {
        this.subject = subject;
    }

    public double getSatisfaction() {
        return satisfaction;
    }

    public void setSatisfaction(double satisfaction) {
        this.satisfaction = Math.max(-1.0, Math.min(1.0, satisfaction));
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MemoryEntry that = (MemoryEntry) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("MemoryEntry{id='%s', subject=%s, satisfaction=%.2f, content='%s', timestamp=%s}",
                id, subject, satisfaction,
                content.length() > 50 ? content.substring(0, 47) + "..." : content,
                timestamp);
    }


    public static Document fromMemoryPoint(Points.RetrievedPoint point) {
        Map<String, JsonWithInt.Value> payloadMap = point.getPayloadMap();

        // Extraire le contenu. Le contenu est stocké directement dans la charge utile.
        String content = payloadMap.get("doc_content").getStringValue();

        // Créer les métadonnées à partir de la charge utile.
        Map<String, Object> metadata = payloadMap.entrySet().stream()
                .filter(entry -> !entry.getKey().equals("content")) // Exclure le contenu
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

        // Gérer le vecteur (embedding)
        List<Float> vector = point.getVectors().getVector().getDataList();
        metadata.put("embedding", vector);


        // L'ID du document est l'ID du point Qdrant.
        String id = point.getId().getUuid();

        return new Document(id, content, metadata);
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("content", this.getContent());
        payload.put("subject", this.getSubject().getValue());
        payload.put("satisfaction", this.getSatisfaction());
        payload.put("timestamp", this.getTimestamp().format(TIMESTAMP_FORMATTER));
        return payload;
    }
}
