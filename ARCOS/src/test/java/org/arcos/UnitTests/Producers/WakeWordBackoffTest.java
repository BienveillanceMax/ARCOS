package org.arcos.UnitTests.Producers;

import org.arcos.Configuration.AudioProperties;
import org.arcos.Configuration.SpeechToTextProperties;
import org.arcos.EventBus.EventQueue;
import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.IO.OuputHandling.StateHandler.AudioCue.AudioCueFeedbackHandler;
import org.arcos.IO.Telemetry.TurnTimeline;
import org.arcos.Producers.WakeWordProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IO-4: only the pure pacing function of the mic supervisor is unit-tested here.
 * The infinite supervised retry loop itself exits only on thread interrupt and is
 * verified by inspection + the manual `pkill -f pw-record` check.
 */
@ExtendWith(MockitoExtension.class)
class WakeWordBackoffTest {

    @Mock private EventQueue eventQueue;
    @Mock private CentralFeedBackHandler centralFeedBackHandler;
    @Mock private AudioCueFeedbackHandler audioCueFeedbackHandler;

    @Test
    void backoffMillis_isExponential_cappedAt30s_andNeverZero() {
        WakeWordProducer p = new WakeWordProducer(eventQueue, centralFeedBackHandler,
                audioCueFeedbackHandler, new AudioProperties(), new SpeechToTextProperties(), new TurnTimeline(), null);
        long b1 = ReflectionTestUtils.invokeMethod(p, "backoffMillis", 1);
        long b2 = ReflectionTestUtils.invokeMethod(p, "backoffMillis", 2);
        long b3 = ReflectionTestUtils.invokeMethod(p, "backoffMillis", 3);
        long bBig = ReflectionTestUtils.invokeMethod(p, "backoffMillis", 99);
        assertThat(b1).isEqualTo(1000L);
        assertThat(b2).isEqualTo(2000L);
        assertThat(b3).isEqualTo(4000L);
        assertThat(bBig).isEqualTo(30_000L);     // capped — never grows unbounded
        assertThat(b1).isGreaterThan(0);          // never busy-spins
    }
}
