package Personality.Desires;

import Exceptions.ResponseParsingException;
import LLM.LLMClient;
import LLM.LLMResponseParser;
import LLM.Prompts.PromptBuilder;
import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.Models.OpinionEntry;
import Memory.LongTermMemory.service.MemoryService;
import Personality.Values.ValueProfile;
import Producers.DesireInitativeProducer;
import org.springframework.stereotype.Service;

@Service
public class DesireService
{
    DesireInitativeProducer desireInitativeProducer;
    PromptBuilder promptBuilder;
    ValueProfile valueProfile;
    MemoryService memoryService;
    LLMClient llmClient;
    LLMResponseParser llmResponseParser;
    double D_CREATE_THRESHOLD = 0.5;
    double D_UPDATE_THRESHOLD = 0.2;

    public void processOpinion(OpinionEntry opinionEntry) {

        if (opinionEntry.getAssociatedDesire() == null) {

            double opinionIntensity = calculateOpinionIntensity(opinionEntry);

            if (opinionIntensity >= D_CREATE_THRESHOLD) {
                DesireEntry createdDesire = createDesire(opinionEntry, opinionIntensity);
                memoryService.storeDesire(createdDesire);

            }
        } else {

            updateDesire(opinionEntry);
        }
    }

    private double calculateOpinionIntensity(OpinionEntry opinionEntry) {
        double absPolarity = Math.abs(opinionEntry.getPolarity());
        return absPolarity * opinionEntry.getStability() * (valueProfile.averageByDimension(opinionEntry.getMainDimension())/100);
    }

    private DesireEntry createDesire(OpinionEntry opinionEntry, double desireIntensity) {
        String prompt = promptBuilder.buildDesirePrompt(opinionEntry, desireIntensity);
        DesireEntry createdDesire;
        try {
            createdDesire =  llmResponseParser.parseDesireFromResponse(llmClient.generateDesireResponse(prompt), opinionEntry.getId());
        } catch (ResponseParsingException e) {
            throw new RuntimeException(e);
        }
        return createdDesire;
    }


    private void updateDesire(OpinionEntry opinionEntry) {
        DesireEntry desireEntry = memoryService.getDesire(opinionEntry.getAssociatedDesire());

        if (desireEntry.getIntensity() < D_UPDATE_THRESHOLD) {
            desireInitativeProducer.initDesireInitiative(desireEntry);
        }
        else
        {
            //TODO
        }
    }
}


