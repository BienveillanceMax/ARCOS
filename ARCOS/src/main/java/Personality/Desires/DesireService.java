package Personality.Desires;

import Exceptions.DesireCreationException;
import LLM.LLMClient;
import LLM.Prompts.PromptBuilder;
import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.Models.OpinionEntry;
import Memory.LongTermMemory.Repositories.DesireRepository;
import Memory.LongTermMemory.Repositories.OpinionRepository;
import Personality.Values.ValueProfile;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;


import org.springframework.beans.factory.annotation.Autowired;

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
    private final Gson gson = new Gson();

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
        List<DesireEntry> pendingDesiresList = new ArrayList<>();
        List<Document> pendingDocumentList = desireRepository.findPendingDesires();
        for (Document document : pendingDocumentList) {
            pendingDesiresList.add(fromDocument(document));
        }
        return pendingDesiresList;
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
        DesireEntry desireEntry = (DesireEntry) desireRepository.findById(opinionEntry.getAssociatedDesire())       //TODO Test
                .map(obj -> fromDocument((Document) obj))  // Cast explicite
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
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("entry", gson.toJson(desireEntry));
        return new Document(desireEntry.getId(), content, metadata);
    }

    private Document toDocument(OpinionEntry opinionEntry) {
        String content = opinionEntry.getSummary() != null ? opinionEntry.getSummary() : opinionEntry.getSubject();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("entry", gson.toJson(opinionEntry));
        return new Document(opinionEntry.getId(), content, metadata);
    }

    private DesireEntry fromDocument(Document document) {
        String json = (String) document.getMetadata().get("entry");
        return gson.fromJson(json, DesireEntry.class);
    }


}




