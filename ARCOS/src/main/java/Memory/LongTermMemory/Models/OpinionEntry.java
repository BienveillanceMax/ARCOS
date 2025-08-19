package Memory.LongTermMemory.Models;

import Personality.Values.Entities.DimensionSchwartz;
import Personality.Values.Entities.ValueSchwartz;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class OpinionEntry
{
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

    @JsonProperty("embedding")
    private float[] embedding;      // Pour recherche vectorielle

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;

    @JsonProperty("mainDimension")
    private DimensionSchwartz mainDimension;

    public OpinionEntry() {
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
}


