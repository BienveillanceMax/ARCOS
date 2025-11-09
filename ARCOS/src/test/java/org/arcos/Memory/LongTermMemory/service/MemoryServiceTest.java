package org.arcos.Memory.LongTermMemory.service;


import Exceptions.ResponseParsingException;
import LLM.LLMClient;
import LLM.Prompts.PromptBuilder;
import Memory.LongTermMemory.Models.MemoryEntry;
import Memory.LongTermMemory.Models.Subject;
import Memory.LongTermMemory.Repositories.MemoryRepository;
import Memory.LongTermMemory.service.MemoryService;
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
import java.util.List;
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

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private Document toDocument(MemoryEntry memoryEntry) {
        return new Document(memoryEntry.getId(), memoryEntry.getContent(), memoryEntry.getPayload());
    }

    @Test
    void storeMemory_ShouldSaveDocument() {
        // Given
        MemoryEntry memoryEntry = new MemoryEntry("test content", Subject.fromString("SELF"), 0.9);

        // When
        memoryService.storeMemory(memoryEntry);

        // Then
        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(memoryRepository).save(documentCaptor.capture());
        Document capturedDocument = documentCaptor.getValue();
        assertEquals(memoryEntry.getContent(), capturedDocument.getText());
    }

    @Test
    void searchMemories_ShouldReturnListOfMemoryEntries() {
        // Given
        MemoryEntry memoryEntry = new MemoryEntry("test content", Subject.fromString("SELF"), 0.9);
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
        MemoryEntry memoryEntry = new MemoryEntry("test content", Subject.fromString("SELF"), 0.9);
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
        MemoryEntry memoryEntry = new MemoryEntry(conversation, Subject.fromString("SELF"), 0.9);
        when(promptBuilder.buildMemoryPrompt(conversation)).thenReturn(prompt);
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
        doThrow(new ResponseParsingException("LLM error")).when(llmClient).generateMemoryResponse(any(Prompt.class));

        // When & Then
        assertThrows(ResponseParsingException.class, () -> memoryService.memorizeConversation(conversation));
        verify(memoryRepository, never()).save(any(Document.class));
    }

    @Test
    void toDocumentFromDocument_IntegrationTest() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        MemoryEntry originalEntry = new MemoryEntry("test-id", "Original Content", Subject.fromString("SELF"), 0.85, now, new float[]{1.0f, 2.0f});

        // When
        Document document = toDocument(originalEntry);
        MemoryEntry retrievedEntry = memoryService.fromDocument(document); // Assuming fromDocument is public for testing or refactor to allow testing.

        // Then
        assertEquals(originalEntry.getId(), retrievedEntry.getId());
        assertEquals(originalEntry.getContent(), retrievedEntry.getContent());
        assertEquals(originalEntry.getSubject().getValue(), retrievedEntry.getSubject().getValue());
        assertEquals(originalEntry.getSatisfaction(), retrievedEntry.getSatisfaction());
        assertEquals(originalEntry.getTimestamp(), retrievedEntry.getTimestamp());
    }
}
