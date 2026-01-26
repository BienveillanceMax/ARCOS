package org.arcos.Personality.Opinions;

import org.arcos.LLM.Client.LLMClient;
import org.arcos.LLM.Prompts.PromptBuilder;
import org.arcos.Memory.LongTermMemory.Models.MemoryEntry;
import org.arcos.Memory.LongTermMemory.Models.OpinionEntry;
import org.arcos.Memory.LongTermMemory.Repositories.OpinionRepository;
import org.arcos.Personality.Values.Entities.DimensionSchwartz;
import org.arcos.Personality.Values.ValueProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OpinionService {

    private final ValueProfile valueProfile;
    private final PromptBuilder promptBuilder;
    private final OpinionRepository opinionRepository;
    private final LLMClient llmClient;
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;


    // ---- Hyperparamètres ----
    private static final double W_EXPERIENCE = 0.70;   // poids experience dans polarity
    private static final double W_COHERENCE = 0.30;    // poids coherence valeur -> polarity

    private static final double REINFORCE_BASE = 0.05; // gain confiance quand cohérent
    private static final double CONTRADICT_BASE = 0.08; // perte confiance si contradictoire

    private static final double STABILITY_GROWTH = 0.02; // croissance stabilité si renforcé
    private static final double STABILITY_SHRINK = 0.03; // baisse stabilité si contradictoire

    private static final double RHO_NETWORK = 0.25; // poids du réseau dans expEffective

    public OpinionService(LLMClient llmClient, OpinionRepository opinionRepository, PromptBuilder promptBuilder, ValueProfile valueProfile) {
        this.llmClient = llmClient;
        this.opinionRepository = opinionRepository;
        this.promptBuilder = promptBuilder;
        this.valueProfile = valueProfile;
    }

    private static double clamp(double v, double lo, double hi) {
        if (Double.isNaN(v)) return lo;
        return Math.max(lo, Math.min(hi, v));
    }

    private static int sign(double x) {
        if (x > 0) return 1;
        if (x < 0) return -1;
        return 0;
    }

    private OpinionEntry getOpinionFromMemoryEntry(MemoryEntry memoryEntry) {
        OpinionEntry opinionEntry;
        Prompt prompt = promptBuilder.buildOpinionPrompt(memoryEntry);
        try {
            opinionEntry = llmClient.generateOpinionResponse(prompt);
            opinionEntry.getAssociatedMemories().add(memoryEntry.getId());
        } catch (Exception e) {
            log.error("Erreur de parsing d'opinion", e);
            return null;
        }
        return opinionEntry;
    }

    public List<OpinionEntry> processInteraction(MemoryEntry memory) {
        OpinionEntry opinionEntry = getOpinionFromMemoryEntry(memory);
        List<OpinionEntry> opinionEntries = new ArrayList<>();
        if (opinionEntry == null) {
            return null;
        }

        SearchRequest searchRequest = SearchRequest.builder().query(opinionEntry.getSubject()).topK(10).build();
        List<Document> similarOpinionDocs = opinionRepository.search(searchRequest);

        List<Document> sufficientlySimilarDocs = similarOpinionDocs.stream()
                .filter(doc -> {
                    Float distance = (Float) doc.getMetadata().get("distance");
                    return (1 - distance) >= 0.85;
                })
                .collect(Collectors.toList());

        if (!sufficientlySimilarDocs.isEmpty()) {
            for (Document doc : sufficientlySimilarDocs) {
                OpinionEntry existingOpinion = fromDocument(doc);
                opinionEntries.add(updateOpinion(existingOpinion, opinionEntry)); ///hmmm todo tests for duplication  
            }
        } else {
            opinionEntries.add(addOpinion(opinionEntry, memory));
        }
        return opinionEntries;
    }

    public List<OpinionEntry> searchOpinions(String query) {
        SearchRequest searchRequest = SearchRequest.builder().query(query).topK(5).build();
        List<Document> docs = opinionRepository.search(searchRequest);
        return docs.stream().map(this::fromDocument).collect(Collectors.toList());
    }

    private double calculateDeltaC(OpinionEntry opinionEntry, double networkConsistency, double imp, int sOld, int sExp) {
        double deltaC;
        if (sOld == 0) {
            deltaC = REINFORCE_BASE * imp * (1.0 - opinionEntry.getConfidence()) * (0.75 + 0.5 * ((networkConsistency + 1.0) / 2.0));
        } else if (sOld == sExp) {
            double netFactor = 1.0 + 0.5 * Math.max(0.0, networkConsistency);
            deltaC = REINFORCE_BASE * imp * (1.0 - opinionEntry.getConfidence()) * netFactor;
        } else {
            double netFactor = 1.0 + 0.5 * Math.max(0.0, -networkConsistency);
            deltaC = -CONTRADICT_BASE * imp * opinionEntry.getConfidence() * netFactor;
        }
        return deltaC;
    }

    private double updateStabilityScore(OpinionEntry opinionEntry, double networkConsistency, double imp, OpinionEntry newOpinion) {
        double expEffective = clamp((1.0 - RHO_NETWORK) * clamp(opinionEntry.getPolarity(), -1.0, 1.0) + RHO_NETWORK * clamp(networkConsistency, -1.0, 1.0), -1.0, 1.0);
        int sOld = sign(newOpinion.getPolarity());
        int sExp = sign(expEffective);
        double signDelta = (sOld == sExp ? +1.0 : -1.0);
        double deltaC = calculateDeltaC(opinionEntry, networkConsistency, imp, sOld, sExp);

        if (sOld == 0) signDelta = +0.5;
        double netStabFactor = 1.0 + 0.3 * ((networkConsistency + 1.0) / 2.0);
        double stabilityDelta = (signDelta > 0 ? STABILITY_GROWTH : -STABILITY_SHRINK) * imp * Math.abs(deltaC) * netStabFactor;
        return clamp(opinionEntry.getStability() + stabilityDelta, 0.0, 1.0);
    }

    private double updateConfidenceScore(OpinionEntry opinionEntry, double networkConsistency, double imp) {
        double expEffective = clamp((1.0 - RHO_NETWORK) * clamp(opinionEntry.getPolarity(), -1.0, 1.0) + RHO_NETWORK * clamp(networkConsistency, -1.0, 1.0), -1.0, 1.0);
        int sOld = sign(opinionEntry.getPolarity());
        int sExp = sign(expEffective);
        double deltaC = calculateDeltaC(opinionEntry, networkConsistency, imp, sOld, sExp);
        return clamp(opinionEntry.getConfidence() + deltaC, 0.0, 1.0);
    }

    private double updatePolarityScore(OpinionEntry opinionEntry, double networkConsistency, double coh) {
        double expEffective = clamp((1.0 - RHO_NETWORK) * clamp(opinionEntry.getPolarity(), -1.0, 1.0) + RHO_NETWORK * clamp(networkConsistency, -1.0, 1.0), -1.0, 1.0);
        double experienceComponent = W_EXPERIENCE * expEffective + W_COHERENCE * coh;
        return clamp(opinionEntry.getPolarity() * opinionEntry.getStability() + (1.0 - opinionEntry.getStability()) * experienceComponent, -1.0, 1.0);
    }

    private double getNetworkConsistencyScore(OpinionEntry newOpinionEntry) {
        Map.Entry<DimensionSchwartz, Double> maxEntry = valueProfile.averageByDimension().entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);
        if (maxEntry == null) {
            log.error("Erreur null lors du calcul de normVp dans le module OpinionService");
            return 0.5;
        }
        double mainValueScore = maxEntry.getValue();
        double normVp = (mainValueScore - 50.0) / 50.0;
        return normVp * newOpinionEntry.getPolarity();
    }

    private OpinionEntry updateOpinion(OpinionEntry existingOpinion, OpinionEntry newOpinion) {
        double networkConsistencyScore = getNetworkConsistencyScore(newOpinion);
        double newOpinionImportance = valueProfile.averageByDimension(existingOpinion.getMainDimension());
        double coherency = (valueProfile.averageByDimension(existingOpinion.getMainDimension()) - valueProfile.dimensionAverage()) / 100;

        existingOpinion.setStability(updateStabilityScore(existingOpinion, networkConsistencyScore, newOpinionImportance, newOpinion));
        if (existingOpinion.getStability() <= 0) {
            opinionRepository.delete(Collections.singletonList(existingOpinion.getId()));
            return null; //Opinion deleted -> nothing more to do
        }
        existingOpinion.setConfidence(updateConfidenceScore(existingOpinion, networkConsistencyScore, newOpinionImportance));
        existingOpinion.setPolarity(updatePolarityScore(existingOpinion, networkConsistencyScore, coherency));
        existingOpinion.setUpdatedAt(LocalDateTime.now());

        opinionRepository.save(toDocument(existingOpinion));
        return existingOpinion;
    }

    private double calculateStabilityScore(OpinionEntry opinionEntry) {
        if (opinionEntry.getMainDimension() == null) {
            return 0.5;
        }
        return 0.5 + (valueProfile.averageByDimension(opinionEntry.getMainDimension()) / 200.0);
    }

    public OpinionEntry addOpinion(OpinionEntry opinionEntry, MemoryEntry associatedMemoryEntry) {
        opinionEntry.setId(UUID.randomUUID().toString());
        opinionEntry.setStability(calculateStabilityScore(opinionEntry));
        opinionEntry.setAssociatedMemories(List.of(associatedMemoryEntry.getId()));
        opinionEntry.setCreatedAt(LocalDateTime.now());
        opinionEntry.setUpdatedAt(LocalDateTime.now());

        opinionRepository.save(toDocument(opinionEntry));
        return opinionEntry;
    }

    private Document toDocument(OpinionEntry opinionEntry) {
        String content = opinionEntry.getSummary() != null ? opinionEntry.getSummary() : opinionEntry.getSubject();
        return new Document(opinionEntry.getId(), content, opinionEntry.getPayload());
    }

    private OpinionEntry fromDocument(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        OpinionEntry opinionEntry = new OpinionEntry();
        opinionEntry.setId(document.getId());
        opinionEntry.setSubject((String) metadata.get("subject"));
        opinionEntry.setSummary((String) metadata.get("summary"));
        opinionEntry.setNarrative((String) metadata.get("narrative"));
        opinionEntry.setPolarity((Double) metadata.get("polarity"));
        opinionEntry.setConfidence((Double) metadata.get("confidence"));
        opinionEntry.setStability((Double) metadata.get("stability"));
        opinionEntry.setAssociatedMemories((List<String>) metadata.get("associatedMemories"));
        opinionEntry.setAssociatedDesire((String) metadata.get("associatedDesire"));
        Object mainDimensionObj = metadata.get("mainDimension");
        if (mainDimensionObj != null) {
            opinionEntry.setMainDimension(DimensionSchwartz.valueOf(mainDimensionObj.toString()));
        }
        opinionEntry.setCreatedAt(LocalDateTime.parse((String) metadata.get("createdAt"), TIMESTAMP_FORMATTER));
        String updatedAtStr = (String) metadata.get("updatedAt");
        if (updatedAtStr != null) {
            opinionEntry.setUpdatedAt(LocalDateTime.parse(updatedAtStr, TIMESTAMP_FORMATTER));
        }
        List<Double> embeddingDouble = (List<Double>) metadata.get("embedding");
        if (embeddingDouble != null) {
            float[] embeddingFloat = new float[embeddingDouble.size()];
            for (int i = 0; i < embeddingDouble.size(); i++) {
                embeddingFloat[i] = embeddingDouble.get(i).floatValue();
            }
            opinionEntry.setEmbedding(embeddingFloat);
        }
        return opinionEntry;
    }
}
