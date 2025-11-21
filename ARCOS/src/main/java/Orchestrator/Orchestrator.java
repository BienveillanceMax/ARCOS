package Orchestrator;

import EventBus.EventQueue;
import EventBus.Events.Event;
import EventBus.Events.EventType;
import IO.OuputHandling.PiperEmbeddedTTSModule;
import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.Models.MemoryEntry;
import LLM.LLMClient;
import java.util.List;
import java.util.stream.Collectors;
import Memory.ConversationContext;
import Memory.LongTermMemory.service.MemoryService;
import LLM.Prompts.PromptBuilder;
import Personality.Desires.DesireService;
import Personality.Mood.ConversationResponse;
import Personality.Mood.MoodService;
import Personality.Mood.MoodVoiceMapper;
import Personality.Mood.PadState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import Personality.PersonalityOrchestrator;


import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class Orchestrator
{
    private final EventQueue eventQueue;
    private final LLMClient llmClient;
    private final PromptBuilder promptBuilder;
    private final ConversationContext context;
    private final MemoryService memoryService;
    private final InitiativeService initiativeService;
    private final PiperEmbeddedTTSModule ttsHandler;
    private final PersonalityOrchestrator personalityOrchestrator;
    private final MoodService moodService;
    private final MoodVoiceMapper moodVoiceMapper;

    private LocalDateTime lastInteracted;
    private DesireService desireService;

    @Autowired
    public Orchestrator(PersonalityOrchestrator personalityOrchestrator, EventQueue evenQueue, LLMClient llmClient, PromptBuilder promptBuilder, ConversationContext context, MemoryService memoryService, InitiativeService initiativeService, DesireService desireService, MoodService moodService, MoodVoiceMapper moodVoiceMapper) {
        this(personalityOrchestrator, evenQueue, llmClient, promptBuilder, context, memoryService, initiativeService, new PiperEmbeddedTTSModule(), moodService, moodVoiceMapper);
        this.desireService = desireService;
    }

    public Orchestrator(PersonalityOrchestrator personalityOrchestrator, EventQueue evenQueue, LLMClient llmClient, PromptBuilder promptBuilder, ConversationContext context, MemoryService memoryService, InitiativeService initiativeService, PiperEmbeddedTTSModule ttsHandler, MoodService moodService, MoodVoiceMapper moodVoiceMapper) {
        this.eventQueue = evenQueue;
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.context = context;
        this.memoryService = memoryService;
        this.initiativeService = initiativeService;
        this.personalityOrchestrator = personalityOrchestrator;
        this.ttsHandler = ttsHandler;
        this.moodService = moodService;
        this.moodVoiceMapper = moodVoiceMapper;
        lastInteracted = LocalDateTime.now();
    }


    public void dispatch(Event<?> event) {
        if (event.getType() == EventType.WAKEWORD) {
            log.info("starting processing");
            processAndSpeak((String) event.getPayload());
        } else if (event.getType() == EventType.INITIATIVE) {
            DesireEntry desire = (DesireEntry) event.getPayload();
            try {
                initiativeService.processInitiative(desire);
            } catch (Exception e) {
                log.error("A critical error occurred in InitiativeService, reverting desire status for {}", desire.getId(), e);
                desire.setStatus(DesireEntry.Status.PENDING);
                desire.setLastUpdated(java.time.LocalDateTime.now());
                desireService.storeDesire(desire);
            }
        } else if (event.getType() == EventType.CALENDAR_EVENT_SCHEDULER) {

            ttsHandler.speak(llmClient.generateToollessResponse(promptBuilder.buildSchedulerAlertPrompt((event.getPayload()))));

        }
        triggerPersonalityProcessing(lastInteracted);
        lastInteracted = LocalDateTime.now();

    }

    public void start() {
        log.info("Orchestrator starting");
        while (true) {
            try {
                Event<?> event = eventQueue.take();
                dispatch(event);
            } catch (InterruptedException e) {
                log.error("Event queue was interrupted", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    private void processAndSpeak(String userQuery) {
        log.info("Processing query: {}", userQuery);

        // Search for relevant memories
        List<MemoryEntry> relevantMemories = memoryService.searchMemories(userQuery);

        // Create the prompt
        Prompt prompt = promptBuilder.buildConversationnalPrompt(context,userQuery);
        log.info("Prompt: {}", prompt);

        ConversationResponse response = llmClient.generateConversationResponse(prompt);
        log.info("Answer: {}", response.response);

        // Update Mood
        moodService.applyMoodUpdate(response.moodUpdate);

        // Get Voice Parameters based on new Mood
        PadState currentPad = context.getPadState();
        MoodVoiceMapper.VoiceParams voiceParams = moodVoiceMapper.mapToVoice(currentPad);

        // 7. Ajout à la mémoire à court terme.
        context.addUserMessage(userQuery);
        context.addAssistantMessage(response.response);

        // Speak with parameters
        ttsHandler.speak(response.response, voiceParams.lengthScale, voiceParams.noiseScale, voiceParams.noiseW);
    }

    private void triggerPersonalityProcessing(LocalDateTime lastInteraction) {
        CompletableFuture.runAsync(() -> {
            try {
                // On ne veut pas que la personnalité se déclenche à chaque interaction.
                Duration elapsedTime = Duration.between(lastInteraction, LocalDateTime.now());
                if (elapsedTime.toMinutes() > 10) {
                    log.info("Triggering personality processing...");
                    String fullConversation = context.getFullConversation();
                    personalityOrchestrator.processMemory(fullConversation);
                }
            } catch (Exception e) {
                log.error("Error during personality processing", e);
            }
        });

    }
}
