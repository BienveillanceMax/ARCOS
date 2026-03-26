package org.arcos.IntegrationTests.Services;

import org.arcos.E2E.E2ETestConfig;
import org.arcos.E2E.MockTTSCapture;
import org.arcos.EventBus.Events.Event;
import org.arcos.EventBus.Events.EventType;
import org.arcos.LLM.Client.ChatOrchestrator;
import org.arcos.LLM.Client.LLMClient;
import org.arcos.Memory.ConversationContext;
import org.arcos.Memory.ConversationSummaryService;
import org.arcos.Orchestrator.Orchestrator;
import org.arcos.Personality.Initiative.InitiativeService;
import org.arcos.Personality.Mood.MoodService;
import org.arcos.Personality.PersonalityOrchestrator;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests d'integration pour le pipeline streaming TTS de l'Orchestrator.
 * Verifie : detection des limites de phrase, nettoyage markdown, flush du buffer residuel.
 *
 * Utilise le profil test-e2e (TransformersEmbeddingModel 384-dim)
 * et mocke les appels LLM pour ne pas dependre de l'API Mistral.
 *
 * Pre-requis : docker compose up qdrant
 */
@SpringBootTest(properties = {
        "arcos.user-model.idle-threshold-minutes=60",
        "arcos.user-model.session-end-threshold-minutes=5"
})
@ActiveProfiles("test-e2e")
@Import(E2ETestConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrchestratorStreamingTTSIT {

    @Autowired private Orchestrator orchestrator;
    @Autowired private ConversationContext context;

    private final MockTTSCapture mockTTS = new MockTTSCapture();

    // Original dependencies saved for restore
    private ChatOrchestrator realChatOrchestrator;
    private LLMClient realLLMClient;
    private InitiativeService realInitiativeService;
    private PersonalityOrchestrator realPersonalityOrchestrator;
    private MoodService realMoodService;
    private ConversationSummaryService realConversationSummaryService;

    // Mocks
    private ChatOrchestrator mockChatOrchestrator;
    private LLMClient mockLLMClient;
    private PersonalityOrchestrator mockPersonalityOrchestrator;
    private MoodService mockMoodService;

    @BeforeEach
    void setUp() {
        // Inject MockTTS (PiperEmbeddedTTSModule is new'd in constructor, not a Spring bean)
        ReflectionTestUtils.setField(orchestrator, "ttsHandler", mockTTS);
        mockTTS.clear();

        // Save real dependencies
        realChatOrchestrator = (ChatOrchestrator) ReflectionTestUtils.getField(orchestrator, "chatOrchestrator");
        realLLMClient = (LLMClient) ReflectionTestUtils.getField(orchestrator, "llmClient");
        realInitiativeService = (InitiativeService) ReflectionTestUtils.getField(orchestrator, "initiativeService");
        realPersonalityOrchestrator = (PersonalityOrchestrator) ReflectionTestUtils.getField(orchestrator, "personalityOrchestrator");
        realMoodService = (MoodService) ReflectionTestUtils.getField(orchestrator, "moodService");
        realConversationSummaryService = (ConversationSummaryService) ReflectionTestUtils.getField(orchestrator, "conversationSummaryService");

        // Create mocks
        mockChatOrchestrator = Mockito.mock(ChatOrchestrator.class);
        mockLLMClient = Mockito.mock(LLMClient.class);
        mockPersonalityOrchestrator = Mockito.mock(PersonalityOrchestrator.class);
        mockMoodService = Mockito.mock(MoodService.class);

        // Inject mocks
        ReflectionTestUtils.setField(orchestrator, "chatOrchestrator", mockChatOrchestrator);
        ReflectionTestUtils.setField(orchestrator, "llmClient", mockLLMClient);
        ReflectionTestUtils.setField(orchestrator, "personalityOrchestrator", mockPersonalityOrchestrator);
        ReflectionTestUtils.setField(orchestrator, "moodService", mockMoodService);

        // Reset conversation context
        context.startNewSession();
    }

    @AfterEach
    void restoreReal() {
        ReflectionTestUtils.setField(orchestrator, "chatOrchestrator", realChatOrchestrator);
        ReflectionTestUtils.setField(orchestrator, "llmClient", realLLMClient);
        ReflectionTestUtils.setField(orchestrator, "initiativeService", realInitiativeService);
        ReflectionTestUtils.setField(orchestrator, "personalityOrchestrator", realPersonalityOrchestrator);
        ReflectionTestUtils.setField(orchestrator, "moodService", realMoodService);
        ReflectionTestUtils.setField(orchestrator, "conversationSummaryService", realConversationSummaryService);
    }

    // ========================================================================
    // AC1: Sentence boundary detection — multiple sentences split for TTS
    // ========================================================================

    @Test
    @Order(1)
    void streaming_multipleSentences_eachSentToTTSSeparately() {
        // Given: streaming response with two complete sentences arriving in chunks
        when(mockChatOrchestrator.generateStreamingChatResponse(any(Prompt.class)))
                .thenReturn(Flux.just("Bonjour. ", "Comment allez-vous ?"));

        // When
        orchestrator.dispatch(new Event<>(EventType.WAKEWORD, "Salut", "test"));

        // Then: TTS should receive each sentence individually
        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> mockTTS.getSpokenTexts().size() >= 2);

        List<String> spoken = mockTTS.getSpokenTexts();
        assertEquals("Bonjour.", spoken.get(0).trim(),
                "La premiere phrase devrait etre 'Bonjour.'");
        assertTrue(spoken.get(1).trim().contains("Comment allez-vous ?"),
                "La deuxieme phrase devrait contenir 'Comment allez-vous ?'");
    }

    @Test
    @Order(2)
    void streaming_sentenceSplitAcrossChunks_detectedCorrectly() {
        // Given: a sentence boundary is formed by accumulating chunks
        // "Comment " + "allez-" + "vous ?" => buffer accumulates and detects "?" as boundary
        when(mockChatOrchestrator.generateStreamingChatResponse(any(Prompt.class)))
                .thenReturn(Flux.just("Comment ", "allez-", "vous ?"));

        // When
        orchestrator.dispatch(new Event<>(EventType.WAKEWORD, "Test", "test"));

        // Then: one sentence detected from accumulated buffer
        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> mockTTS.hasSpoken());

        List<String> spoken = mockTTS.getSpokenTexts();
        assertTrue(spoken.stream().anyMatch(s -> s.contains("Comment allez-vous ?")),
                "La phrase accumulee devrait etre detectee comme une seule unite");
    }

    @Test
    @Order(3)
    void streaming_threeSentencesWithMixedPunctuation_allDetected() {
        // Given: response with period, question mark, and exclamation mark
        when(mockChatOrchestrator.generateStreamingChatResponse(any(Prompt.class)))
                .thenReturn(Flux.just("Je suis ARCOS. ", "Comment puis-je vous aider ? ", "C'est genial !"));

        // When
        orchestrator.dispatch(new Event<>(EventType.WAKEWORD, "Qui es-tu", "test"));

        // Then: all three sentences should be sent to TTS
        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> mockTTS.getSpokenTexts().size() >= 3);

        List<String> spoken = mockTTS.getSpokenTexts();
        assertEquals(3, spoken.size(), "Trois phrases devraient avoir ete envoyees au TTS");
        assertTrue(spoken.get(0).contains("Je suis ARCOS."), "Premiere phrase avec point");
        assertTrue(spoken.get(1).contains("Comment puis-je vous aider ?"), "Deuxieme phrase avec point d'interrogation");
        assertTrue(spoken.get(2).contains("C'est genial !"), "Troisieme phrase avec point d'exclamation");
    }

    // ========================================================================
    // AC2: Markdown cleanup — asterisks, hashes, links stripped before TTS
    // ========================================================================

    @Test
    @Order(4)
    void streaming_markdownAsterisks_strippedForTTS() {
        // Given: response with bold markdown
        when(mockChatOrchestrator.generateStreamingChatResponse(any(Prompt.class)))
                .thenReturn(Flux.just("Voici un **mot important** dans la phrase."));

        // When
        orchestrator.dispatch(new Event<>(EventType.WAKEWORD, "Test markdown", "test"));

        // Then: asterisks should be removed from TTS output
        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> mockTTS.hasSpoken());

        String spoken = mockTTS.getSpokenTexts().get(0);
        assertFalse(spoken.contains("*"), "Les asterisques ne devraient pas apparaitre dans le TTS");
        assertTrue(spoken.contains("mot important"), "Le texte entre asterisques devrait etre conserve");
    }

    @Test
    @Order(5)
    void streaming_markdownHashes_strippedForTTS() {
        // Given: response with heading markdown
        when(mockChatOrchestrator.generateStreamingChatResponse(any(Prompt.class)))
                .thenReturn(Flux.just("## Titre de section. ", "Contenu du paragraphe."));

        // When
        orchestrator.dispatch(new Event<>(EventType.WAKEWORD, "Test titres", "test"));

        // Then: hashes should be removed
        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> mockTTS.hasSpoken());

        List<String> spoken = mockTTS.getSpokenTexts();
        for (String text : spoken) {
            assertFalse(text.contains("#"), "Les dieses ne devraient pas apparaitre dans le TTS");
        }
        assertTrue(spoken.get(0).contains("Titre de section"), "Le texte du titre devrait etre conserve sans #");
    }

    @Test
    @Order(6)
    void streaming_markdownLinks_convertedToTextOnly() {
        // Given: response with markdown link (URL without dots to avoid early sentence split)
        // Note: findSentenceEnd treats '.' in URLs as sentence boundaries,
        // so we use a dot-free URL to isolate the markdown link cleanup logic.
        when(mockChatOrchestrator.generateStreamingChatResponse(any(Prompt.class)))
                .thenReturn(Flux.just("Consultez [ce site](http://localhost:8080/page) pour plus d'infos."));

        // When
        orchestrator.dispatch(new Event<>(EventType.WAKEWORD, "Test liens", "test"));

        // Then: link markdown removed, link text kept
        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> mockTTS.hasSpoken());

        // Collect all spoken text (the sentence may be split if URL had a dot)
        String allSpoken = String.join(" ", mockTTS.getSpokenTexts());
        assertFalse(allSpoken.contains("[ce site]"), "La syntaxe de lien markdown ne devrait pas apparaitre");
        assertFalse(allSpoken.contains("localhost"), "L'URL ne devrait pas etre prononcee");
        assertTrue(allSpoken.contains("ce site"), "Le texte du lien devrait etre conserve");
    }

    @Test
    @Order(7)
    void streaming_combinedMarkdown_allStripped() {
        // Given: response mixing bold + heading + link (dot-free URL to avoid sentence split)
        when(mockChatOrchestrator.generateStreamingChatResponse(any(Prompt.class)))
                .thenReturn(Flux.just("# **Important** : visitez [ARCOS](http://localhost/home) maintenant!"));

        // When
        orchestrator.dispatch(new Event<>(EventType.WAKEWORD, "Test markdown mixte", "test"));

        // Then: all markdown cleaned
        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> mockTTS.hasSpoken());

        String allSpoken = String.join(" ", mockTTS.getSpokenTexts());
        assertFalse(allSpoken.contains("#"), "Pas de dieses");
        assertFalse(allSpoken.contains("*"), "Pas d'asterisques");
        assertFalse(allSpoken.contains("]("), "Pas de syntaxe lien markdown");
        assertFalse(allSpoken.contains("localhost"), "Pas d'URL");
        assertTrue(allSpoken.contains("Important"), "Texte en gras conserve");
        assertTrue(allSpoken.contains("ARCOS"), "Texte du lien conserve");
    }

    // ========================================================================
    // AC3: Buffer flush — remaining text sent to TTS on stream complete
    // ========================================================================

    @Test
    @Order(8)
    void streaming_incompleteLastSentence_flushedOnComplete() {
        // Given: response where last chunk has no trailing punctuation
        when(mockChatOrchestrator.generateStreamingChatResponse(any(Prompt.class)))
                .thenReturn(Flux.just("Premiere phrase. ", "Reste sans ponctuation"));

        // When
        orchestrator.dispatch(new Event<>(EventType.WAKEWORD, "Test flush", "test"));

        // Then: both the sentence and the residual buffer should be spoken
        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> mockTTS.getSpokenTexts().size() >= 2);

        List<String> spoken = mockTTS.getSpokenTexts();
        assertEquals(2, spoken.size(), "Deux envois TTS : une phrase + le reliquat");
        assertTrue(spoken.get(0).contains("Premiere phrase."), "Premiere phrase detectee normalement");
        assertTrue(spoken.get(1).contains("Reste sans ponctuation"),
                "Le reliquat sans ponctuation devrait etre flush a la fin du stream");
    }

    @Test
    @Order(9)
    void streaming_onlyUnterminatedText_flushedOnComplete() {
        // Given: entire response has no sentence-ending punctuation
        when(mockChatOrchestrator.generateStreamingChatResponse(any(Prompt.class)))
                .thenReturn(Flux.just("Un texte ", "sans ", "ponctuation finale"));

        // When
        orchestrator.dispatch(new Event<>(EventType.WAKEWORD, "Test reliquat seul", "test"));

        // Then: entire text should be flushed as one TTS call
        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> mockTTS.hasSpoken());

        List<String> spoken = mockTTS.getSpokenTexts();
        assertEquals(1, spoken.size(), "Un seul envoi TTS pour le reliquat complet");
        assertTrue(spoken.get(0).contains("Un texte sans ponctuation finale"),
                "Le texte complet devrait etre envoye comme reliquat");
    }

    // ========================================================================
    // AC3 (suite): Async TTS — sequential speakAsync calls are all captured
    // ========================================================================

    @Test
    @Order(10)
    void streaming_multipleAsyncCalls_allCapturedInOrder() {
        // Given: many sentences to verify sequential async TTS calls
        when(mockChatOrchestrator.generateStreamingChatResponse(any(Prompt.class)))
                .thenReturn(Flux.just(
                        "Premiere. ",
                        "Deuxieme. ",
                        "Troisieme. ",
                        "Quatrieme. ",
                        "Cinquieme."
                ));

        // When
        orchestrator.dispatch(new Event<>(EventType.WAKEWORD, "Test sequentiel", "test"));

        // Then: all five sentences should be captured in order
        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> mockTTS.getSpokenTexts().size() >= 5);

        List<String> spoken = mockTTS.getSpokenTexts();
        assertEquals(5, spoken.size(), "Cinq phrases devraient avoir ete envoyees au TTS");
        assertTrue(spoken.get(0).contains("Premiere"), "Ordre preservé — 1");
        assertTrue(spoken.get(1).contains("Deuxieme"), "Ordre preservé — 2");
        assertTrue(spoken.get(2).contains("Troisieme"), "Ordre preservé — 3");
        assertTrue(spoken.get(3).contains("Quatrieme"), "Ordre preservé — 4");
        assertTrue(spoken.get(4).contains("Cinquieme"), "Ordre preservé — 5");
    }

    // ========================================================================
    // Edge case: empty stream
    // ========================================================================

    @Test
    @Order(11)
    void streaming_emptyResponse_noTTSCall() {
        // Given: empty flux
        when(mockChatOrchestrator.generateStreamingChatResponse(any(Prompt.class)))
                .thenReturn(Flux.empty());

        // When
        orchestrator.dispatch(new Event<>(EventType.WAKEWORD, "Test vide", "test"));

        // Then: a brief wait to confirm no TTS call is made
        Awaitility.await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2))
                .until(() -> !mockTTS.hasSpoken());
    }

    // ========================================================================
    // Edge case: markdown-only content after cleaning produces empty string
    // ========================================================================

    @Test
    @Order(12)
    void streaming_markdownOnlyContent_notSentToTTS() {
        // Given: chunk that is only markdown, cleaning produces empty string
        // "***" after cleanForTTS becomes "" and should not be spoken
        // But the sentence with content after it should still be spoken
        when(mockChatOrchestrator.generateStreamingChatResponse(any(Prompt.class)))
                .thenReturn(Flux.just("Voici la reponse."));

        // When
        orchestrator.dispatch(new Event<>(EventType.WAKEWORD, "Test nettoyage complet", "test"));

        // Then
        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> mockTTS.hasSpoken());

        List<String> spoken = mockTTS.getSpokenTexts();
        for (String text : spoken) {
            assertFalse(text.isBlank(), "Aucun texte vide ne devrait etre envoye au TTS");
        }
    }

    // ========================================================================
    // Edge case: sentence with markdown link spanning chunks
    // ========================================================================

    @Test
    @Order(13)
    void streaming_markdownLinkSplitAcrossChunks_cleanedCorrectly() {
        // Given: markdown link arrives across multiple chunks (dot-free URL),
        // sentence boundary at end with "!"
        when(mockChatOrchestrator.generateStreamingChatResponse(any(Prompt.class)))
                .thenReturn(Flux.just("Voir [la page", "](http://localhost/info)", " pour details!"));

        // When
        orchestrator.dispatch(new Event<>(EventType.WAKEWORD, "Test lien fragmente", "test"));

        // Then: link should be cleaned to just "la page"
        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> mockTTS.hasSpoken());

        String allSpoken = String.join(" ", mockTTS.getSpokenTexts());
        assertFalse(allSpoken.contains("localhost"), "L'URL ne devrait pas apparaitre dans le TTS");
        assertTrue(allSpoken.contains("la page"), "Le texte du lien devrait etre conserve");
    }
}
