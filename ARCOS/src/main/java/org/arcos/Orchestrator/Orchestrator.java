package org.arcos.Orchestrator;

import org.arcos.Configuration.AudioProperties;
import org.arcos.EventBus.EventQueue;
import org.arcos.EventBus.Events.Event;
import org.arcos.EventBus.Events.EventType;
import org.arcos.EventBus.Events.WakeWordEvent;
import org.arcos.Producers.WakeWordProducer;
import org.arcos.IO.OuputHandling.PiperEmbeddedTTSModule;
import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.IO.OuputHandling.StateHandler.FeedBackEvent;
import org.arcos.IO.OuputHandling.StateHandler.UXEventType;
import org.arcos.Memory.ConversationSummaryService;
import org.arcos.Memory.LongTermMemory.Models.DesireEntry;
import org.arcos.LLM.Client.ChatOrchestrator;
import org.arcos.LLM.Client.LLMClient;
import org.arcos.Memory.ConversationContext;
import org.arcos.Memory.LongTermMemory.service.MemoryService;
import org.arcos.LLM.Prompts.PromptBuilder;
import org.arcos.Personality.Desires.DesireService;
import org.arcos.Personality.Initiative.InitiativeService;
import org.arcos.Personality.Mood.MoodService;
import org.arcos.Personality.Mood.MoodStateHolder;
import org.arcos.Personality.Mood.MoodVoiceMapper;
import org.arcos.Personality.Mood.MoodUpdate;
import org.arcos.Personality.Mood.PadState;
import org.arcos.PlannedAction.ExecutionHistoryService;
import org.arcos.PlannedAction.Models.PlannedActionEntry;
import org.arcos.PlannedAction.PlannedActionExecutor;
import org.arcos.PlannedAction.PlannedActionService;
import org.arcos.Memory.ConversationMessage;
import org.arcos.Producers.InactivityProducer;
import org.arcos.UserModel.BatchPipeline.BatchPipelineOrchestrator;
import org.arcos.UserModel.BatchPipeline.Queue.ConversationPair;
import org.arcos.UserModel.BatchPipeline.Queue.ConversationQueueService;
import org.arcos.UserModel.BatchPipeline.Queue.QueuedConversation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.arcos.Personality.PersonalityOrchestrator;


import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@Slf4j
public class Orchestrator
{
    private final EventQueue eventQueue;
    private final LLMClient llmClient;
    private final ChatOrchestrator chatOrchestrator;
    private final PromptBuilder promptBuilder;
    private final ConversationContext context;
    private final MemoryService memoryService;
    private final InitiativeService initiativeService;
    private final PiperEmbeddedTTSModule ttsHandler;
    private final PersonalityOrchestrator personalityOrchestrator;
    private final MoodService moodService;
    private final MoodStateHolder moodStateHolder;
    private final MoodVoiceMapper moodVoiceMapper;
    private final CentralFeedBackHandler centralFeedBackHandler;
    private final PlannedActionExecutor plannedActionExecutor;
    private final PlannedActionService plannedActionService;
    private final ExecutionHistoryService executionHistoryService;

    private final ConversationSummaryService conversationSummaryService;
    private final WakeWordProducer wakeWordProducer;
    private final AudioProperties audioProperties;
    private final ConversationQueueService conversationQueueService;
    private final InactivityProducer inactivityProducer;
    private final BatchPipelineOrchestrator batchPipelineOrchestrator;
    private volatile boolean isExecutingAction = false;
    private volatile boolean inConversationMode = false;

    private volatile boolean running = true;
    private DesireService desireService;
    static final int MIN_MESSAGES_FOR_SUMMARY = 6;
    static final String LLM_UNAVAILABLE_MESSAGE =
            "Désolé, le service de langage est temporairement indisponible. Réessaie dans quelques instants.";
    private final ExecutorService moodExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mood-updater");
        t.setDaemon(true);
        return t;
    });
    private final ExecutorService personalityExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "personality-processor");
        t.setDaemon(true);
        return t;
    });

    @Autowired
    public Orchestrator(CentralFeedBackHandler centralFeedBackHandler, PersonalityOrchestrator personalityOrchestrator, EventQueue evenQueue, LLMClient llmClient, ChatOrchestrator chatOrchestrator, PromptBuilder promptBuilder, ConversationContext context, MemoryService memoryService, InitiativeService initiativeService, DesireService desireService, MoodService moodService, MoodStateHolder moodStateHolder, MoodVoiceMapper moodVoiceMapper, PlannedActionExecutor plannedActionExecutor, PlannedActionService plannedActionService, ExecutionHistoryService executionHistoryService, WakeWordProducer wakeWordProducer, AudioProperties audioProperties, ConversationSummaryService conversationSummaryService, @Nullable ConversationQueueService conversationQueueService, @Nullable InactivityProducer inactivityProducer, @Nullable BatchPipelineOrchestrator batchPipelineOrchestrator) {
        this.ttsHandler = new PiperEmbeddedTTSModule();
        this.desireService = desireService;
        this.centralFeedBackHandler = centralFeedBackHandler;
        this.eventQueue = evenQueue;
        this.llmClient = llmClient;
        this.chatOrchestrator = chatOrchestrator;
        this.promptBuilder = promptBuilder;
        this.context = context;
        this.memoryService = memoryService;
        this.initiativeService = initiativeService;
        this.personalityOrchestrator = personalityOrchestrator;
        this.moodService = moodService;
        this.moodStateHolder = moodStateHolder;
        this.moodVoiceMapper = moodVoiceMapper;
        this.plannedActionExecutor = plannedActionExecutor;
        this.plannedActionService = plannedActionService;
        this.executionHistoryService = executionHistoryService;
        this.wakeWordProducer = wakeWordProducer;
        this.audioProperties = audioProperties;
        this.conversationSummaryService = conversationSummaryService;
        this.conversationQueueService = conversationQueueService;
        this.inactivityProducer = inactivityProducer;
        this.batchPipelineOrchestrator = batchPipelineOrchestrator;
    }


    public void dispatch(Event<?> event) {
        if (event.getType() == EventType.WAKEWORD) {
            log.info("starting processing");
            boolean isMultiTurn = (event instanceof WakeWordEvent) && ((WakeWordEvent) event).isMultiTurn();
            processAndSpeak((String) event.getPayload(), isMultiTurn);
        } else if (event.getType() == EventType.LISTENING_WINDOW_TIMEOUT) {
            inConversationMode = false;
            log.info("Mode conversation terminé — retour veille standard");
        } else if (event.getType() == EventType.SESSION_END) {
            endSession();
        } else if (event.getType() == EventType.IDLE_WINDOW_OPEN) {
            triggerBatchPipeline();
        } else if (event.getType() == EventType.INITIATIVE) {
            isExecutingAction = true;
            DesireEntry desire = (DesireEntry) event.getPayload();
            try {
                centralFeedBackHandler.handleFeedBack(new FeedBackEvent(UXEventType.INITIATIVE_START));
                boolean success = initiativeService.processInitiative(desire);
                centralFeedBackHandler.handleFeedBack(new FeedBackEvent(
                        success ? UXEventType.INITIATIVE_END : UXEventType.FAILURE));
            } catch (CallNotPermittedException e) {
                handleLlmUnavailable();
            } finally {
                isExecutingAction = false;
            }
        } else if (event.getType() == EventType.CALENDAR_EVENT_SCHEDULER) {
            try {
                ttsHandler.speakAsync(llmClient.generateToollessResponse(promptBuilder.buildSchedulerAlertPrompt((event.getPayload()))));
            } catch (CallNotPermittedException e) {
                log.warn("Circuit breaker OPEN — fallback calendrier sans LLM");
                ttsHandler.speakAsync("Rappel d'événement : " + event.getPayload());
            }
        } else if (event.getType() == EventType.PLANNED_ACTION) {
            isExecutingAction = true;
            PlannedActionEntry action = (PlannedActionEntry) event.getPayload();
            try {
                if (action.isReminderTrigger()) {
                    String reminderMessage = buildReminderMessage(action);
                    ttsHandler.speakAsync(reminderMessage);
                    action.setReminderTrigger(false);
                } else {
                    String result = plannedActionExecutor.execute(action);
                    ttsHandler.speakAsync(result);
                    executionHistoryService.recordExecution(action, result, true);
                    if (!action.isHabit()) {
                        plannedActionService.markCompleted(action);
                    }
                }
            } catch (Exception e) {
                log.error("Error executing planned action {}", action.getId(), e);
                centralFeedBackHandler.handleFeedBack(new FeedBackEvent(UXEventType.FAILURE));
                executionHistoryService.recordExecution(action, e.getMessage(), false);
            } finally {
                isExecutingAction = false;
            }
        }
    }

    public void start() {
        log.info("Orchestrator starting");
        while (running) {
            try {
                Event<?> event = eventQueue.poll(500);
                if (event != null) {
                    dispatch(event);
                }
            } catch (InterruptedException e) {
                log.info("Orchestrator interrupted, stopping");
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("Orchestrator stopped");
    }

    @PreDestroy
    public void stop() {
        log.info("Orchestrator shutdown requested");
        running = false;
        moodExecutor.shutdownNow();
        personalityExecutor.shutdownNow();
        ttsHandler.shutdown();
    }

    private String buildReminderMessage(PlannedActionEntry action) {
        StringBuilder message = new StringBuilder("Rappel : ").append(action.getLabel());
        if (action.getDeadlineDatetime() != null) {
            long minutesUntil = ChronoUnit.MINUTES.between(LocalDateTime.now(), action.getDeadlineDatetime());
            if (minutesUntil > 60) {
                long hours = minutesUntil / 60;
                message.append(" — échéance dans ").append(hours).append(" heure").append(hours > 1 ? "s" : "");
            } else if (minutesUntil > 0) {
                message.append(" — échéance dans ").append(minutesUntil).append(" minute").append(minutesUntil > 1 ? "s" : "");
            } else {
                message.append(" — échéance dépassée");
            }
        }
        if (action.hasContext()) {
            message.append(" — ").append(action.getContext());
        }
        return message.toString();
    }

    private void processAndSpeak(String userQuery, boolean isMultiTurn) {
        log.info("Processing query: {}", userQuery);
        if (inactivityProducer != null) {
            inactivityProducer.recordInteraction();
        }
        if (batchPipelineOrchestrator != null) {
            batchPipelineOrchestrator.interrupt();
        }
        centralFeedBackHandler.handleFeedBack(new FeedBackEvent(UXEventType.THINKING_START));

        // Create the prompt for streaming response
        Prompt streamingPrompt = promptBuilder.buildConversationnalPrompt(context, userQuery);
        log.info("Streaming Prompt: {}", streamingPrompt);

        // Get Voice Parameters based on current Mood
        PadState currentPad = moodStateHolder.getPadState();
        MoodVoiceMapper.VoiceParams voiceParams = moodVoiceMapper.mapToVoice(currentPad);
        // Suspend mic processing before TTS starts to prevent audio feedback
        wakeWordProducer.suspend();

        // Callback post-TTS : resume mic (conversation window or wake word detection)
        Runnable onTtsDone = () -> {
            if (audioProperties.isMultiTurnEnabled() && !isExecutingAction) {
                inConversationMode = true;
                wakeWordProducer.openConversationWindow(audioProperties.getPostResponseListeningWindowMs());
                log.debug("Fenêtre de conversation ouverte ({} ms)", audioProperties.getPostResponseListeningWindowMs());
            } else {
                wakeWordProducer.resumeDetection();
            }
        };

        try {
            generateFluxAndSpeak(streamingPrompt, userQuery, voiceParams, onTtsDone);
        } catch (CallNotPermittedException e) {
            handleLlmUnavailable();
        }

    }

    private void handleLlmUnavailable() {
        log.warn("Circuit breaker Mistral OPEN — dégradation gracieuse, feedback vocal");
        ttsHandler.speakAsync(LLM_UNAVAILABLE_MESSAGE);
        wakeWordProducer.resumeDetection();
        centralFeedBackHandler.handleFeedBack(new FeedBackEvent(UXEventType.FAILURE));
    }

    private void generateFluxAndSpeak(Prompt streamingPrompt, String userQuery, MoodVoiceMapper.VoiceParams voiceParams, Runnable onTtsDone) {
        StringBuilder sentenceBuffer = new StringBuilder();
        StringBuilder fullResponse = new StringBuilder();
        chatOrchestrator.generateStreamingChatResponse(streamingPrompt)
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
                            ttsHandler.speakAsync(cleanRelic, voiceParams.lengthScale, voiceParams.noiseScale, voiceParams.noiseW, onTtsDone);
                        } else {
                            ttsHandler.afterPlayback(onTtsDone);
                        }
                    } else {
                        ttsHandler.afterPlayback(onTtsDone);
                    }

                    // Pour la mémoire, on garde le texte original 'fullResponse' (avec le formatage)
                    String finalResponse = fullResponse.toString();

                    context.addUserMessage(userQuery);
                    context.addAssistantMessage(finalResponse);
                    updateMoodAsync(userQuery, finalResponse);
                    log.info("Complete response : " + fullResponse);
                })
                .subscribe(
                        unused -> {},
                        error -> {
                            log.error("Error in streaming response", error);
                            centralFeedBackHandler.handleFeedBack(new FeedBackEvent(UXEventType.FAILURE));
                            wakeWordProducer.resumeDetection();
                        }
                );
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
        moodExecutor.submit(() -> {
            try {
                log.info("Starting asynchronous mood update...");
                Prompt moodPrompt = promptBuilder.buildMoodUpdatePrompt(moodStateHolder.getPadState(), userQuery, assistantResponse);
                MoodUpdate moodUpdate = llmClient.generateMoodUpdateResponse(moodPrompt);
                moodService.applyMoodUpdate(moodUpdate);
                log.info("Asynchronous mood update completed.");
            } catch (Exception e) {
                log.error("Error during asynchronous mood update", e);
            }
        });
    }

    private void endSession() {
        int messageCount = context.getMessageCount();
        if (messageCount == 0) {
            return;
        }

        // Capture snapshots before startNewSession() clears state
        String fullConversation = context.getFullConversation();
        List<ConversationMessage> messages = context.getMessageHistory();

        processPersonalityAndEnqueue(fullConversation, messages);

        if (messageCount >= MIN_MESSAGES_FOR_SUMMARY) {
            conversationSummaryService.summarizeAsync(personalityExecutor, fullConversation)
                    .thenAccept(summary -> {
                        context.setPreviousSessionSummary(summary);
                        context.startNewSession();
                        log.info("Session ended ({} messages). Summary: {}", messageCount, summary);
                    });
        } else {
            context.startNewSession();
            log.info("Session ended ({} messages, below threshold — no summary)", messageCount);
        }
    }

    private void processPersonalityAndEnqueue(String fullConversation, List<ConversationMessage> messages) {
        personalityExecutor.submit(() -> {
            try {
                personalityOrchestrator.processMemory(fullConversation);

                if (conversationQueueService != null) {
                    List<ConversationPair> pairs = new ArrayList<>();
                    String pendingUser = null;
                    for (ConversationMessage msg : messages) {
                        if (msg.getType() == ConversationMessage.MessageType.USER) {
                            pendingUser = msg.getContent();
                        } else if (msg.getType() == ConversationMessage.MessageType.ASSISTANT && pendingUser != null) {
                            pairs.add(new ConversationPair(pendingUser, msg.getContent()));
                            pendingUser = null;
                        }
                    }
                    if (!pairs.isEmpty()) {
                        QueuedConversation queued = new QueuedConversation(
                                UUID.randomUUID().toString(), pairs, LocalDateTime.now(), false);
                        conversationQueueService.enqueue(queued);
                        log.debug("Enqueued {} pairs for batch processing", pairs.size());
                    }
                }
            } catch (Exception e) {
                log.error("Error during session-end personality processing", e);
            }
        });
    }

    private void triggerBatchPipeline() {
        if (batchPipelineOrchestrator != null) {
            personalityExecutor.submit(() -> {
                try {
                    batchPipelineOrchestrator.runBatch();
                } catch (Exception e) {
                    log.error("Error running batch pipeline", e);
                }
            });
        }
    }
}
