package org.arcos.UserModel.BatchPipeline;

import lombok.extern.slf4j.Slf4j;
import org.arcos.UserModel.BatchPipeline.Queue.ConversationChunk;
import org.arcos.UserModel.BatchPipeline.Queue.ConversationQueueService;
import org.arcos.UserModel.BatchPipeline.Queue.QueuedConversation;
import org.arcos.UserModel.GdeltThemeIndex.GdeltThemeIndexService;
import org.arcos.UserModel.PersonaTree.PersonaTreeGate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class BatchPipelineOrchestrator {

    private final ConversationQueueService queueService;
    private final ConversationChunker chunker;
    private final MemListenerClient memListenerClient;
    private final MemListenerPromptBuilder promptBuilder;
    private final PersonaTreeGate personaTreeGate;
    private final MemListenerReadinessCheck readinessCheck;
    @Nullable
    private final GdeltThemeIndexService gdeltThemeIndexService; // absent when arcos.gdelt.enabled=false

    private volatile boolean interrupted = false;

    public BatchPipelineOrchestrator(ConversationQueueService queueService,
                                     ConversationChunker chunker,
                                     MemListenerClient memListenerClient,
                                     MemListenerPromptBuilder promptBuilder,
                                     PersonaTreeGate personaTreeGate,
                                     MemListenerReadinessCheck readinessCheck,
                                     @Nullable GdeltThemeIndexService gdeltThemeIndexService) {
        this.queueService = queueService;
        this.chunker = chunker;
        this.memListenerClient = memListenerClient;
        this.promptBuilder = promptBuilder;
        this.personaTreeGate = personaTreeGate;
        this.readinessCheck = readinessCheck;
        this.gdeltThemeIndexService = gdeltThemeIndexService;
    }

    public void runBatch() {
        if (!readinessCheck.isModelReady()) {
            log.debug("MemListener model not available, skipping batch");
            return;
        }
        if (queueService.isEmpty()) {
            log.debug("Queue empty, skipping batch");
            return;
        }
        interrupted = false;
        personaTreeGate.createSnapshot();
        if (gdeltThemeIndexService != null) {
            gdeltThemeIndexService.beginBatch(); // defer GDELT extraction out of the batch hot path
        }

        List<QueuedConversation> conversations = queueService.drainAll();
        log.info("Batch pipeline processing {} conversations", conversations.size());

        try {
            for (int i = 0; i < conversations.size(); i++) {
                if (interrupted) {
                    log.info("Batch pipeline interrupted, re-enqueuing {} remaining conversations",
                            conversations.size() - i);
                    for (int j = i; j < conversations.size(); j++) {
                        queueService.enqueue(conversations.get(j));
                    }
                    personaTreeGate.persist();
                    return;
                }

                QueuedConversation conversation = conversations.get(i);
                processConversation(conversation);
            }

            personaTreeGate.persist();
            log.info("Batch pipeline completed successfully");
        } finally {
            if (gdeltThemeIndexService != null) {
                gdeltThemeIndexService.endBatchAndReconcile(); // one extraction/persist pass
            }
        }
    }

    public void interrupt() {
        interrupted = true;
        log.info("Batch pipeline interrupt requested");
    }

    private void processConversation(QueuedConversation conversation) {
        List<ConversationChunk> chunks = chunker.chunk(conversation);
        log.debug("Processing conversation {} with {} chunks", conversation.id(), chunks.size());

        for (ConversationChunk chunk : chunks) {
            if (interrupted) {
                return;
            }

            try {
                String prompt = promptBuilder.buildPrompt(chunk);
                String response = memListenerClient.generate(prompt);

                if (!response.isEmpty()) {
                    personaTreeGate.applyRawOperations(response, false);
                    log.debug("Applied operations from chunk of conversation {}", conversation.id());
                } else {
                    log.debug("Empty response from MemListener for chunk of conversation {}", conversation.id());
                }
            } catch (Exception e) {
                log.error("Error processing chunk of conversation {}: {}", conversation.id(), e.getMessage());
            }
        }
    }
}
