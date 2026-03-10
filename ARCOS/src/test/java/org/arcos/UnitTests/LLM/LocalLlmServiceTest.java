package org.arcos.UnitTests.LLM;

import org.arcos.LLM.Local.LocalLlmHealthIndicator;
import org.arcos.LLM.Local.LocalLlmService;
import org.arcos.LLM.Local.ThinkingMode;
import org.arcos.UserModel.UserModelProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.boot.actuate.health.Status;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LocalLlmServiceTest {

    @Mock
    private OllamaChatModel ollamaChatModel;

    @Mock
    private LocalLlmHealthIndicator healthIndicator;

    private UserModelProperties properties;
    private LocalLlmService service;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        properties = new UserModelProperties();
        properties.getConsolidation().setTimeoutMs(5000);
        properties.getConsolidation().setModel("qwen3.5:4b");
        when(healthIndicator.isOllamaUp()).thenReturn(true);
        service = new LocalLlmService(ollamaChatModel, properties, healthIndicator);
    }

    @AfterEach
    void tearDown() throws Exception {
        service.shutdown();
        mocks.close();
    }

    @Test
    void generateSimpleAsync_shouldCallOllamaWithNoThinkPrefix() throws Exception {
        // Given
        ChatResponse chatResponse = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        var output = mock(org.springframework.ai.chat.messages.AssistantMessage.class);
        when(output.getText()).thenReturn("{\"decision\":\"MERGE\"}");
        when(generation.getOutput()).thenReturn(output);
        when(chatResponse.getResult()).thenReturn(generation);
        when(ollamaChatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        // When
        CompletableFuture<String> future = service.generateSimpleAsync("test prompt");
        String result = future.get(5, TimeUnit.SECONDS);

        // Then
        assertEquals("{\"decision\":\"MERGE\"}", result);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(ollamaChatModel).call(promptCaptor.capture());
        String sentPrompt = promptCaptor.getValue().getContents();
        assertTrue(sentPrompt.startsWith(ThinkingMode.NO_THINK.getPrefix()));
    }

    @Test
    void generateComplexAsync_shouldCallOllamaWithThinkPrefix() throws Exception {
        // Given
        ChatResponse chatResponse = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        var output = mock(org.springframework.ai.chat.messages.AssistantMessage.class);
        when(output.getText()).thenReturn("response");
        when(generation.getOutput()).thenReturn(output);
        when(chatResponse.getResult()).thenReturn(generation);
        when(ollamaChatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        // When
        CompletableFuture<String> future = service.generateComplexAsync("complex prompt");
        String result = future.get(5, TimeUnit.SECONDS);

        // Then
        assertEquals("response", result);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(ollamaChatModel).call(promptCaptor.capture());
        String sentPrompt = promptCaptor.getValue().getContents();
        assertTrue(sentPrompt.startsWith(ThinkingMode.THINK.getPrefix()));
    }

    @Test
    void concurrencyGuard_shouldRejectWhenAlreadyProcessing() throws Exception {
        // Given — make the first call block
        ChatResponse chatResponse = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        var output = mock(org.springframework.ai.chat.messages.AssistantMessage.class);
        when(output.getText()).thenReturn("result");
        when(generation.getOutput()).thenReturn(output);
        when(chatResponse.getResult()).thenReturn(generation);

        var latch = new java.util.concurrent.CountDownLatch(1);
        when(ollamaChatModel.call(any(Prompt.class))).thenAnswer(inv -> {
            latch.await(5, TimeUnit.SECONDS);
            return chatResponse;
        });

        // When — first call will block
        CompletableFuture<String> first = service.generateSimpleAsync("first");
        // Give the executor a moment to start processing
        Thread.sleep(100);

        // Then — second call should fail with RejectedExecutionException
        CompletableFuture<String> second = service.generateSimpleAsync("second");
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> second.get(2, TimeUnit.SECONDS));
        assertInstanceOf(RejectedExecutionException.class, ex.getCause());

        // Cleanup
        latch.countDown();
        first.get(5, TimeUnit.SECONDS);
    }

    @Test
    void timeout_shouldCompleteExceptionallyOnTimeout() {
        // Given — make the call take longer than timeout
        properties.getConsolidation().setTimeoutMs(200);
        service.shutdown();
        service = new LocalLlmService(ollamaChatModel, properties, healthIndicator);

        when(ollamaChatModel.call(any(Prompt.class))).thenAnswer(inv -> {
            Thread.sleep(5000);
            return null;
        });

        // When
        CompletableFuture<String> future = service.generateSimpleAsync("slow prompt");

        // Then
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(3, TimeUnit.SECONDS));
        assertInstanceOf(java.util.concurrent.TimeoutException.class, ex.getCause());
    }

    @Test
    void isProcessing_shouldReflectState() {
        assertFalse(service.isProcessing());
        assertTrue(service.isAvailable());
    }

    @Test
    void isAvailable_shouldBeFalseWhenOllamaDown() {
        when(healthIndicator.isOllamaUp()).thenReturn(false);
        assertFalse(service.isAvailable());
    }

    @Test
    void healthIndicator_shouldReportDownByDefault() {
        // Given
        LocalLlmHealthIndicator indicator = new LocalLlmHealthIndicator("http://localhost:11434");

        // When — no poll has happened
        var health = indicator.health();

        // Then
        assertEquals(Status.DOWN, health.getStatus());
        assertFalse(indicator.isOllamaUp());
    }
}
