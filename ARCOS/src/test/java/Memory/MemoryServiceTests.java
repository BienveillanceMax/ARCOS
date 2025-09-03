package Memory;

import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.Qdrant.QdrantClient;
import Memory.LongTermMemory.service.EmbeddingService;
import Memory.LongTermMemory.service.MemoryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import LLM.LLMClient;
import LLM.LLMResponseParser;
import LLM.Prompts.PromptBuilder;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MemoryServiceTests
{

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

    private MemoryService memoryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Manual instantiation with mocks after openMocks()
        memoryService = new MemoryService(qdrantClient, embeddingService, llmClient, promptBuilder, llmResponseParser);
    }

    @Test
    void testGetPendingDesires() {
        // Arrange
        DesireEntry desire = new DesireEntry();
        when(qdrantClient.scroll(anyString(), any(), any())).thenReturn(Collections.singletonList(desire));

        // Act
        List<DesireEntry> result = memoryService.getPendingDesires();

        // Assert
        assertEquals(1, result.size());
        assertEquals(desire, result.get(0));

        ArgumentCaptor<ObjectNode> filterCaptor = ArgumentCaptor.forClass(ObjectNode.class);
        verify(qdrantClient).scroll(eq(MemoryService.DESIRES_COLLECTION), filterCaptor.capture(), any());

        ObjectNode capturedFilter = filterCaptor.getValue();
        JsonNode mustNode = capturedFilter.get("must").get(0);
        String key = mustNode.get("key").asText();
        String value = mustNode.get("match").get("value").asText();

        assertEquals("status", key);
        assertEquals("PENDING", value);
    }
}

