package Orchestrator;

import LLM.LLMClient;
import LLM.Prompts.PromptBuilder;
import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.service.MemoryService;
import Personality.Opinions.OpinionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class InitiativeService {

    private final MemoryService memoryService;
    private final OpinionService opinionService;
    private final LLMClient llmClient;
    private final PromptBuilder promptBuilder;

    @Autowired
    public InitiativeService(MemoryService memoryService, OpinionService opinionService, LLMClient llmClient, PromptBuilder promptBuilder) {
        this.memoryService = memoryService;
        this.opinionService = opinionService;
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
    }

    public void processInitiative(DesireEntry desire) {
        /*
        try {
            log.info("Processing initiative for desire: {}", desire.getLabel());

            // 1. Enrich context
            log.info("Step 1: Enriching context...");
            List<MemoryEntry> memories = memoryService.searchMemories(desire.getDescription(), 5).stream()
                    .map(SearchResult::getEntry)
                    .collect(Collectors.toList());
            List<OpinionEntry> opinions = memoryService.searchOpinions(desire.getDescription(), 5).stream()
                    .map(SearchResult::getEntry)
                    .collect(Collectors.toList());

            // 2. Transform desire into a plan
            log.info("Step 2: Transforming desire into a plan...");
            String prompt = promptBuilder.buildInitiativePlanningPrompt(desire, memories, opinions);
            String planningResponse = llmClient.generatePlanningResponse(prompt);
            ExecutionPlan plan = responseParser.parseWithMistralRetry(planningResponse, 3);
            log.info("Plan received from LLM: {}", plan.getReasoning());


            // 3. Execute the plan
            log.info("Step 3: Executing plan...");
            Map<String, ActionResult> results = actionExecutor.executeActions(plan);
            log.info("Execution results: {}", results);


            // 4. Update state
            log.info("Step 4: Updating state...");
            boolean allActionsSucceeded = results.values().stream().allMatch(ActionResult::isSuccess);

            if (allActionsSucceeded) {
                desire.setStatus(DesireEntry.Status.SATISFIED);
                log.info("Initiative '{}' was satisfied successfully.", desire.getLabel());
            } else {
                desire.setStatus(DesireEntry.Status.PENDING); // or a new FAILED status
                log.warn("Initiative '{}' failed. Setting status back to PENDING.", desire.getLabel());
            }

        } catch (Exception e) {
            log.error("An unexpected error occurred while processing initiative {}", desire.getId(), e);
            desire.setStatus(DesireEntry.Status.PENDING); // Revert to PENDING on failure
        } finally {
            desire.setLastUpdated(java.time.LocalDateTime.now());
            memoryService.storeDesire(desire);
            log.info("Desire {} updated in database with status {}", desire.getId(), desire.getStatus());
        }

        */
    }
}
