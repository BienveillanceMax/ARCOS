package org.arcos.UnitTests.IO.InputHandling;

import org.arcos.Configuration.AudioProperties;
import org.arcos.IO.InputHandling.CaptureConfig;
import org.arcos.IO.InputHandling.MicrophoneSource;
import org.arcos.IO.InputHandling.STT.SttCall;
import org.arcos.IO.InputHandling.STT.SttGate;
import org.arcos.IO.InputHandling.STT.SttResult;
import org.arcos.IO.InputHandling.UtteranceCaptureService;
import org.arcos.IO.Telemetry.TurnTimeline;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Teste la boucle de capture partagée sur un micro scripté (trames S=parole / .=silence).
 *
 * Cadence : chaque read() dort {@value #FRAME_SLEEP_MS}ms — le temps réel avance avec les
 * trames, ce qui rend les seuils de silence en wall-clock déterministes avec de la marge.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UtteranceCaptureServiceTest {

    private static final int FRAME_BYTES = 1600; // 50ms @16kHz
    private static final long FRAME_SLEEP_MS = 15;
    private static final int THRESHOLD = 75;

    @Mock private SttGate sttGate;

    private UtteranceCaptureService service;

    @AfterEach
    void tearDown() {
        if (service != null) service.close();
    }

    /** Micro scripté : sert la séquence de trames puis du silence, une trame par read(). */
    private static final class ScriptedMic implements MicrophoneSource {
        private final String script; // ex. "SSS...": S = parole, . = silence
        private int index = 0;

        ScriptedMic(String script) {
            this.script = script;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            try {
                Thread.sleep(FRAME_SLEEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return -1;
            }
            char kind = index < script.length() ? script.charAt(index) : '.';
            index++;
            if (kind == 'S') {
                // Onde carrée d'amplitude 3000 : RMS largement au-dessus du seuil 75
                for (int i = 0; i < length; i += 2) {
                    short v = (short) (((i / 2) % 2 == 0) ? 3000 : -3000);
                    buffer[offset + i] = (byte) (v & 0xFF);
                    buffer[offset + i + 1] = (byte) ((v >> 8) & 0xFF);
                }
            } else {
                Arrays.fill(buffer, offset, offset + length, (byte) 0);
            }
            return length;
        }

        @Override public void close() { }
        @Override public boolean isAvailable() { return true; }
        @Override public String describe() { return "scripted"; }
        @Override public int getSampleRate() { return 16000; }
        @Override public int recommendedSilenceThreshold() { return THRESHOLD; }
    }

    private CaptureConfig config(long silenceMs) {
        return new CaptureConfig("", 2000, silenceMs, 10_000);
    }

    private SttCall completedCall(String result) {
        return SttCall.completed(SttResult.transcript(result));
    }

    @Test
    void capture_WhenSpeechThenSilence_ShouldReturnSpeculativeTranscript() {
        // Given : 4 trames de parole puis silence ; fin d'énoncé à ~90ms de silence (6 trames)
        service = new UtteranceCaptureService(new ScriptedMic("SSSS"), sttGate, THRESHOLD, new TurnTimeline());
        when(sttGate.hasMinimumAudio()).thenReturn(true);
        when(sttGate.startTranscription()).thenReturn(completedCall("bonjour arcos"));

        // When
        SttResult result = service.capture(config(6 * FRAME_SLEEP_MS));

        // Then : le résultat vient de la spéculation, sans appel synchrone supplémentaire
        assertThat(result.hasTranscript()).isTrue();
        assertThat(result.text()).isEqualTo("bonjour arcos");
        verify(sttGate, times(1)).startTranscription();
        verify(sttGate, never()).getTranscription();
        verify(sttGate).reset();
    }

    @Test
    void capture_ShouldDebounceSpeculation_NotLaunchOnFirstSilentFrame() {
        // Given : une seule trame de silence entre deux paroles — sous le débounce (2 trames)
        service = new UtteranceCaptureService(new ScriptedMic("SSS.SSS"), sttGate, THRESHOLD, new TurnTimeline());
        when(sttGate.hasMinimumAudio()).thenReturn(true);
        when(sttGate.startTranscription()).thenReturn(completedCall("phrase complète"));

        // When
        SttResult result = service.capture(config(6 * FRAME_SLEEP_MS));

        // Then : la micro-pause n'a PAS déclenché de spéculation ; une seule au silence final
        assertThat(result.text()).isEqualTo("phrase complète");
        verify(sttGate, times(1)).startTranscription();
    }

    @Test
    void capture_WhenSpeechResumes_ShouldCancelOrphanedSpeculationOnce() {
        // Given : pause de 3 trames (≥ débounce → spéculation lancée) puis reprise de parole
        SttCall orphan = mock(SttCall.class);
        when(orphan.await()).thenReturn(SttResult.transcript("partiel"));
        SttCall finalCall = completedCall("phrase finale");
        service = new UtteranceCaptureService(new ScriptedMic("SSS...SSS"), sttGate, THRESHOLD, new TurnTimeline());
        when(sttGate.hasMinimumAudio()).thenReturn(true);
        when(sttGate.startTranscription()).thenReturn(orphan, finalCall);

        // When
        SttResult result = service.capture(config(6 * FRAME_SLEEP_MS));

        // Then : l'orphelin est annulé exactement une fois, le résultat vient du second appel
        assertThat(result.text()).isEqualTo("phrase finale");
        verify(orphan, times(1)).cancel();
        verify(sttGate, times(2)).startTranscription();
        verify(sttGate, never()).getTranscription();
    }

    @Test
    void capture_WhenNoSpeechInWindow_ShouldReturnNoSpeechWithoutSttCall() {
        // Given : que du silence, fenêtre initiale courte
        service = new UtteranceCaptureService(new ScriptedMic("...."), sttGate, THRESHOLD, new TurnTimeline());

        // When
        SttResult result = service.capture(new CaptureConfig("", 5 * FRAME_SLEEP_MS, 60, 10_000));

        // Then
        assertThat(result.status()).isEqualTo(SttResult.Status.NO_SPEECH);
        verify(sttGate, never()).startTranscription();
        verify(sttGate, never()).getTranscription();
    }

    @Test
    void resolveSilenceThreshold_ShouldPreferExplicitConfigOverMicRecommendation() {
        // Given
        AudioProperties auto = new AudioProperties();
        auto.setSilenceThreshold(-1);
        AudioProperties forced = new AudioProperties();
        forced.setSilenceThreshold(400);
        MicrophoneSource mic = new ScriptedMic("");

        // When / Then
        assertThat(UtteranceCaptureService.resolveSilenceThreshold(auto, mic)).isEqualTo(THRESHOLD);
        assertThat(UtteranceCaptureService.resolveSilenceThreshold(forced, mic)).isEqualTo(400);
    }
}
