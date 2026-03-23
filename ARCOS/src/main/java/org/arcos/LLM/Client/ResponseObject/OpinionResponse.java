package org.arcos.LLM.Client.ResponseObject;

import org.arcos.Personality.Values.Entities.DimensionSchwartz;
import com.fasterxml.jackson.annotation.JsonProperty;

public class OpinionResponse
{
    @JsonProperty("subject")
    private String subject;         // Sujet concerné (chiens, politique, utilisateur...), un enum n'est pas suffisant ici

    @JsonProperty("summary")
    private String summary;         // Résumé court

    @JsonProperty("canonicalText")
    private String canonicalText;   // Forme canonique (Sujet + Verbe + Objet), première personne, présent indicatif

    @JsonProperty("polarity")
    private double polarity;        // [-1, 1] (négatif/positif)

    @JsonProperty("confidence")
    private double confidence;      // [0, 1] (degré de certitude)

    @JsonProperty("stability")
    private double stability;       // [0, 1] (résistance au changement)

    @JsonProperty("mainDimension")
    private DimensionSchwartz mainDimension;

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

    public String getCanonicalText() {
        return canonicalText;
    }

    public void setCanonicalText(String canonicalText) {
        this.canonicalText = canonicalText;
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

    public DimensionSchwartz getMainDimension() {
        return mainDimension;
    }

    public void setMainDimension(DimensionSchwartz mainDimension) {
        this.mainDimension = mainDimension;
    }
}
