package org.arcos.UnitTests.Orchestrator;

import org.arcos.Configuration.AudioProperties;
import org.arcos.EventBus.EventQueue;
import org.arcos.EventBus.Events.Event;
import org.arcos.EventBus.Events.EventType;
import org.arcos.IO.InputHandling.EndpointingPolicyService;
import org.arcos.IO.OuputHandling.PiperEmbeddedTTSModule;
import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.IO.Telemetry.TurnTimeline;
import org.arcos.LLM.Client.ChatOrchestrator;
import org.arcos.LLM.Client.LLMClient;
import org.arcos.LLM.Prompts.PromptBuilder;
import org.arcos.Memory.ConversationContext;
import org.arcos.Memory.ConversationSummaryService;
import org.arcos.Memory.LongTermMemory.service.MemoryService;
import org.arcos.Orchestrator.ConversationRecoveryService;
import org.arcos.Orchestrator.Orchestrator;
import org.arcos.Personality.Desires.DesireService;
import org.arcos.Personality.Initiative.InitiativeService;
import org.arcos.Personality.Mood.MoodService;
import org.arcos.Personality.Mood.MoodStateHolder;
import org.arcos.Personality.Mood.MoodVoiceMapper;
import org.arcos.Personality.PersonalityOrchestrator;
import org.arcos.PlannedAction.ExecutionHistoryService;
import org.arcos.PlannedAction.PlannedActionExecutor;
import org.arcos.PlannedAction.PlannedActionService;
import org.arcos.Producers.WakeWordProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Characterization guards for the Orchestrator event loop: events are dispatched
 * promptly and stop() terminates the loop even when it is parked in a blocking take().
 */
class OrchestratorStartLoopTest {

    private final EventQueue eventQueue = new EventQueue(); // real queue, blocking take()

    @Mock private LLMClient llmClient;
    @Mock private ChatOrchestrator chatOrchestrator;
    @Mock private PromptBuilder promptBuilder;
    @Mock private ConversationContext conversationContext;
    @Mock private MemoryService memoryService;
    @Mock private InitiativeService initiativeService;
    @Mock private PersonalityOrchestrator personalityOrchestrator;
    @Mock private PiperEmbeddedTTSModule piperEmbeddedTTSModule;
    @Mock private MoodService moodService;
    @Mock private MoodStateHolder moodStateHolder;
    @Mock private MoodVoiceMapper moodVoiceMapper;
    @Mock private CentralFeedBackHandler centralFeedBackHandler;
    @Mock private DesireService desireService;
    @Mock private PlannedActionExecutor plannedActionExecutor;
    @Mock private PlannedActionService plannedActionService;
    @Mock private ExecutionHistoryService executionHistoryService;
    @Mock private WakeWordProducer wakeWordProducer;
    @Mock private AudioProperties audioProperties;
    @Mock private ConversationSummaryService conversationSummaryService;

    private Orchestrator orchestrator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orchestrator = new Orchestrator(centralFeedBackHandler,
                personalityOrchestrator,
                eventQueue,
                llmClient,
                chatOrchestrator,
                promptBuilder,
                conversationContext,
                memoryService,
                initiativeService,
                desireService,
                moodService,
                moodStateHolder,
                moodVoiceMapper,
                plannedActionExecutor,
                plannedActionService,
                executionHistoryService,
                wakeWordProducer,
                audioProperties,
                conversationSummaryService,
                new TurnTimeline(),
                new EndpointingPolicyService(audioProperties),
                new ConversationRecoveryService(),
                null, null, null
        );
        ReflectionTestUtils.setField(orchestrator, "ttsHandler", piperEmbeddedTTSModule);
    }

    @Test
    void start_dispatchesEnqueuedEvent_withoutPollLatency() throws Exception {
        when(promptBuilder.buildSchedulerAlertPrompt(any())).thenReturn(new Prompt(""));
        when(llmClient.generateToollessResponse(any(Prompt.class))).thenReturn("rappel");
        Thread loop = new Thread(orchestrator::start, "test-orch");
        loop.start();
        eventQueue.offer(new Event<>(EventType.CALENDAR_EVENT_SCHEDULER, "evt", "test"));
        verify(piperEmbeddedTTSModule, timeout(200)).speakAsync("rappel");
        orchestrator.stop();
        loop.interrupt();
        loop.join(1000);
    }

    @Test
    void stop_interruptsBlockingTake_andLoopExits() throws Exception {
        Thread loop = new Thread(orchestrator::start, "test-orch");
        loop.start();
        Thread.sleep(50); // let it reach the blocking take()
        orchestrator.stop();
        loop.join(1000);
        assertFalse(loop.isAlive(), "consumer thread must exit on stop()");
    }
}
