package Personality;

import LLM.LLMClient;
import LLM.LLMResponseParser;
import LLM.Prompts.PromptBuilder;
import Memory.Actions.ActionRegistry;
import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.Models.MemoryEntry;
import Memory.LongTermMemory.Models.OpinionEntry;
import Memory.LongTermMemory.Models.SearchResult.SearchResult;
import Memory.LongTermMemory.service.EmbeddingService;
import Memory.LongTermMemory.service.MemoryService;
import Personality.Desires.DesireService;
import Personality.Opinions.OpinionService;
import Personality.Values.ValueProfile;
import Producers.DesireInitativeProducer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mistralai.MistralAiChatModel;
import org.springframework.ai.mistralai.MistralAiChatOptions;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.resolution.DelegatingToolCallbackResolver;
import org.springframework.retry.support.RetryTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Integration Test for PersonalityOrchestrator.
 *
 * NOTE: This test is disabled by default as it requires a running Qdrant instance
 * and a valid Mistral AI API key.
 *
 * To run this test:
 * 1. Ensure a Qdrant container is running and accessible on localhost:6333.
 * 2. Provide a valid Mistral AI API key as an environment variable (e.g., MISTRAL_API_KEY).
 * 3. Remove or comment out the @Disabled annotation below.
 */
@Disabled("Requires a running Qdrant instance and a valid API key.")
public class PersonalityOrchestratorIT {

    // Services
    private PersonalityOrchestrator personalityOrchestrator;
    private MemoryService memoryService;
    private OpinionService opinionService;
    private DesireService desireService;
    private ValueProfile valueProfile;
    private LLMClient llmClient;
    private LLMResponseParser llmResponseParser;
    private PromptBuilder promptBuilder;
    private EmbeddingService embeddingService;
    private DesireInitativeProducer desireInitativeProducer;
    private ActionRegistry actionRegistry;


    // Configuration
    private static final String QDRANT_HOST = "localhost";
    private static final int QDRANT_PORT = 6333;
    private static final int EMBEDDING_DIMENSION = 1024;
    private static final String MISTRAL_API_KEY = System.getenv("MISTRALAI_API_KEY");


    @BeforeEach
    void setUp() {
        // Skip test if the API key is not provided in the environment
        Assumptions.assumeTrue(MISTRAL_API_KEY != null && !MISTRAL_API_KEY.isEmpty(),
                "MISTRAL_API_KEY environment variable not set. Skipping integration test.");

        // Bottom-up instantiation
        actionRegistry = new ActionRegistry();
        valueProfile = new ValueProfile();
        llmResponseParser = new LLMResponseParser(actionRegistry);
        promptBuilder = new PromptBuilder(actionRegistry, valueProfile);

        DefaultToolCallingManager toolCallingManager = new DefaultToolCallingManager(
                ObservationRegistry.NOOP,
                new DelegatingToolCallbackResolver(List.of()),
                DefaultToolExecutionExceptionProcessor.builder().build()
        );

        // Setup for LLMClient using Spring AI
        MistralAiApi mistralAiApi = new MistralAiApi(MISTRAL_API_KEY);

        MistralAiChatModel mistralAiChatModel = MistralAiChatModel.builder()
                .mistralAiApi(mistralAiApi)
                .defaultOptions(MistralAiChatOptions.builder()
                        .model("mistral-large-latest")   // or "mistral-medium", "mistral-large-latest"
                        .build())
                .toolCallingManager(toolCallingManager)
                .retryTemplate(new RetryTemplate())
                .observationRegistry(ObservationRegistry.NOOP)
                .build();

        ChatClient.Builder chatClientBuilder = ChatClient.builder(mistralAiChatModel);
        llmClient = new LLMClient(chatClientBuilder);

        embeddingService = new EmbeddingService(EMBEDDING_DIMENSION);

        memoryService = new MemoryService(QDRANT_HOST, QDRANT_PORT, embeddingService, llmClient, promptBuilder, llmResponseParser);

        // Initialize Qdrant collections. A real test setup might need a more robust way
        // to handle this, e.g., using Testcontainers for a disposable DB.
        memoryService.initializeCollections();

        desireInitativeProducer = new DesireInitativeProducer();

        opinionService = new OpinionService(llmResponseParser, llmClient, memoryService, promptBuilder, valueProfile);

        desireService = new DesireService(desireInitativeProducer, promptBuilder, valueProfile, memoryService, llmClient, llmResponseParser);

        personalityOrchestrator = new PersonalityOrchestrator(memoryService, opinionService, desireService, valueProfile);
    }

    @Test
    void testFullPersonalityFlow() throws InterruptedException {
        // 1. Define a sample conversation
        String conversation = "Créateur: Je suis si fatigué, la fin de mes dernières vacances d'été approche et j'ai du mal à accepter de devoir tourner cette page, de dire adieu à tant de choses." ;
        String memoryKeyword = "puppy";
        String opinionSubject = "dogs"; // The LLM is likely to generalize "puppy" to "dogs"

        // 2. Call the main orchestrator method
        personalityOrchestrator.processMemory(conversation);

        // 3. Wait a moment to allow for processing and persistence
        // In a real-world scenario, a more robust solution like Awaitility would be better.
        TimeUnit.SECONDS.sleep(1);

        // 4. Verify Memory Creation
        List<SearchResult<MemoryEntry>> memoryResults = memoryService.searchMemories(memoryKeyword, 1);
        assertFalse(memoryResults.isEmpty(), "Search for the memory should return at least one result.");
        MemoryEntry createdMemory = memoryResults.get(0).getEntry();
        assertNotNull(createdMemory, "The created memory entry should not be null.");
        assertNotNull(createdMemory.getContent(), "Memory content should be populated.");
        assertTrue(createdMemory.getContent().contains(memoryKeyword), "Memory content should be related to the conversation.");
        assertNotNull(createdMemory.getEmbedding(), "Memory embedding should be generated.");
        System.out.println("Verified Memory: " + createdMemory);

        // 5. Verify Opinion Creation
        // The subject might be general, so we search for something broader.
        List<SearchResult<OpinionEntry>> opinionResults = memoryService.searchOpinions(opinionSubject, 1);
        assertFalse(opinionResults.isEmpty(), "Search for the opinion should return at least one result.");
        OpinionEntry createdOpinion = opinionResults.get(0).getEntry();
        assertNotNull(createdOpinion, "The created opinion entry should not be null.");
        assertNotNull(createdOpinion.getSubject(), "Opinion subject should be populated.");
        assertNotNull(createdOpinion.getAssociatedDesire(), "The associated desire ID should be populated after the full flow.");
        assertFalse(createdOpinion.getAssociatedDesire().isEmpty(), "Associated desire ID should not be empty.");
        System.out.println("Verified Opinion: " + createdOpinion);
        System.out.println("Associated Desire ID: " + createdOpinion.getAssociatedDesire());

        // 6. Verify Desire Creation
        String desireId = createdOpinion.getAssociatedDesire();
        DesireEntry createdDesire = memoryService.getDesire(desireId);
        assertNotNull(createdDesire, "The created desire entry should not be null.");
        assertNotNull(createdDesire.getLabel(), "Desire label should be populated.");
        assertEquals(createdOpinion.getId(), createdDesire.getOpinionId(), "Desire should be linked to the correct opinion.");
        System.out.println("Verified Desire: " + createdDesire);
    }
}
