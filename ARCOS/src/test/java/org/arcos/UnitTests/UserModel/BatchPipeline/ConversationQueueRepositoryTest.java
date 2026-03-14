package org.arcos.UnitTests.UserModel.BatchPipeline;

import org.arcos.UserModel.BatchPipeline.Queue.ConversationPair;
import org.arcos.UserModel.BatchPipeline.Queue.ConversationQueueRepository;
import org.arcos.UserModel.BatchPipeline.Queue.QueuedConversation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConversationQueueRepositoryTest {

    private ConversationQueueRepository repository;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        repository = new ConversationQueueRepository();
    }

    @Test
    void saveAndLoadRoundtrip() {
        // Given
        Path filePath = tempDir.resolve("queue.json");
        LocalDateTime now = LocalDateTime.of(2026, 3, 14, 10, 30, 0);

        List<QueuedConversation> queue = List.of(
                new QueuedConversation("conv-1",
                        List.of(new ConversationPair("Bonjour", "Salut !")),
                        now, false),
                new QueuedConversation("conv-2",
                        List.of(
                                new ConversationPair("Comment vas-tu ?", "Bien merci"),
                                new ConversationPair("Et toi ?", "Super aussi")
                        ),
                        now.plusMinutes(5), true)
        );

        // When
        repository.save(queue, filePath);
        List<QueuedConversation> loaded = repository.load(filePath);

        // Then
        assertEquals(2, loaded.size());

        QueuedConversation first = loaded.get(0);
        assertEquals("conv-1", first.id());
        assertEquals(1, first.pairs().size());
        assertEquals("Bonjour", first.pairs().get(0).userMessage());
        assertEquals("Salut !", first.pairs().get(0).assistantMessage());
        assertEquals(now, first.timestamp());
        assertFalse(first.hadInitiative());

        QueuedConversation second = loaded.get(1);
        assertEquals("conv-2", second.id());
        assertEquals(2, second.pairs().size());
        assertTrue(second.hadInitiative());
    }

    @Test
    void loadFromNonexistentFile_returnsEmptyList() {
        // Given
        Path nonExistent = tempDir.resolve("does-not-exist.json");

        // When
        List<QueuedConversation> result = repository.load(nonExistent);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void atomicWrite_cleansTmpFile() {
        // Given
        Path filePath = tempDir.resolve("queue-atomic.json");
        List<QueuedConversation> queue = List.of(
                new QueuedConversation("conv-1",
                        List.of(new ConversationPair("Hello", "Hi")),
                        LocalDateTime.now(), false)
        );

        // When
        repository.save(queue, filePath);

        // Then: main file exists, tmp file does not
        assertTrue(Files.exists(filePath));
        assertFalse(Files.exists(Path.of(filePath + ".tmp")));
    }
}
