package org.arcos.UnitTests.IO.Telemetry;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.arcos.IO.Telemetry.TurnTimeline;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TurnTimelineTest {

    private TurnTimeline timeline;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        timeline = new TurnTimeline();
        Logger logger = (Logger) LoggerFactory.getLogger(TurnTimeline.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(TurnTimeline.class);
        logger.detachAppender(logAppender);
    }

    private List<String> timelineLines() {
        return logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.startsWith("VOICE_TIMELINE eou_ms="))
                .toList();
    }

    @Test
    void completeTurn_ShouldLogSingleTimelineLineWithNonNegativeDeltas() {
        // Given : un tour complet, marques dans l'ordre du pipeline
        timeline.beginTurn();
        timeline.markSpeechEnd(System.currentTimeMillis());

        // When
        timeline.markSttDone();
        timeline.markPromptBuilt();
        timeline.markFirstToken();
        timeline.markFirstAudible();

        // Then : une seule ligne, aucun delta manquant (-1)
        List<String> lines = timelineLines();
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).doesNotContain("=-1");
    }

    @Test
    void marksWithoutBeginTurn_ShouldLogNothing() {
        // Given : pas de tour actif (ex. TTS d'initiative hors conversation)

        // When
        timeline.markSttDone();
        timeline.markFirstToken();
        timeline.markFirstAudible();

        // Then
        assertThat(timelineLines()).isEmpty();
    }

    @Test
    void repeatedMarks_ShouldKeepFirstOccurrenceAndLogOnce() {
        // Given : les phrases suivantes d'une même réponse re-déclenchent les marques
        timeline.beginTurn();
        timeline.markSpeechEnd(System.currentTimeMillis());
        timeline.markSttDone();
        timeline.markPromptBuilt();
        timeline.markFirstToken();

        // When : chunks et lectures multiples
        timeline.markFirstToken();
        timeline.markFirstAudible();
        timeline.markFirstAudible();

        // Then : une seule ligne loguée
        assertThat(timelineLines()).hasSize(1);
    }

    @Test
    void abandonedTurn_ShouldNotLogTimelineLine() {
        // Given : un tour commencé mais jamais complété (STT vide, erreur LLM…)
        timeline.beginTurn();
        timeline.markSpeechEnd(System.currentTimeMillis());

        // When : un nouveau tour démarre puis se complète
        timeline.beginTurn();
        timeline.markSpeechEnd(System.currentTimeMillis());
        timeline.markSttDone();
        timeline.markPromptBuilt();
        timeline.markFirstToken();
        timeline.markFirstAudible();

        // Then : une seule ligne (celle du second tour), le tour abandonné n'en produit pas
        assertThat(timelineLines()).hasSize(1);
    }
}
