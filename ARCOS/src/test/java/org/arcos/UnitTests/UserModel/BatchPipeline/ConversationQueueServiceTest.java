package org.arcos.UnitTests.UserModel.BatchPipeline;

import org.arcos.UserModel.BatchPipeline.Queue.ConversationPair;
import org.arcos.UserModel.BatchPipeline.Queue.ConversationQueueRepository;
import org.arcos.UserModel.BatchPipeline.Queue.ConversationQueueService;
import org.arcos.UserModel.BatchPipeline.Queue.QueuedConversation;
import org.arcos.UserModel.UserModelProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationQueueServiceTest {

    @Mock
    private ConversationQueueRepository repository;

    private ConversationQueueService service;

    @BeforeEach
    void setUp() {
        when(repository.load(any(Path.class))).thenReturn(Collections.emptyList());
        UserModelProperties properties = new UserModelProperties();
        properties.setConversationQueuePath("data/conversation-queue.json");
        service = new ConversationQueueService(repository, properties);
        service.initialize();
    }

    @Test
    void enqueueAddsToQueueAndCallsSave() {
        // Given
        QueuedConversation conversation = new QueuedConversation(
                "conv-1",
                List.of(new ConversationPair("Hello", "Hi")),
                LocalDateTime.now(), false);

        // When
        service.enqueue(conversation);

        // Then
        assertEquals(1, service.size());
        assertFalse(service.isEmpty());
        // save called once in initialize (empty), once in enqueue
        verify(repository, times(1)).save(any(), any(Path.class));
    }

    @Test
    void drainAllReturnsAllAndClearsAndCallsSave() {
        // Given
        QueuedConversation conv1 = new QueuedConversation(
                "conv-1", List.of(new ConversationPair("A", "B")),
                LocalDateTime.now(), false);
        QueuedConversation conv2 = new QueuedConversation(
                "conv-2", List.of(new ConversationPair("C", "D")),
                LocalDateTime.now(), true);

        service.enqueue(conv1);
        service.enqueue(conv2);
        reset(repository);

        // When
        List<QueuedConversation> drained = service.drainAll();

        // Then
        assertEquals(2, drained.size());
        assertTrue(service.isEmpty());
        assertEquals(0, service.size());
        verify(repository, times(1)).save(any(), any(Path.class));
    }

    @Test
    void isEmptyAndSize_reflectQueueState() {
        // Given: empty queue
        assertTrue(service.isEmpty());
        assertEquals(0, service.size());

        // When: enqueue one
        service.enqueue(new QueuedConversation(
                "conv-1", List.of(new ConversationPair("X", "Y")),
                LocalDateTime.now(), false));

        // Then
        assertFalse(service.isEmpty());
        assertEquals(1, service.size());
    }
}
