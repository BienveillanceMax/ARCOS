package org.arcos.Orchestrator;

import org.arcos.LLM.Client.LLMClient;
import org.arcos.LLM.Prompts.PromptBuilder;
import org.arcos.Memory.LongTermMemory.Models.DesireEntry;
import org.arcos.Memory.LongTermMemory.Models.MemoryEntry;
import org.arcos.Memory.LongTermMemory.Models.OpinionEntry;
import org.arcos.Memory.LongTermMemory.Models.Subject;
import org.arcos.Memory.LongTermMemory.service.MemoryService;
import org.arcos.Personality.Desires.DesireService;
import org.arcos.Personality.Opinions.OpinionService;
import org.arcos.Personality.PersonalityOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class InitiativeService {

    private final MemoryService memoryService;
    private final OpinionService opinionService;
    private final DesireService desireService;
    private final LLMClient llmClient;
    private final PromptBuilder promptBuilder;
    private final PersonalityOrchestrator personalityOrchestrator;

    @Autowired
    public InitiativeService(MemoryService memoryService, OpinionService opinionService, DesireService desireService, LLMClient llmClient, PromptBuilder promptBuilder, PersonalityOrchestrator personalityOrchestrator) {
        this.memoryService = memoryService;
        this.opinionService = opinionService;
        this.desireService = desireService;
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.personalityOrchestrator = personalityOrchestrator;
    }

    public void processInitiative(DesireEntry desire) {
        try {
            log.info("Processing initiative for desire: {}", desire.getLabel());

            // 1. Enrich context
            log.info("Step 1: Enriching context...");
            List<MemoryEntry> memories = memoryService.searchMemories(desire.getDescription(), 5);
            List<OpinionEntry> opinions = opinionService.searchOpinions(desire.getDescription());

            // 2. Build Prompt and Execute
            log.info("Step 2: Executing initiative via LLM Agent...");
            Prompt prompt = promptBuilder.buildInitiativePrompt(desire, memories, opinions);

            // The LLMClient is configured with tools, so it will autonomously execute actions
            String result = llmClient.generateChatResponse(prompt); // This returns a String (content)

            log.info("Initiative execution result: {}", result);

            // 3. Update state
            log.info("Step 3: Updating desire state...");

            desire.setStatus(DesireEntry.Status.SATISFIED);
            desire.setReasoning("Executed via initiative. Result: " + result);
            desire.setLastUpdated(java.time.LocalDateTime.now());

            desireService.storeDesire(desire);
            log.info("Initiative '{}' was satisfied successfully.", desire.getLabel());

            // 4. Close BDI Loop (Memory -> Opinion)
            log.info("Step 4: Forming memory of initiative...");
            MemoryEntry memory = new MemoryEntry(
                "I took initiative to " + desire.getDescription() + ". Result: " + result,
                Subject.SELF,
                0.8 // Assuming satisfaction
            );
            memoryService.storeMemory(memory);
            personalityOrchestrator.processMemoryEntryIntoOpinion(memory);

        } catch (Exception e) {
            log.error("An unexpected error occurred while processing initiative {}", desire.getId(), e);
            desire.setStatus(DesireEntry.Status.PENDING); // Revert to PENDING on failure
            desire.setLastUpdated(java.time.LocalDateTime.now());
            desireService.storeDesire(desire);
            throw new RuntimeException(e);
        }
    }
}
