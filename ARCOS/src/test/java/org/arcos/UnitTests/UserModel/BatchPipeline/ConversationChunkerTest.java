package org.arcos.UnitTests.UserModel.BatchPipeline;

import org.arcos.UserModel.BatchPipeline.ConversationChunker;
import org.arcos.UserModel.BatchPipeline.Queue.ConversationChunk;
import org.arcos.UserModel.BatchPipeline.Queue.ConversationPair;
import org.arcos.UserModel.BatchPipeline.Queue.QueuedConversation;
import org.arcos.UserModel.UserModelProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConversationChunkerTest {

    private ConversationChunker chunker;

    @BeforeEach
    void setUp() {
        UserModelProperties properties = new UserModelProperties();
        properties.setChunkWindowSize(3);
        properties.setChunkMaxChars(8000);
        chunker = new ConversationChunker(properties);
    }

    @Test
    void chunkConversationWith7PairsAndWindow3_producesThreeChunks() {
        // Given: a conversation with 7 pairs
        List<ConversationPair> pairs = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            pairs.add(new ConversationPair("User message " + i, "Assistant response " + i));
        }
        QueuedConversation conversation = new QueuedConversation(
                "conv-1", pairs, LocalDateTime.now(), false);

        // When
        List<ConversationChunk> chunks = chunker.chunk(conversation);

        // Then: 3 chunks (3 + 3 + 1)
        assertEquals(3, chunks.size());
        assertEquals(3, chunks.get(0).pairs().size());
        assertEquals(3, chunks.get(1).pairs().size());
        assertEquals(1, chunks.get(2).pairs().size());

        // All chunks reference the same conversation
        for (ConversationChunk chunk : chunks) {
            assertEquals("conv-1", chunk.conversationId());
        }
    }

    @Test
    void chunkWithPairsExceedingMaxChars_truncates() {
        // Given: a chunker with low maxChars
        UserModelProperties properties = new UserModelProperties();
        properties.setChunkWindowSize(3);
        properties.setChunkMaxChars(100);
        ConversationChunker smallChunker = new ConversationChunker(properties);

        // Create pairs where each pair is ~60 chars
        List<ConversationPair> pairs = new ArrayList<>();
        String longMsg = "A".repeat(30);
        for (int i = 0; i < 3; i++) {
            pairs.add(new ConversationPair(longMsg, longMsg));
        }
        QueuedConversation conversation = new QueuedConversation(
                "conv-2", pairs, LocalDateTime.now(), false);

        // When
        List<ConversationChunk> chunks = smallChunker.chunk(conversation);

        // Then: first chunk should be truncated to fewer than 3 pairs
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).pairs().size() < 3,
                "Should truncate to fit maxChars");
    }

    @Test
    void chunkEmptyConversation_returnsEmptyList() {
        // Given: an empty conversation
        QueuedConversation conversation = new QueuedConversation(
                "conv-3", List.of(), LocalDateTime.now(), false);

        // When
        List<ConversationChunk> chunks = chunker.chunk(conversation);

        // Then
        assertTrue(chunks.isEmpty());
    }

    @Test
    void chunkConversationWith1Pair_returnsOneChunk() {
        // Given: a conversation with a single pair
        List<ConversationPair> pairs = List.of(
                new ConversationPair("Bonjour", "Salut !"));
        QueuedConversation conversation = new QueuedConversation(
                "conv-4", pairs, LocalDateTime.now(), false);

        // When
        List<ConversationChunk> chunks = chunker.chunk(conversation);

        // Then
        assertEquals(1, chunks.size());
        assertEquals(1, chunks.get(0).pairs().size());
        assertEquals("Bonjour", chunks.get(0).pairs().get(0).userMessage());
    }
}
