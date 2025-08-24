package Personality.Desires;

import Exceptions.ResponseParsingException;
import LLM.LLMClient;
import LLM.LLMResponseParser;
import LLM.Prompts.PromptBuilder;
import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.Models.OpinionEntry;
import Personality.Values.ValueProfile;
import org.springframework.stereotype.Service;

@Service
public class DesireService
{
    ValueProfile valueProfile;
    LLMClient llmClient;
    LLMResponseParser llmResponseParser;
    PromptBuilder promptBuilder;

    double D_CREATE_THRESHOLD = 0.5;
    double D_UPDATE_THRESHOLD = 0.2;

    public void processOpinion(OpinionEntry opinionEntry) {

        if (opinionEntry.getAssociatedDesire() == null) {

            if (opinionValidatesConditions(opinionEntry)) {
                DesireEntry createdDesire = createDesire(opinionEntry);
            }
        } else {

            updateDesire(opinionEntry);
        }
    }


    private boolean opinionValidatesConditions(OpinionEntry opinionEntry) {
        double absPolarity = Math.abs(opinionEntry.getPolarity());
        double candidacyScore = absPolarity * opinionEntry.getPolarity() * (valueProfile.averageByDimension(opinionEntry.getMainDimension())/100);
        return candidacyScore >= D_CREATE_THRESHOLD;
    }

    private DesireEntry createDesire(OpinionEntry opinionEntry) {
        String prompt = promptBuilder.buildDesirePrompt(opinionEntry);
        try {
            llmResponseParser.parseDesireFromResponse(llmClient.generateDesireResponse(prompt));
        } catch (ResponseParsingException e) {
            throw new RuntimeException(e);
        }
        
    }

    private void updateDesire(OpinionEntry opinionEntry) {
    }
}


