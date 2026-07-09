package org.arcos.UnitTests.IO.InputHandling;

import org.arcos.IO.InputHandling.CaptureConfig;
import org.arcos.IO.InputHandling.MicrophoneSource;
import org.arcos.IO.InputHandling.SpeechDetector;
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

    @Mock private SttGate sttGate;

    private UtteranceCaptureService service;
    private final FakeSpeechDetector detector = new FakeSpeechDetector();

    @AfterEach
    void tearDown() {
        if (service != null) service.close();
    }

    /**
     * Détecteur factice : une trame est « parole » si elle contient un échantillon non nul —
     * exactement l'inverse des trames de silence (zéros) du {@link ScriptedMic}. Garde ONNX
     * hors de la boucle testée (le vrai Silero classerait les ondes carrées comme non-parole).
     * Compte les appels à {@link #reset()} pour vérifier le contrat de la capture.
     */
    private static final class FakeSpeechDetector implements SpeechDetector {
        int resetCount = 0;

        @Override
        public boolean isSpeech(byte[] frame) {
            for (byte b : frame) {
                if (b != 0) return true;
            }
            return false;
        }

        @Override
        public void reset() {
            resetCount++;
        }
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
                // Onde carrée d'amplitude 3000 : échantillons non nuls → parole pour le détecteur factice
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
    }

    /**
     * Micro à lectures PARTIELLES (comme pw-record réel : ~1/3 des read() rendent moins que
     * demandé). Sert {@code speechFrames} trames de parole (octets non nuls) puis du silence pur
     * (zéros), en rendant au plus {@link #PARTIAL} octets par read(). Reproduit la condition qui
     * laissait des octets périmés dans le buffer réutilisé → parole fantôme perpétuelle.
     */
    private static final class PartialReadMic implements MicrophoneSource {
        // 896 o comme pw-record réel : NE divise PAS 1600 → les sous-lectures se désalignent de la
        // frontière de trame, si bien qu'une queue de buffer périmée survit d'une trame à l'autre
        // (c'est exactement ce qui gardait le VAD en "parole"). Un diviseur (800) masquerait le bug.
        private static final int PARTIAL = 896;
        private final long speechBytes;
        private long served = 0;

        /** Sert {@code speechFrames} trames de parole, puis du silence pur indéfiniment. */
        PartialReadMic(int speechFrames) {
            this.speechBytes = (long) speechFrames * FRAME_BYTES;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            try {
                Thread.sleep(FRAME_SLEEP_MS / 2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return -1;
            }
            int n = Math.min(length, PARTIAL);
            for (int i = 0; i < n; i++) {
                boolean speech = (served + i) < speechBytes; // frontière parole/silence au sein du chunk
                buffer[offset + i] = speech ? (byte) ((i % 2 == 0) ? 0x30 : 0x0C) : 0;
            }
            served += n;
            return n;
        }

        @Override public void close() { }
        @Override public boolean isAvailable() { return true; }
        @Override public String describe() { return "partial-read"; }
        @Override public int getSampleRate() { return 16000; }
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
        service = new UtteranceCaptureService(new ScriptedMic("SSSS"), sttGate, detector, new TurnTimeline());
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
        service = new UtteranceCaptureService(new ScriptedMic("SSS.SSS"), sttGate, detector, new TurnTimeline());
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
        service = new UtteranceCaptureService(new ScriptedMic("SSS...SSS"), sttGate, detector, new TurnTimeline());
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
        service = new UtteranceCaptureService(new ScriptedMic("...."), sttGate, detector, new TurnTimeline());

        // When
        SttResult result = service.capture(new CaptureConfig("", 5 * FRAME_SLEEP_MS, 60, 10_000));

        // Then
        assertThat(result.status()).isEqualTo(SttResult.Status.NO_SPEECH);
        verify(sttGate, never()).startTranscription();
        verify(sttGate, never()).getTranscription();
    }

    @Test
    void capture_ShouldResetSpeechDetector_BeforeEachUtterance() {
        // Given : le détecteur porte un état inter-énoncé (état LSTM Silero) qui DOIT être
        // remis à zéro à chaque capture, sinon l'énoncé précédent fuit dans le suivant.
        service = new UtteranceCaptureService(new ScriptedMic("SSSS"), sttGate, detector, new TurnTimeline());
        when(sttGate.hasMinimumAudio()).thenReturn(true);
        when(sttGate.startTranscription()).thenReturn(completedCall("bonjour"));

        // When
        service.capture(config(6 * FRAME_SLEEP_MS));

        // Then : reset() appelé exactement une fois pour cette capture
        assertThat(detector.resetCount).isEqualTo(1);
    }

    @Test
    void capture_WithPartialReads_ShouldReassembleFullFrames_AndNotHang() {
        // Given : micro à lectures partielles (896 o, comme pw-record). Ici le détecteur factice
        // suffit à prouver le RÉ-ASSEMBLAGE (parole puis silence → fin d'énoncé, pas de plafond).
        // La preuve que les octets PÉRIMÉS n'accrochent pas le VAD réel est un test d'intégration
        // avec le vrai Silero (voir SileroSpeechDetectorTest#capture_WithPartialReads_*).
        long maxRecordingMs = 3000;
        CaptureConfig cfg = new CaptureConfig("", 2000, 6 * FRAME_SLEEP_MS, maxRecordingMs);
        service = new UtteranceCaptureService(new PartialReadMic(4), sttGate, detector, new TurnTimeline());
        when(sttGate.hasMinimumAudio()).thenReturn(true);
        when(sttGate.startTranscription()).thenReturn(completedCall("bonjour arcos"));

        // When
        long start = System.currentTimeMillis();
        SttResult result = service.capture(cfg);
        long elapsed = System.currentTimeMillis() - start;

        // Then : l'énoncé se clôt sur le silence, bien avant le plafond maxRecordingMs
        assertThat(result.hasTranscript()).isTrue();
        assertThat(result.text()).isEqualTo("bonjour arcos");
        assertThat(elapsed).as("fin sur silence, pas sur le plafond d'enregistrement").isLessThan(maxRecordingMs);
    }
}
