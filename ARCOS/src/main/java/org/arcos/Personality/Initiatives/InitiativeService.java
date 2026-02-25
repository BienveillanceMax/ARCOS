package org.arcos.Personality.Initiatives;

import org.arcos.LLM.Client.LLMClient;
import org.arcos.LLM.Prompts.PromptBuilder;
import org.arcos.Memory.LongTermMemory.Models.DesireEntry;
import org.arcos.Memory.LongTermMemory.Models.MemoryEntry;
import org.arcos.Memory.LongTermMemory.Models.OpinionEntry;
import org.arcos.Memory.LongTermMemory.service.MemoryService;
import org.arcos.Personality.Desires.DesireService;
import org.arcos.Personality.Opinions.OpinionService;
import org.arcos.Personality.PersonalityOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
public class InitiativeService {

    private final OpinionService opinionService;
    private final MemoryService memoryService;
    private final LLMClient llmClient;
    private final PromptBuilder promptBuilder;
    private final PersonalityOrchestrator personalityOrchestrator;
    private final DesireService desireService;

    public InitiativeService(DesireService desireService, OpinionService opinionService,
                             MemoryService memoryService, LLMClient llmClient,
                             PromptBuilder promptBuilder, PersonalityOrchestrator personalityOrchestrator) {
        this.desireService = desireService;
        this.opinionService = opinionService;
        this.memoryService = memoryService;
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.personalityOrchestrator = personalityOrchestrator;
    }

    /**
     * Process an initiative for a given desire. Called by the Orchestrator
     * when it receives an INITIATIVE event from DesireInitiativeProducer.
     */
    public void processInitiative(DesireEntry desire) {
        try {
            // Gather context
            List<MemoryEntry> memories = memoryService.searchMemories(desire.getLabel(), 5);
            List<OpinionEntry> opinions = opinionService.searchOpinions(desire.getLabel());

            // Build Prompt
            var prompt = promptBuilder.buildInitiativePrompt(desire, memories, opinions);

            // Execute (using ChatResponse to allow Tools)
            String result = llmClient.generateChatResponse(prompt);

            log.info("Initiative Result: {}", result);

            // Feedback loop
            personalityOrchestrator.processMemory("AUTO-GENERATED INITIATIVE RESULT: " + result);

            // Determine if successful. For now, we assume if we got a result, we took action.
            // A more robust system would analyze the result to check for failure.
            // We set it to SATISFIED to complete the cycle and prevent infinite loops on the same desire.
            // TODO: Implement result analysis to decide if the desire is truly satisfied or failed (and needs retry/abandon).
            desire.setStatus(DesireEntry.Status.SATISFIED);
            desireService.storeDesire(desire);

        } catch (Exception e) {
            log.error("Error executing initiative for desire: {}", desire.getId(), e);
            // Keep PENDING
        }
    }
}
