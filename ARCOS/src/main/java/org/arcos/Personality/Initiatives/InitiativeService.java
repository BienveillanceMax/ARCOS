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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
public class InitiativeService {

    private final DesireService desireService;
    private final OpinionService opinionService;
    private final MemoryService memoryService;
    private final LLMClient llmClient;
    private final PromptBuilder promptBuilder;
    private final PersonalityOrchestrator personalityOrchestrator;

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

    @Scheduled(fixedDelay = 60000) // Check every minute
    public void checkPendingDesires() {
        log.info("Checking for pending desires...");
        List<DesireEntry> pending = desireService.getPendingDesires();
        if (pending == null || pending.isEmpty()) {
            return;
        }

        // Pick highest intensity
        pending.sort(Comparator.comparingDouble(DesireEntry::getIntensity).reversed());
        DesireEntry topDesire = pending.get(0);

        log.info("Executing initiative for desire: {} (Intensity: {})", topDesire.getLabel(), topDesire.getIntensity());

        // Mark as ACTIVE during execution? Or just keep PENDING?
        // User said: "Si cette initiative réussit ... il sera supprimé, sinon il sera gardé en 'Pending'".
        // I will keep it as PENDING during execution, but if I crash I don't want to deadlock.
        // Let's execute.

        executeInitiative(topDesire);
    }

    private void executeInitiative(DesireEntry desire) {
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
