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
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import Personality.PersonalityOrchestrator;


import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class Orchestrator
{
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonFactory jsonFactory = new JsonFactory();
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
            triggerPersonalityProcessing(lastInteracted);
            lastInteracted = LocalDateTime.now();

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

        StringBuilder fullJsonResponse = new StringBuilder();
        Queue<String> sentencesToSpeak = new ConcurrentLinkedQueue<>();
        AtomicBoolean streamingComplete = new AtomicBoolean(false);

        PadState currentPad = context.getPadState();
        MoodVoiceMapper.VoiceParams voiceParams = moodVoiceMapper.mapToVoice(currentPad);

        CompletableFuture.runAsync(() -> {
            while (!streamingComplete.get() || !sentencesToSpeak.isEmpty()) {
                String sentence = sentencesToSpeak.poll();
                if (sentence != null) {
                    String unescapedSentence = StringEscapeUtils.unescapeJson(sentence);
                    log.info("Speaking sentence: {}", unescapedSentence);
                    ttsHandler.speak(unescapedSentence, voiceParams.lengthScale, voiceParams.noiseScale, voiceParams.noiseW);
                } else {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });

        try {
            PipedOutputStream outputStream = new PipedOutputStream();
            PipedInputStream inputStream = new PipedInputStream(outputStream);

            // Parser thread
            CompletableFuture<Void> parsingFuture = CompletableFuture.runAsync(() -> {
                try (JsonParser parser = jsonFactory.createParser(inputStream)) {
                    StringBuilder sentenceBuffer = new StringBuilder();
                    JsonToken token;
                    while ((token = parser.nextToken()) != null) {
                        if (token == JsonToken.FIELD_NAME && "response".equals(parser.getCurrentName())) {
                            while (parser.nextToken() != JsonToken.VALUE_STRING) {
                                // Find the value token
                            }
                            String text = parser.getText();
                            sentenceBuffer.append(text);
                            processTextForSentences(sentenceBuffer, sentencesToSpeak);
                        }
                    }
                } catch (IOException e) {
                    log.error("Error while parsing JSON stream", e);
                }
            });

            // Subscriber thread
            Prompt prompt = promptBuilder.buildConversationnalPrompt(context, userQuery);
            log.info("Prompt: {}", prompt);

            llmClient.generateConversationResponseStream(prompt)
                    .doOnNext(chunk -> {
                        try {
                            fullJsonResponse.append(chunk);
                            outputStream.write(chunk.getBytes(StandardCharsets.UTF_8));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .doOnError(error -> {
                        log.error("Error during streaming: {}", error.getMessage());
                        try {
                            outputStream.close();
                        } catch (IOException e) {
                            // ignore
                        }
                        streamingComplete.set(true);
                    })
                    .doOnComplete(() -> {
                        log.info("Streaming complete.");
                        try {
                            outputStream.close();
                        } catch (IOException e) {
                            // ignore
                        }
                        parsingFuture.join(); // Wait for parser to finish
                        streamingComplete.set(true);
                        try {
                            ConversationResponse response = objectMapper.readValue(fullJsonResponse.toString(), ConversationResponse.class);
                            if (response != null) {
                                if (response.moodUpdate != null) {
                                    moodService.applyMoodUpdate(response.moodUpdate);
                                    log.info("Mood updated successfully.");
                                }
                                context.addUserMessage(userQuery);
                                context.addAssistantMessage(response.response);
                                log.info("Conversation context updated.");
                            }
                        } catch (Exception e) {
                            log.error("Failed to parse final JSON response or update context", e);
                        }
                    })
                    .subscribe();
        } catch (IOException e) {
            log.error("Failed to set up piped streams", e);
        }
    }

    private void processTextForSentences(StringBuilder textBuffer, Queue<String> sentencesToSpeak) {
        String[] sentences = textBuffer.toString().split("(?<=[.!?])\\s*");
        textBuffer.setLength(0);
        for (int i = 0; i < sentences.length; i++) {
            String sentence = sentences[i];
            if (i == sentences.length - 1 && !sentence.endsWith(".") && !sentence.endsWith("!") && !sentence.endsWith("?")) {
                textBuffer.append(sentence);
            } else if (!sentence.isEmpty()) {
                sentencesToSpeak.add(sentence);
            }
        }
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
