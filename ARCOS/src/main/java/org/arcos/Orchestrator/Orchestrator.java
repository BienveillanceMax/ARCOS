package org.arcos.Orchestrator;

import org.arcos.EventBus.EventQueue;
import org.arcos.EventBus.Events.Event;
import org.arcos.EventBus.Events.EventType;
import org.arcos.IO.OuputHandling.PiperEmbeddedTTSModule;
import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.IO.OuputHandling.StateHandler.FeedBackEvent;
import org.arcos.IO.OuputHandling.StateHandler.UXEventType;
import org.arcos.Memory.LongTermMemory.Models.DesireEntry;
import org.arcos.LLM.Client.LLMClient;
import org.arcos.Memory.ConversationContext;
import org.arcos.Memory.LongTermMemory.service.MemoryService;
import org.arcos.LLM.Prompts.PromptBuilder;
import org.arcos.Personality.Desires.DesireService;
import org.arcos.Personality.Mood.MoodService;
import org.arcos.Personality.Mood.MoodVoiceMapper;
import org.arcos.Personality.Mood.MoodUpdate;
import org.arcos.Personality.Mood.PadState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.arcos.Personality.PersonalityOrchestrator;


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
    private final CentralFeedBackHandler centralFeedBackHandler;

    private LocalDateTime lastInteracted;
    private DesireService desireService;

    @Autowired
    public Orchestrator(CentralFeedBackHandler centralFeedBackHandler, PersonalityOrchestrator personalityOrchestrator, EventQueue evenQueue, LLMClient llmClient, PromptBuilder promptBuilder, ConversationContext context, MemoryService memoryService, InitiativeService initiativeService, DesireService desireService, MoodService moodService, MoodVoiceMapper moodVoiceMapper) {
        this.ttsHandler = new PiperEmbeddedTTSModule();
        this.desireService = desireService;
        this.centralFeedBackHandler = centralFeedBackHandler;
        this.eventQueue = evenQueue;
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.context = context;
        this.memoryService = memoryService;
        this.initiativeService = initiativeService;
        this.personalityOrchestrator = personalityOrchestrator;
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
                centralFeedBackHandler.handleFeedBack(new FeedBackEvent(UXEventType.INITIATIVE_START));
                initiativeService.processInitiative(desire);
                centralFeedBackHandler.handleFeedBack(new FeedBackEvent(UXEventType.INITIATIVE_END));

            } catch (Exception e) {
                log.error("A critical error occurred in InitiativeService, reverting desire status for {}", desire.getId(), e);
                desire.setStatus(DesireEntry.Status.PENDING);
                desire.setLastUpdated(java.time.LocalDateTime.now());
                desireService.storeDesire(desire);
            }
        } else if (event.getType() == EventType.CALENDAR_EVENT_SCHEDULER) {

            ttsHandler.speakAsync(llmClient.generateToollessResponse(promptBuilder.buildSchedulerAlertPrompt((event.getPayload()))));

        }

    }

    public void start() {
        log.info("Orchestrator starting");
      //processAndSpeak("Bienvenue dans le monde réel, tu viens d'être activé");
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

        // Create the prompt for streaming response
        Prompt streamingPrompt = promptBuilder.buildConversationnalPrompt(context, userQuery);
        log.info("Streaming Prompt: {}", streamingPrompt);

        // Get Voice Parameters based on current Mood
        PadState currentPad = context.getPadState();
        MoodVoiceMapper.VoiceParams voiceParams = moodVoiceMapper.mapToVoice(currentPad);
        generateFluxAndSpeak(streamingPrompt,userQuery,voiceParams);





    }

    private void generateFluxAndSpeak(Prompt streamingPrompt, String userQuery, MoodVoiceMapper.VoiceParams voiceParams) {
        StringBuilder sentenceBuffer = new StringBuilder();
        StringBuilder fullResponse = new StringBuilder();
        llmClient.generateStreamingChatResponse(streamingPrompt)
                .doOnNext(chunk -> {
                    // 1. On garde le texte brut (avec *) pour l'historique et le buffer
                    sentenceBuffer.append(chunk);
                    fullResponse.append(chunk);

                    int punctuationIndex;
                    while ((punctuationIndex = findSentenceEnd(sentenceBuffer)) != -1) {

                        // Extraction de la phrase brute
                        String rawSentence = sentenceBuffer.substring(0, punctuationIndex + 1);

                        // 2. NETTOYAGE : On enlève les * et autres bruits pour l'audio uniquement
                        String cleanSentence = cleanForTTS(rawSentence);

                        // On ne parle que s'il reste quelque chose à dire après nettoyage
                        if (!cleanSentence.isEmpty()) {
                            ttsHandler.speakAsync(cleanSentence, voiceParams.lengthScale, voiceParams.noiseScale, voiceParams.noiseW);
                        }

                        sentenceBuffer.delete(0, punctuationIndex + 1);
                    }
                })
                .doOnComplete(() -> {
                    // Gestion du reliquat (fin de phrase sans point)
                    if (sentenceBuffer.length() > 0) {
                        String cleanRelic = cleanForTTS(sentenceBuffer.toString());
                        if (!cleanRelic.isEmpty()) {
                            ttsHandler.speakAsync(cleanRelic, voiceParams.lengthScale, voiceParams.noiseScale, voiceParams.noiseW);
                        }
                    }

                    // Pour la mémoire, on garde le texte original 'fullResponse' (avec le formatage)
                    String finalResponse = fullResponse.toString();

                    context.addUserMessage(userQuery);
                    context.addAssistantMessage(finalResponse);
                    updateMoodAsync(userQuery, finalResponse);
                    log.info("Complete response : " + fullResponse);
                    triggerPersonalityProcessing(lastInteracted);


                })
                .subscribe();
    }

    private int findSentenceEnd(StringBuilder sb) {
        String text = sb.toString();
        // On cherche les index des ponctuations
        int dot = text.indexOf(".");
        int query = text.indexOf("?");
        int exclam = text.indexOf("!");

        // On trouve le plus petit index non négatif (le premier qui apparaît)
        int minIndex = -1;

        if (dot != -1) minIndex = dot;
        if (query != -1 && (minIndex == -1 || query < minIndex)) minIndex = query;
        if (exclam != -1 && (minIndex == -1 || exclam < minIndex)) minIndex = exclam;

        return minIndex;
    }

    private String cleanForTTS(String text) {
        if (text == null) return "";

        // 1. Remplacer les astérisques (*) et les dièses (#) par rien
        // Le regex [*#]+ signifie : n'importe quelle combinaison de * ou #
        String cleaned = text.replaceAll("[*#]+", "");

        cleaned = cleaned.replaceAll("\\[(.*?)\\]\\(.*?\\)", "$1");

        return cleaned.trim();
    }

    private void updateMoodAsync(String userQuery, String assistantResponse) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("Starting asynchronous mood update...");
                Prompt moodPrompt = promptBuilder.buildMoodUpdatePrompt(context.getPadState(), userQuery, assistantResponse);
                MoodUpdate moodUpdate = llmClient.generateMoodUpdateResponse(moodPrompt);
                moodService.applyMoodUpdate(moodUpdate);
                log.info("Asynchronous mood update completed.");
            } catch (Exception e) {
                log.error("Error during asynchronous mood update", e);
            }
        });
    }

    private void triggerPersonalityProcessing(LocalDateTime lastInteraction) {
        CompletableFuture.runAsync(() -> {
            try {
                // On ne veut pas que la personnalité se déclenche à chaque interaction.
                Duration elapsedTime = Duration.between(lastInteraction, LocalDateTime.now());
                if (elapsedTime.toMinutes() >= 0) {             //todo change after tests
                    log.info("Triggering personality processing...");
                    String fullConversation = context.getFullConversation();
                    personalityOrchestrator.processMemory(fullConversation);
                    lastInteracted = LocalDateTime.now();
                }
            } catch (Exception e) {
                log.error("Error during personality processing", e);
            }
        });

    }
}
