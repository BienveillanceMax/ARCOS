package org.arcos.LLM.Client;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.IO.OuputHandling.StateHandler.FeedBackEvent;
import org.arcos.IO.OuputHandling.StateHandler.UXEventType;
import org.arcos.Memory.LongTermMemory.Repositories.MemoryRepository;
import org.arcos.Tools.Actions.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.Objects;

/**
 * Tool-equipped chat orchestrator for user-facing conversations.
 * Separated from LLMClient to break the circular dependency:
 * ChatOrchestrator → MemoryActions → MemoryService → LLMClient (linear, no cycle).
 */
@Slf4j
@Component
public class ChatOrchestrator {

    private static final String MEMORY_ADVISOR_PROMPT_TEMPLATE = """
            {query}

            ## Souvenirs pertinents
            {question_answer_context}
            """;

    private final ChatClient chatClient;
    private final QuestionAnswerAdvisor questionAnswerAdvisor;
    private final Object[] tools;
    private final CentralFeedBackHandler feedBackHandler;

    public ChatOrchestrator(ChatClient.Builder chatClientBuilder,
                            CalendarActions calendarActions,
                            PythonActions pythonActions,
                            SearchActions searchActions,
                            PlannedActionActions plannedActionActions,
                            MemoryActions memoryActions,
                            WebPageActions webPageActions,
                            WeatherActions weatherActions,
                            @Nullable GdeltActions gdeltActions,
                            MemoryRepository memoryRepository,
                            CentralFeedBackHandler feedBackHandler,
                            @Value("${arcos.memory.advisor.top-k:3}") int memoryAdvisorTopK) {
        this.chatClient = chatClientBuilder.build();
        this.feedBackHandler = feedBackHandler;

        this.tools = Arrays.stream(new Object[]{
                calendarActions, pythonActions, searchActions, plannedActionActions,
                memoryActions, webPageActions, weatherActions, gdeltActions
        }).filter(Objects::nonNull).toArray();

        this.questionAnswerAdvisor = QuestionAnswerAdvisor.builder(memoryRepository.getVectorStore())
                .searchRequest(SearchRequest.builder().topK(memoryAdvisorTopK).build())
                .promptTemplate(new PromptTemplate(MEMORY_ADVISOR_PROMPT_TEMPLATE))
                .build();
    }

    @CircuitBreaker(name = "mistral_free", fallbackMethod = "generateChatResponseFallback")
    @RateLimiter(name = "mistral_free")
    public String generateChatResponse(Prompt prompt) {
        return chatClient.prompt(prompt)
                .advisors(questionAnswerAdvisor)
                .tools(tools)
                .call()
                .content();
    }

    @CircuitBreaker(name = "mistral_free", fallbackMethod = "generateStreamingChatResponseFallback")
    @RateLimiter(name = "mistral_free")
    public Flux<String> generateStreamingChatResponse(Prompt prompt) {
        return chatClient.prompt(prompt)
                .advisors(questionAnswerAdvisor)
                .tools(tools)
                .stream()
                .content()
                .onErrorResume(e -> {
                    if (e instanceof CallNotPermittedException || e instanceof RequestNotPermitted) {
                        return Flux.error(e);
                    }
                    log.warn("Streaming content error (likely tool-call chunk): {}", e.getMessage());
                    return Flux.empty();
                });
    }

    private String generateChatResponseFallback(Prompt prompt, Throwable t) {
        log.error("Mistral indisponible (chat): {}", t.getMessage());
        feedBackHandler.handleFeedBack(new FeedBackEvent(UXEventType.FAILURE));
        return "Je suis désolé, je rencontre un problème technique. Réessaie dans quelques instants.";
    }

    private Flux<String> generateStreamingChatResponseFallback(Prompt prompt, Throwable t) {
        log.error("Mistral indisponible (streaming): {}", t.getMessage());
        feedBackHandler.handleFeedBack(new FeedBackEvent(UXEventType.FAILURE));
        return Flux.just("Je suis désolé, je rencontre un problème technique. Réessaie dans quelques instants.");
    }
}
