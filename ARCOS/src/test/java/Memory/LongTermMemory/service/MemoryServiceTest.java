package Memory.LongTermMemory.service;

import LLM.LLMClient;
import LLM.LLMResponseParser;
import LLM.Prompts.PromptBuilder;
import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.Models.MemoryEntry;
import Memory.LongTermMemory.Models.OpinionEntry;
import Memory.LongTermMemory.Models.SearchResult.SearchResult;
import Memory.LongTermMemory.Qdrant.QdrantClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MemoryServiceTest {

    @Mock
    private QdrantClient qdrantClient;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private LLMClient llmClient;

    @Mock
    private PromptBuilder promptBuilder;

    @Mock
    private LLMResponseParser llmResponseParser;

    @InjectMocks
    private MemoryService memoryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(embeddingService.getEmbeddingDimension()).thenReturn(768);
        memoryService = new MemoryService(qdrantClient, embeddingService, llmClient, promptBuilder, llmResponseParser);
    }

    @Test
    void testInitializeCollections_AllExist() {
        when(qdrantClient.collectionExists(anyString())).thenReturn(true);
        assertTrue(memoryService.initializeCollections());
        verify(qdrantClient, times(4)).collectionExists(anyString());
        verify(qdrantClient, never()).createCollection(anyString(), anyInt());
    }

    @Test
    void testInitializeCollections_NoneExist_CreateSuccess() {
        when(qdrantClient.collectionExists(anyString())).thenReturn(false);
        when(qdrantClient.createCollection(anyString(), anyInt())).thenReturn(true);
        assertTrue(memoryService.initializeCollections());
        verify(qdrantClient, times(4)).collectionExists(anyString());
        verify(qdrantClient, times(4)).createCollection(anyString(), anyInt());
    }

    @Test
    void testInitializeCollections_CreateFailure() {
        when(qdrantClient.collectionExists(anyString())).thenReturn(false);
        when(qdrantClient.createCollection(anyString(), anyInt())).thenReturn(false);
        assertFalse(memoryService.initializeCollections());
    }

    @Test
    void testStoreMemory_Success() {
        MemoryEntry memoryEntry = new MemoryEntry();
        memoryEntry.setContent("Test memory");
        float[] embedding = new float[768];

        when(embeddingService.generateEmbedding(anyString())).thenReturn(embedding);
        when(qdrantClient.upsertPoint(anyString(), any(MemoryEntry.class))).thenReturn(true);

        assertTrue(memoryService.storeMemory(memoryEntry));
        assertArrayEquals(embedding, memoryEntry.getEmbedding());
        verify(embeddingService, times(1)).generateEmbedding("Test memory");
        verify(qdrantClient, times(1)).upsertPoint(MemoryService.MEMORIES_COLLECTION, memoryEntry);
    }

    @Test
    void testStoreMemory_EmbeddingFailure() {
        MemoryEntry memoryEntry = new MemoryEntry();
        memoryEntry.setContent("Test memory");

        when(embeddingService.generateEmbedding(anyString())).thenThrow(new RuntimeException("Embedding error"));

        assertFalse(memoryService.storeMemory(memoryEntry));
        verify(qdrantClient, never()).upsertPoint(anyString(), any(MemoryEntry.class));
    }

    @Test
    void testStoreDesire_Success() {
        DesireEntry desireEntry = new DesireEntry();
        desireEntry.setLabel("Test desire");
        desireEntry.setDescription("Desire description");
        float[] embedding = new float[768];

        when(embeddingService.generateEmbedding(anyString())).thenReturn(embedding);
        when(qdrantClient.upsertPoint(anyString(), any(DesireEntry.class))).thenReturn(true);

        assertTrue(memoryService.storeDesire(desireEntry));
        verify(embeddingService, times(1)).generateEmbedding("Test desire. Desire description");
        verify(qdrantClient, times(1)).upsertPoint(MemoryService.DESIRES_COLLECTION, desireEntry);
    }

    @Test
    void testGetDesire_Success() {
        DesireEntry desireEntry = new DesireEntry();
        when(qdrantClient.getPoint(eq(MemoryService.DESIRES_COLLECTION), eq("desire1"), any())).thenReturn(desireEntry);
        assertEquals(desireEntry, memoryService.getDesire("desire1"));
    }

    @Test
    void testSearchOpinions_Success() {
        String query = "test query";
        float[] embedding = new float[768];
        List<SearchResult<OpinionEntry>> expectedResults = Collections.singletonList(new SearchResult<>(new OpinionEntry(), 0.9f));

        when(embeddingService.generateEmbedding(query)).thenReturn(embedding);
        when(qdrantClient.search(eq(MemoryService.OPINIONS_COLLECTION), eq(embedding), eq(10), any())).thenReturn((List) expectedResults);

        List<SearchResult<OpinionEntry>> results = memoryService.searchOpinions(query);

        assertEquals(expectedResults, results);
    }

    @Test
    void testSearchOpinions_EmbeddingFailure() {
        String query = "test query";
        when(embeddingService.generateEmbedding(query)).thenThrow(new RuntimeException("Embedding error"));
        List<SearchResult<OpinionEntry>> results = memoryService.searchOpinions(query);
        assertTrue(results.isEmpty());
    }

    @Test
    void testMemorizeConversation_Success() throws Exception {
        String conversation = "This is a test conversation.";
        String llmResponse = "Parsed response";
        MemoryEntry memoryEntry = new MemoryEntry();
        memoryEntry.setContent("Test content");
        float[] embedding = new float[768];

        when(promptBuilder.buildMemoryPrompt(conversation)).thenReturn("prompt");
        when(llmClient.generateMemoryResponse("prompt")).thenReturn(llmResponse);
        when(llmResponseParser.parseMemoryFromMistralResponse(llmResponse)).thenReturn(memoryEntry);
        when(embeddingService.generateEmbedding(memoryEntry.getContent())).thenReturn(embedding);
        when(qdrantClient.upsertPoint(MemoryService.MEMORIES_COLLECTION, memoryEntry)).thenReturn(true);

        MemoryEntry result = memoryService.memorizeConversation(conversation);

        assertNotNull(result);
        assertArrayEquals(embedding, result.getEmbedding());
        verify(qdrantClient, times(1)).upsertPoint(MemoryService.MEMORIES_COLLECTION, memoryEntry);
    }
}
