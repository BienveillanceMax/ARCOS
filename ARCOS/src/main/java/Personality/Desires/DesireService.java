package Personality.Desires;

import Exceptions.ResponseParsingException;
import LLM.LLMClient;
import LLM.LLMResponseParser;
import LLM.LLMService;
import LLM.Prompts.PromptBuilder;
import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.Models.OpinionEntry;
import Memory.LongTermMemory.service.MemoryService;
import Personality.Values.ValueProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


import org.springframework.beans.factory.annotation.Autowired;

@Service
@Slf4j
public class DesireService
{
    private final PromptBuilder promptBuilder;
    private final ValueProfile valueProfile;
    private final MemoryService memoryService;
    private final LLMClient llmClient;
    private final LLMResponseParser llmResponseParser;
    private final LLMService llmService;
    public static final double D_CREATE_THRESHOLD = 0.5;
    public static final double D_UPDATE_THRESHOLD = 0.2;

    @Autowired
    public DesireService(PromptBuilder promptBuilder, ValueProfile valueProfile, MemoryService memoryService, LLMClient llmClient, LLMResponseParser llmResponseParser, LLMService llmService) {
        this.promptBuilder = promptBuilder;
        this.valueProfile = valueProfile;
        this.memoryService = memoryService;
        this.llmClient = llmClient;
        this.llmResponseParser = llmResponseParser;
        this.llmService = llmService;
    }

    public DesireEntry processOpinion(OpinionEntry opinionEntry) {

        DesireEntry createdDesire;
        if (opinionEntry.getAssociatedDesire() == null) {

            double opinionIntensity = calculateOpinionIntensity(opinionEntry);

            if (opinionIntensity >= D_CREATE_THRESHOLD) {
                createdDesire = createDesire(opinionEntry, opinionIntensity);
                if (createdDesire == null) {
                    return null;
                }
                memoryService.storeDesire(createdDesire);
                opinionEntry.setAssociatedDesire(createdDesire.getId());
                memoryService.storeOpinion(opinionEntry);
                return createdDesire;

            }
        } else {

            createdDesire = updateDesire(opinionEntry);
            opinionEntry.setAssociatedDesire(createdDesire.getId());
            memoryService.storeOpinion(opinionEntry);
            return createdDesire;
        }

        return null;
    }

    private double calculateOpinionIntensity(OpinionEntry opinionEntry) {
        double absPolarity = Math.abs(opinionEntry.getPolarity());
        return absPolarity * opinionEntry.getStability() * (valueProfile.averageByDimension(opinionEntry.getMainDimension())/100);
    }

    private DesireEntry createDesire(OpinionEntry opinionEntry, double desireIntensity) {
        String prompt = promptBuilder.buildDesirePrompt(opinionEntry, desireIntensity);
        try {
            return llmService.generateAndParse(
                llmClient::generateDesireResponse,
                prompt,
                llmResponse -> {
                    try {
                        return llmResponseParser.parseDesireFromResponse(llmResponse, opinionEntry.getId());
                    } catch (ResponseParsingException e) {
                        throw new RuntimeException(e);
                    }
                },
                3
            );
        } catch (ResponseParsingException e) {
            log.error("Failed to generate and parse desire after multiple retries.", e);
            return null;
        } catch (RuntimeException e) {
            if (e.getCause() instanceof ResponseParsingException) {
                log.error("Failed to parse desire after multiple retries.", e.getCause());
            } else {
                log.error("An unexpected error occurred during desire generation.", e);
            }
            return null;
        }
    }


    private DesireEntry updateDesire(OpinionEntry opinionEntry) {
        DesireEntry desireEntry = memoryService.getDesire(opinionEntry.getAssociatedDesire());

        desireEntry = updateStats(desireEntry, opinionEntry);
        memoryService.storeDesire(desireEntry);
        return desireEntry;
    }

    private DesireEntry updateStats(DesireEntry desireEntry, OpinionEntry opinionEntry) {

        if (opinionEntry != null) {
            // Recalculate intensity based on current opinion and agent's values
            double newIntensity = calculateDesireIntensityUpdate(opinionEntry, desireEntry);

            // Apply smoothing to prevent dramatic changes - weighted average of old and new
            double smoothingFactor = 0.7; // Adjust this value between 0-1 for more/less sensitivity
            double updatedIntensity = (smoothingFactor * desireEntry.getIntensity()) +
                    ((1 - smoothingFactor) * newIntensity);

            // Ensure intensity stays within bounds [0, 1]
            updatedIntensity = Math.max(0.0, Math.min(1.0, updatedIntensity));

            desireEntry.setIntensity(updatedIntensity);
            desireEntry.setLastUpdated(java.time.LocalDateTime.now());

            // Update status based on new intensity if needed
            if (updatedIntensity < D_UPDATE_THRESHOLD &&
                    desireEntry.getStatus() != DesireEntry.Status.SATISFIED &&
                    desireEntry.getStatus() != DesireEntry.Status.ABANDONED) {
                desireEntry.setStatus(DesireEntry.Status.PENDING);
            }
        }
        return desireEntry;
    }

    private double calculateDesireIntensityUpdate(OpinionEntry opinionEntry, DesireEntry desireEntry) {
        // Base calculation similar to opinion intensity but adapted for desire updates
        double absPolarity = Math.abs(opinionEntry.getPolarity());
        double stabilityFactor = opinionEntry.getStability();
        double valueDimensionAverage = valueProfile.averageByDimension(opinionEntry.getMainDimension()) / 100.0;

        // Consider how aligned the desire is with current strong values
        double valueAlignment = valueProfile.calculateValueAlignment(opinionEntry.getMainDimension());

        // Calculate new intensity considering current opinion state and value alignment
        double baseIntensity = absPolarity * stabilityFactor * valueDimensionAverage;
        double adjustedIntensity = baseIntensity * valueAlignment;

        return Math.max(0.0, Math.min(1.0, adjustedIntensity));
    }


}
