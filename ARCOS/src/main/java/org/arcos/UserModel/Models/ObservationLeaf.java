package org.arcos.UserModel.Models;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class ObservationLeaf {

    private String id;
    private String text;
    private TreeBranch branch;
    private Instant lastReinforced;
    private int observationCount;
    private float emotionalImportance;
    private ObservationSource source;
    private float[] embedding;
    private boolean needsConsolidation;
    private String conflictsWith;

    public ObservationLeaf() {
        this.id = UUID.randomUUID().toString();
        this.lastReinforced = Instant.now();
        this.observationCount = 1;
        this.emotionalImportance = 0.5f;
        this.needsConsolidation = false;
    }

    public ObservationLeaf(String text, TreeBranch branch, ObservationSource source) {
        this();
        this.text = text;
        this.branch = branch;
        this.source = source;
    }

    public ObservationLeaf(String text, TreeBranch branch, ObservationSource source, float[] embedding) {
        this(text, branch, source);
        this.embedding = embedding;
    }
}
