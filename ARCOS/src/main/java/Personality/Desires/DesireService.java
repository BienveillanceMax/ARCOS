package Personality.Desires;

import Exceptions.DesireCreationException;
import LLM.Client.LLMClient;
import LLM.Prompts.PromptBuilder;
import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.Models.OpinionEntry;
import Memory.LongTermMemory.Repositories.DesireRepository;
import Memory.LongTermMemory.Repositories.OpinionRepository;
import Personality.Values.ValueProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;


import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


@Service
@Slf4j
public class DesireService
{
    private final PromptBuilder promptBuilder;
    private final ValueProfile valueProfile;
    private final LLMClient llmClient;
    private final DesireRepository desireRepository;
    private final OpinionRepository opinionRepository;
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;


    public static final double D_CREATE_THRESHOLD = 0.5;
    public static final double D_UPDATE_THRESHOLD = 0.2;

    @Autowired
    public DesireService(PromptBuilder promptBuilder, ValueProfile valueProfile, LLMClient llmClient, DesireRepository desireRepository, OpinionRepository opinionRepository) {
        this.promptBuilder = promptBuilder;
        this.valueProfile = valueProfile;
        this.llmClient = llmClient;
        this.desireRepository = desireRepository;
        this.opinionRepository = opinionRepository;
    }

    public DesireEntry processOpinion(OpinionEntry opinionEntry) {

        DesireEntry createdDesire;
        if (opinionEntry.getAssociatedDesire() == null) {

            double opinionIntensity = calculateOpinionIntensity(opinionEntry);

            if (opinionIntensity >= D_CREATE_THRESHOLD) {
                try {
                    createdDesire = createDesire(opinionEntry, opinionIntensity);
                } catch (DesireCreationException e) {
                    log.error("Failed to create desire for opinion {}", opinionEntry.getId(), e);
                    return null;
                }

                try {
                    desireRepository.save(toDocument(createdDesire));
                    opinionEntry.setAssociatedDesire(createdDesire.getId());
                    opinionRepository.save(toDocument(opinionEntry));
                    return createdDesire;
                } catch (Exception e) {
                    log.error("Failed to store desire or update opinion for opinion {}. Deleting desire to prevent orphan.", opinionEntry.getId(), e);
                    desireRepository.delete(Collections.singletonList(createdDesire.getId()));
                    return null;
                }

            }
        } else {
            createdDesire = updateDesire(opinionEntry);
            if (createdDesire != null) {
                opinionEntry.setAssociatedDesire(createdDesire.getId());
                opinionRepository.save(toDocument(opinionEntry));
            }
            return createdDesire;
        }

        return null;
    }

    public List<DesireEntry> getPendingDesires() {
        return desireRepository.findPendingDesires().stream()
                .map(this::fromDocument)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    public void storeDesire(DesireEntry desire) {
        desireRepository.save(toDocument(desire));
    }

    private double calculateOpinionIntensity(OpinionEntry opinionEntry) {
        double absPolarity = Math.abs(opinionEntry.getPolarity());
        return absPolarity * opinionEntry.getStability() * (valueProfile.averageByDimension(opinionEntry.getMainDimension()) / 100);
    }

    private DesireEntry createDesire(OpinionEntry opinionEntry, double desireIntensity) throws DesireCreationException {
        Prompt prompt = promptBuilder.buildDesirePrompt(opinionEntry, desireIntensity);
        DesireEntry createdDesire;

        int retries = 3;
        for (int i = 0; i < retries; i++) {
            createdDesire = llmClient.generateDesireResponse(prompt);
            createdDesire.setOpinionId(opinionEntry.getId());
            return createdDesire;
        }
        throw new DesireCreationException("Failed to create desire for opinion " + opinionEntry.getId() + " after " + retries + " retries.");
    }

    private DesireEntry updateDesire(OpinionEntry opinionEntry) {
        DesireEntry desireEntry = desireRepository.findById(opinionEntry.getAssociatedDesire())
                .map(this::fromDocument)
                .orElse(null);

        if (desireEntry == null) {
            log.error("Could not find desire with id {}", opinionEntry.getAssociatedDesire());
            return null;
        }

        desireEntry = updateStats(desireEntry, opinionEntry);
        desireRepository.save(toDocument(desireEntry));
        return desireEntry;
    }

    private DesireEntry updateStats(DesireEntry desireEntry, OpinionEntry opinionEntry) {

        if (opinionEntry != null) {
            double newIntensity = calculateDesireIntensityUpdate(opinionEntry, desireEntry);
            double smoothingFactor = 0.7;
            double updatedIntensity = (smoothingFactor * desireEntry.getIntensity()) +
                    ((1 - smoothingFactor) * newIntensity);
            updatedIntensity = Math.max(0.0, Math.min(1.0, updatedIntensity));
            desireEntry.setIntensity(updatedIntensity);
            desireEntry.setLastUpdated(java.time.LocalDateTime.now());

            if (updatedIntensity < D_UPDATE_THRESHOLD &&
                    desireEntry.getStatus() != DesireEntry.Status.SATISFIED &&
                    desireEntry.getStatus() != DesireEntry.Status.ABANDONED) {
                desireEntry.setStatus(DesireEntry.Status.PENDING);
            }
        }
        return desireEntry;
    }

    private double calculateDesireIntensityUpdate(OpinionEntry opinionEntry, DesireEntry desireEntry) {
        double absPolarity = Math.abs(opinionEntry.getPolarity());
        double stabilityFactor = opinionEntry.getStability();
        double valueDimensionAverage = valueProfile.averageByDimension(opinionEntry.getMainDimension()) / 100.0;
        double valueAlignment = valueProfile.calculateValueAlignment(opinionEntry.getMainDimension());
        double baseIntensity = absPolarity * stabilityFactor * valueDimensionAverage;
        double adjustedIntensity = baseIntensity * valueAlignment;
        return Math.max(0.0, Math.min(1.0, adjustedIntensity));
    }

    private Document toDocument(DesireEntry desireEntry) {
        String content = desireEntry.getLabel() + ". " + desireEntry.getDescription();
        return new Document(desireEntry.getId(), content, desireEntry.getPayload());
    }

    private Document toDocument(OpinionEntry opinionEntry) {
        String content = opinionEntry.getSummary() != null ? opinionEntry.getSummary() : opinionEntry.getSubject();
        return new Document(opinionEntry.getId(), content, opinionEntry.getPayload());
    }

    private DesireEntry fromDocument(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        DesireEntry desireEntry = new DesireEntry();
        desireEntry.setId(document.getId());
        desireEntry.setLabel((String) metadata.get("label"));
        desireEntry.setDescription((String) metadata.get("description"));
        desireEntry.setReasoning((String) metadata.get("reasoning"));
        desireEntry.setIntensity((Double) metadata.get("intensity"));
        desireEntry.setOpinionId((String) metadata.get("opinionId"));
        desireEntry.setStatus(DesireEntry.Status.valueOf((String) metadata.get("status")));
        desireEntry.setCreatedAt(LocalDateTime.parse((String) metadata.get("createdAt"), TIMESTAMP_FORMATTER));
        desireEntry.setLastUpdated(LocalDateTime.parse((String) metadata.get("lastUpdated"), TIMESTAMP_FORMATTER));
        List<Double> embeddingDouble = (List<Double>) metadata.get("embedding");
        if (embeddingDouble != null) {
            float[] embeddingFloat = new float[embeddingDouble.size()];
            for (int i = 0; i < embeddingDouble.size(); i++) {
                embeddingFloat[i] = embeddingDouble.get(i).floatValue();
            }
            desireEntry.setEmbedding(embeddingFloat);
        }
        return desireEntry;
    }
}




