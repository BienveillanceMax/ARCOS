package org.arcos.UnitTests.UserModel.BatchPipeline;

import org.arcos.UserModel.BatchPipeline.*;
import org.arcos.UserModel.BatchPipeline.Queue.ConversationChunk;
import org.arcos.UserModel.BatchPipeline.Queue.ConversationPair;
import org.arcos.UserModel.BatchPipeline.Queue.ConversationQueueService;
import org.arcos.UserModel.BatchPipeline.Queue.QueuedConversation;
import org.arcos.UserModel.GdeltThemeIndex.GdeltThemeIndexService;
import org.arcos.UserModel.PersonaTree.PersonaTreeGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchPipelineOrchestratorTest {

    @Mock private ConversationQueueService queueService;
    @Mock private ConversationChunker chunker;
    @Mock private MemListenerClient memListenerClient;
    @Mock private MemListenerPromptBuilder promptBuilder;
    @Mock private PersonaTreeGate personaTreeGate;
    @Mock private MemListenerReadinessCheck readinessCheck;
    @Mock private GdeltThemeIndexService gdeltThemeIndexService;

    private BatchPipelineOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new BatchPipelineOrchestrator(
                queueService, chunker, memListenerClient,
                promptBuilder, personaTreeGate,
                readinessCheck, gdeltThemeIndexService);
    }

    @Test
    void runBatch_processesConversationsThroughFullPipeline() {
        // Given
        QueuedConversation conv = new QueuedConversation(
                "conv-1",
                List.of(new ConversationPair("Hello", "Hi")),
                LocalDateTime.now(), false);

        ConversationChunk chunk = new ConversationChunk(
                List.of(new ConversationPair("Hello", "Hi")), "conv-1");

        when(readinessCheck.isModelReady()).thenReturn(true);
        when(queueService.isEmpty()).thenReturn(false);
        when(queueService.drainAll()).thenReturn(List.of(conv));
        when(chunker.chunk(conv)).thenReturn(List.of(chunk));
        when(promptBuilder.buildPrompt(chunk)).thenReturn("test prompt");
        when(memListenerClient.generate("test prompt")).thenReturn("ADD(\"path\", \"value\")");
        when(personaTreeGate.createSnapshot()).thenReturn(Path.of("snapshot.json"));

        // When
        orchestrator.runBatch();

        // Then
        verify(personaTreeGate).createSnapshot();
        verify(queueService).drainAll();
        verify(chunker).chunk(conv);
        verify(promptBuilder).buildPrompt(chunk);
        verify(memListenerClient).generate("test prompt");
        verify(personaTreeGate).applyRawOperations("ADD(\"path\", \"value\")", false);
        verify(personaTreeGate).persist();
    }

    @Test
    void interrupt_causesRemainingConversationsToBeReEnqueued() {
        // Given: 3 conversations, interrupt after first
        QueuedConversation conv1 = new QueuedConversation("conv-1",
                List.of(new ConversationPair("A", "B")), LocalDateTime.now(), false);
        QueuedConversation conv2 = new QueuedConversation("conv-2",
                List.of(new ConversationPair("C", "D")), LocalDateTime.now(), false);
        QueuedConversation conv3 = new QueuedConversation("conv-3",
                List.of(new ConversationPair("E", "F")), LocalDateTime.now(), false);

        ConversationChunk chunk1 = new ConversationChunk(
                List.of(new ConversationPair("A", "B")), "conv-1");

        when(readinessCheck.isModelReady()).thenReturn(true);
        when(queueService.isEmpty()).thenReturn(false);
        when(queueService.drainAll()).thenReturn(List.of(conv1, conv2, conv3));
        when(chunker.chunk(conv1)).thenReturn(List.of(chunk1));
        when(promptBuilder.buildPrompt(chunk1)).thenReturn("prompt");
        when(memListenerClient.generate("prompt")).thenAnswer(invocation -> {
            // Interrupt after processing first conversation
            orchestrator.interrupt();
            return "NO_OP()";
        });
        when(personaTreeGate.createSnapshot()).thenReturn(Path.of("snapshot.json"));

        // When
        orchestrator.runBatch();

        // Then: conv2 and conv3 should be re-enqueued
        verify(queueService).enqueue(conv2);
        verify(queueService).enqueue(conv3);
        // persist should be called to durably save mutations applied before the interrupt
        verify(personaTreeGate).persist();
    }

    @Test
    void runBatch_bracketsDrainLoopWithGdeltBatchLifecycle() {
        QueuedConversation conv = new QueuedConversation("conv-1",
                List.of(new ConversationPair("Hello", "Hi")), LocalDateTime.now(), false);
        ConversationChunk chunk = new ConversationChunk(
                List.of(new ConversationPair("Hello", "Hi")), "conv-1");
        when(readinessCheck.isModelReady()).thenReturn(true);
        when(queueService.isEmpty()).thenReturn(false);
        when(queueService.drainAll()).thenReturn(List.of(conv));
        when(chunker.chunk(conv)).thenReturn(List.of(chunk));
        when(promptBuilder.buildPrompt(chunk)).thenReturn("p");
        when(memListenerClient.generate("p")).thenReturn("ADD(\"path\", \"value\")");
        when(personaTreeGate.createSnapshot()).thenReturn(Path.of("snapshot.json"));

        orchestrator.runBatch();

        InOrder inOrder = inOrder(gdeltThemeIndexService, personaTreeGate);
        inOrder.verify(gdeltThemeIndexService).beginBatch();
        inOrder.verify(personaTreeGate).persist();
        inOrder.verify(gdeltThemeIndexService).endBatchAndReconcile();
    }

    @Test
    void interrupt_stillReconcilesGdeltBatchOnce() {
        QueuedConversation conv1 = new QueuedConversation("conv-1",
                List.of(new ConversationPair("A", "B")), LocalDateTime.now(), false);
        QueuedConversation conv2 = new QueuedConversation("conv-2",
                List.of(new ConversationPair("C", "D")), LocalDateTime.now(), false);
        ConversationChunk chunk1 = new ConversationChunk(
                List.of(new ConversationPair("A", "B")), "conv-1");
        when(readinessCheck.isModelReady()).thenReturn(true);
        when(queueService.isEmpty()).thenReturn(false);
        when(queueService.drainAll()).thenReturn(List.of(conv1, conv2));
        when(chunker.chunk(conv1)).thenReturn(List.of(chunk1));
        when(promptBuilder.buildPrompt(chunk1)).thenReturn("prompt");
        when(memListenerClient.generate("prompt")).thenAnswer(i -> { orchestrator.interrupt(); return "NO_OP()"; });
        when(personaTreeGate.createSnapshot()).thenReturn(Path.of("snapshot.json"));

        orchestrator.runBatch();

        verify(gdeltThemeIndexService).beginBatch();
        verify(gdeltThemeIndexService).endBatchAndReconcile();
    }
}
