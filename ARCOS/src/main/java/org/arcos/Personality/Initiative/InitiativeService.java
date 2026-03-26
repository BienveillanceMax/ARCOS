package org.arcos.Personality.Initiative;

import org.arcos.LLM.Client.ChatOrchestrator;
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

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class InitiativeService {

    private final MemoryService memoryService;
    private final OpinionService opinionService;
    private final DesireService desireService;
    private final ChatOrchestrator chatOrchestrator;
    private final PromptBuilder promptBuilder;
    private final PersonalityOrchestrator personalityOrchestrator;

    @Autowired
    public InitiativeService(MemoryService memoryService, OpinionService opinionService, DesireService desireService, ChatOrchestrator chatOrchestrator, PromptBuilder promptBuilder, PersonalityOrchestrator personalityOrchestrator) {
        this.memoryService = memoryService;
        this.opinionService = opinionService;
        this.desireService = desireService;
        this.chatOrchestrator = chatOrchestrator;
        this.promptBuilder = promptBuilder;
        this.personalityOrchestrator = personalityOrchestrator;
    }

    static final String SKIP_MARKER = "[SKIP]";
    private static final int MAX_REASONING_LENGTH = 300;

    /**
     * @return true if the initiative was executed successfully, false on failure or skip (desire stays PENDING)
     */
    public boolean processInitiative(DesireEntry desire) {
        try {
            log.info("Processing initiative for desire: {}", desire.getLabel());

            // 1. Enrich context
            List<MemoryEntry> memories = memoryService.searchMemories(desire.getDescription(), 5);
            List<OpinionEntry> opinions = opinionService.searchOpinions(desire.getDescription());

            // 2. Build Prompt and Execute
            Prompt prompt = promptBuilder.buildInitiativePrompt(desire, memories, opinions);
            String result = chatOrchestrator.generateChatResponse(prompt);

            log.info("Initiative execution result: {}", result);

            if (result == null || result.isBlank()) {
                log.warn("Initiative '{}' returned empty result, skipping.", desire.getLabel());
                return false;
            }

            // 3. Handle [SKIP] — desire is not actionable, keep PENDING
            if (result.startsWith(SKIP_MARKER)) {
                log.info("Initiative '{}' skipped (non-actionable): {}", desire.getLabel(), result);
                return false;
            }

            // 4. Update state
            String cappedResult = result.length() > MAX_REASONING_LENGTH
                    ? result.substring(0, MAX_REASONING_LENGTH) + "..."
                    : result;

            desireService.withDesireLock(() -> {
                desire.setStatus(DesireEntry.Status.SATISFIED);
                desire.setLastUpdated(LocalDateTime.now());
                desireService.storeDesire(desire);
            });
            log.info("Initiative '{}' was satisfied.", desire.getLabel());

            // 5. Close BDI Loop (Memory -> Opinion)
            String memoryContent = "J'ai pris l'initiative de " + desire.getLabel() + ". " + cappedResult;
            MemoryEntry memory = new MemoryEntry(
                memoryContent,
                Subject.SELF,
                0.7
            );
            memoryService.storeMemory(memory);
            personalityOrchestrator.processMemoryEntryIntoOpinion(memory);

            return true;

        } catch (Exception e) {
            log.error("Initiative '{}' failed (will retry later): {}", desire.getLabel(), e.getMessage());
            desireService.withDesireLock(() -> {
                desire.setStatus(DesireEntry.Status.PENDING);
                desire.setLastUpdated(LocalDateTime.now());
                desireService.storeDesire(desire);
            });
            return false;
        }
    }
}
