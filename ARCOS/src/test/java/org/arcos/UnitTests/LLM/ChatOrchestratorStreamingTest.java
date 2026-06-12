package org.arcos.UnitTests.LLM;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.LLM.Client.ChatOrchestrator;
import org.arcos.Memory.LongTermMemory.Repositories.MemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vectorstore.VectorStore;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ChatOrchestratorStreamingTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS) ChatClient chatClient;
    @Mock ChatClient.Builder chatClientBuilder;
    @Mock MemoryRepository memoryRepository;
    @Mock VectorStore vectorStore;
    @Mock CentralFeedBackHandler feedBackHandler;

    private ChatOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(memoryRepository.getVectorStore()).thenReturn(vectorStore);
        // Action tools are all null — the constructor's nonNull-filter yields an empty tool array.
        orchestrator = new ChatOrchestrator(chatClientBuilder,
                null, null, null, null, null, null, null, null,
                memoryRepository, feedBackHandler, 3);
    }

    private void stubStream(Flux<String> content) {
        when(chatClient.prompt(any(Prompt.class))
                .advisors(any(Advisor.class))
                .tools(any(Object[].class))
                .stream()
                .content())
                .thenReturn(content);
    }

    @Test
    void streamingError_NonRateLimit_EmitsPartialThenFallback_AndCompletes() {
        stubStream(Flux.concat(Flux.just("Bonjour"),
                Flux.error(new RuntimeException("connection reset mid-stream"))));

        StepVerifier.create(orchestrator.generateStreamingChatResponse(new Prompt("salut")))
                .expectNext("Bonjour")
                .expectNextMatches(chunk -> chunk.toLowerCase().contains("problème")
                        || chunk.toLowerCase().contains("coupé"))
                .verifyComplete();
    }

    @Test
    void streamingError_RateLimit_StillPropagatesForCircuitBreakerFallback() {
        RateLimiter neverPermit = RateLimiter.of("test", RateLimiterConfig.custom()
                .limitForPeriod(1)
                .build());
        RequestNotPermitted rateLimitError = RequestNotPermitted.createRequestNotPermitted(neverPermit);
        stubStream(Flux.concat(Flux.just("Bonjour"), Flux.error(rateLimitError)));

        StepVerifier.create(orchestrator.generateStreamingChatResponse(new Prompt("salut")))
                .expectNext("Bonjour")
                .expectError(RequestNotPermitted.class)
                .verify();
    }
}
