package org.arcos.LLM.Client;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.arcos.Memory.LongTermMemory.Repositories.MemoryRepository;
import org.arcos.Tools.Actions.CalendarActions;
import org.arcos.Tools.Actions.MemoryActions;
import org.arcos.Tools.Actions.PlannedActionActions;
import org.arcos.Tools.Actions.PythonActions;
import org.arcos.Tools.Actions.SearchActions;
import org.arcos.Tools.Actions.WeatherActions;
import org.arcos.Tools.Actions.WebPageActions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

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
    private final CalendarActions calendarActions;
    private final PythonActions pythonActions;
    private final SearchActions searchActions;
    private final PlannedActionActions plannedActionActions;
    private final MemoryActions memoryActions;
    private final WebPageActions webPageActions;
    private final WeatherActions weatherActions;
    private final QuestionAnswerAdvisor questionAnswerAdvisor;

    public ChatOrchestrator(ChatClient.Builder chatClientBuilder,
                            CalendarActions calendarActions,
                            PythonActions pythonActions,
                            SearchActions searchActions,
                            PlannedActionActions plannedActionActions,
                            MemoryActions memoryActions,
                            WebPageActions webPageActions,
                            WeatherActions weatherActions,
                            MemoryRepository memoryRepository,
                            @Value("${arcos.memory.advisor.top-k:3}") int memoryAdvisorTopK) {
        this.chatClient = chatClientBuilder.build();
        this.calendarActions = calendarActions;
        this.pythonActions = pythonActions;
        this.searchActions = searchActions;
        this.plannedActionActions = plannedActionActions;
        this.memoryActions = memoryActions;
        this.webPageActions = webPageActions;
        this.weatherActions = weatherActions;

        this.questionAnswerAdvisor = QuestionAnswerAdvisor.builder(memoryRepository.getVectorStore())
                .searchRequest(SearchRequest.builder().topK(memoryAdvisorTopK).build())
                .promptTemplate(new PromptTemplate(MEMORY_ADVISOR_PROMPT_TEMPLATE))
                .build();
    }

    @RateLimiter(name = "mistral_free")
    public String generateChatResponse(Prompt prompt) {
        return chatClient.prompt(prompt)
                .advisors(questionAnswerAdvisor)
                .tools(calendarActions, pythonActions, searchActions, plannedActionActions, memoryActions, webPageActions, weatherActions)
                .call()
                .content();
    }

    @RateLimiter(name = "mistral_free")
    public Flux<String> generateStreamingChatResponse(Prompt prompt) {
        return chatClient.prompt(prompt)
                .advisors(questionAnswerAdvisor)
                .tools(calendarActions, pythonActions, searchActions, plannedActionActions, memoryActions, webPageActions, weatherActions)
                .stream()
                .content();
    }
}
