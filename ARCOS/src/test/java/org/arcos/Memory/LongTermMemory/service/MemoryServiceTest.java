package org.arcos.Memory.LongTermMemory.service;


import Exceptions.ResponseParsingException;
import LLM.LLMClient;
import LLM.Prompts.PromptBuilder;
import Memory.LongTermMemory.Models.MemoryEntry;
import Memory.LongTermMemory.Models.Subject;
import Memory.LongTermMemory.Repositories.MemoryRepository;
import Memory.LongTermMemory.service.MemoryService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import common.gson.LocalDateTimeAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MemoryServiceTest {

    @InjectMocks
    private MemoryService memoryService;

    @Mock
    private MemoryRepository memoryRepository;

    @Mock
    private LLMClient llmClient;

    @Mock
    private PromptBuilder promptBuilder;

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private Document toDocument(MemoryEntry memoryEntry) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("entry", gson.toJson(memoryEntry));
        return new Document(memoryEntry.getId(), memoryEntry.getContent(), metadata);
    }

    @Test
    void storeMemory_ShouldSaveDocument() {
        // Given
        MemoryEntry memoryEntry = new MemoryEntry("test content", Subject.SELF, 0.9);

        // When
        memoryService.storeMemory(memoryEntry);

        // Then
        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(memoryRepository).save(documentCaptor.capture());
        Document capturedDocument = documentCaptor.getValue();
        assertEquals(memoryEntry.getContent(), capturedDocument.getMetadata().get("content"));
    }

    @Test
    void searchMemories_ShouldReturnListOfMemoryEntries() {
        // Given
        MemoryEntry memoryEntry = new MemoryEntry("test content", Subject.SELF, 0.9);
        Document document = toDocument(memoryEntry);
        when(memoryRepository.search(any())).thenReturn(Collections.singletonList(document));

        // When
        List<MemoryEntry> results = memoryService.searchMemories("test query");

        // Then
        assertFalse(results.isEmpty());
        assertEquals(memoryEntry.getContent(), results.get(0).getContent());
    }

    @Test
    void getMemory_WhenFound_ShouldReturnMemoryEntry() {
        // Given
        MemoryEntry memoryEntry = new MemoryEntry("test content", Subject.SELF, 0.9);
        Document document = toDocument(memoryEntry);
        when(memoryRepository.findById(memoryEntry.getId())).thenReturn(Optional.of(document));

        // When
        MemoryEntry result = memoryService.getMemory(memoryEntry.getId());

        // Then
        assertNotNull(result);
        assertEquals(memoryEntry.getContent(), result.getContent());
    }

    @Test
    void getMemory_WhenNotFound_ShouldReturnNull() {
        // Given
        when(memoryRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // When
        MemoryEntry result = memoryService.getMemory("nonexistent");

        // Then
        assertNull(result);
    }

    @Test
    void deleteMemory_ShouldCallRepositoryDelete() {
        // Given
        String memoryId = "test-id";

        // When
        memoryService.deleteMemory(memoryId);

        // Then
        verify(memoryRepository).delete(Collections.singletonList(memoryId));
    }

    @Test
    void memorizeConversation_Success_ShouldStoreAndReturnMemory() throws ResponseParsingException {
        // Given
        String conversation = "test conversation";
        Prompt prompt = new Prompt("test prompt");
        MemoryEntry memoryEntry = new MemoryEntry(conversation, Subject.SELF, 0.9);
        when(promptBuilder.buildMemoryPrompt(conversation)).thenReturn(prompt);         //TODO verify ?
        when(llmClient.generateMemoryResponse(prompt)).thenReturn(memoryEntry);

        // When
        MemoryEntry result = memoryService.memorizeConversation(conversation);

        // Then
        verify(memoryRepository).save(any(Document.class));
        assertEquals(memoryEntry, result);
    }

    @Test
    void memorizeConversation_Failure_ShouldThrowException() throws ResponseParsingException {
        // Given
        String conversation = "test conversation";
        Prompt prompt = new Prompt("test prompt");
        when(promptBuilder.buildMemoryPrompt(conversation)).thenReturn(prompt);
        when(llmClient.generateMemoryResponse(prompt)).thenThrow(new ResponseParsingException("LLM error"));

        // When & Then
        assertThrows(ResponseParsingException.class, () -> memoryService.memorizeConversation(conversation));
        verify(memoryRepository, never()).save(any(Document.class));
    }
}
