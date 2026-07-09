package org.arcos.UnitTests.IO.InputHandling;

import org.arcos.Configuration.AudioProperties;
import org.arcos.IO.InputHandling.CaptureConfig;
import org.arcos.IO.InputHandling.MicrophoneSource;
import org.arcos.IO.InputHandling.SileroSpeechDetector;
import org.arcos.IO.InputHandling.STT.SttCall;
import org.arcos.IO.InputHandling.STT.SttGate;
import org.arcos.IO.InputHandling.STT.SttResult;
import org.arcos.IO.InputHandling.UtteranceCaptureService;
import org.arcos.IO.Telemetry.TurnTimeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Valide le VAD Silero sur de vraies fixtures FR : la parole est classée « parole », le silence
 * « silence », et {@link SileroSpeechDetector#reset()} garantit qu'aucun état ne fuit d'un énoncé
 * au suivant (deux passes identiques → verdicts identiques).
 *
 * Gated : si le modèle ONNX est absent du classpath, les tests sont ignorés (assumeTrue) plutôt
 * qu'en échec — le modèle est une ressource lourde et le CI peut ne pas la porter.
 */
class SileroSpeechDetectorTest {

    private static final int FRAME_BYTES = 1600; // 50 ms @ 16 kHz, comme la boucle de capture
    private static final Path FIXTURE =
            Path.of("src/test/resources/fixtures/speech-fr/utt_05s_dentiste.wav");

    private SileroSpeechDetector detector;

    @BeforeEach
    void setUp() {
        AudioProperties props = new AudioProperties();
        assumeTrue(modelPresent(props.getVad().getModelResource()),
                "Modèle Silero absent du classpath — test ignoré");
        detector = new SileroSpeechDetector(props);
        detector.initialize();
        assumeTrue(detector.isAvailable(), "Silero non initialisé — test ignoré");
    }

    @Test
    void isSpeech_ShouldDetectSpeechInFrenchUtterance_AndSilenceInZeros() throws IOException {
        // Given : fixture FR (parole) + une seconde de silence pur
        byte[] speech = readPcm(FIXTURE);
        byte[] silence = new byte[16000 * 2]; // 1 s de zéros

        // When
        double speechRatio = speechFrameRatio(speech);
        detector.reset();
        double silenceRatio = speechFrameRatio(silence);

        // Then : nette séparation parole/silence
        assertThat(speechRatio).as("proportion de trames parole sur de la vraie parole").isGreaterThan(0.5);
        assertThat(silenceRatio).as("proportion de trames parole sur du silence pur").isLessThan(0.05);
    }

    @Test
    void reset_ShouldPreventStateLeakBetweenUtterances() throws IOException {
        // Given
        byte[] speech = readPcm(FIXTURE);

        // When : deux passes séparées par un reset()
        detector.reset();
        boolean[] pass1 = frameVerdicts(speech);
        detector.reset();
        boolean[] pass2 = frameVerdicts(speech);

        // Then : reset() rend l'inférence déterministe — mêmes verdicts trame à trame
        assertThat(pass2).isEqualTo(pass1);
    }

    /**
     * Régression du bug « n'arrête jamais d'écouter en environnement bruyant » : pw-record fait
     * des lectures PARTIELLES (896 o pour 1600 demandés). Avant le ré-assemblage des trames, la
     * queue périmée du buffer réutilisé gardait un fragment de forme d'onde de parole désaligné,
     * et le vrai Silero (contrairement à un détecteur factice) s'y accrochait → l'énoncé courait
     * jusqu'au plafond maxRecordingMs. Ce test exerce le VRAI modèle sur de la VRAIE parole FR à
     * travers la boucle de capture de production, avec un micro à lectures partielles.
     */
    @Test
    void capture_WithPartialReads_RealSpeechThenSilence_ShouldEndOnSilence() throws IOException {
        // Given : vraie parole FR (896 o/read) puis silence pur ; STT mocké (pas de réseau)
        byte[] speech = readPcm(FIXTURE);
        long maxRecordingMs = 12_000; // large : le bug le heurtait, un EOU correct est bien en-dessous
        CaptureConfig cfg = new CaptureConfig("", 3000, 500, maxRecordingMs);
        SttGate sttGate = mock(SttGate.class);
        when(sttGate.hasMinimumAudio()).thenReturn(true);
        when(sttGate.startTranscription()).thenReturn(SttCall.completed(SttResult.transcript("ok")));

        try (UtteranceCaptureService service =
                     new UtteranceCaptureService(new PartialReadMic(speech), sttGate, detector, new TurnTimeline())) {
            // When
            long start = System.currentTimeMillis();
            SttResult result = service.capture(cfg);
            long elapsed = System.currentTimeMillis() - start;

            // Then : fin d'énoncé sur le silence, PAS sur le plafond d'enregistrement
            assertThat(result.hasTranscript()).isTrue();
            assertThat(elapsed)
                    .as("EOU sur silence, pas le plafond maxRecordingMs (bug: courait jusqu'à %dms)", maxRecordingMs)
                    .isLessThan(maxRecordingMs);
        }
    }

    /**
     * Micro à lectures partielles (896 o — comme pw-record ; NE divise pas 1600 → désalignement
     * qui laisse des octets périmés) : sert le PCM de parole fourni, puis du silence pur à l'infini.
     */
    private static final class PartialReadMic implements MicrophoneSource {
        private static final int PARTIAL = 896;
        private final byte[] speech;
        private int pos = 0;

        PartialReadMic(byte[] speech) {
            this.speech = speech;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            int n = Math.min(length, PARTIAL);
            for (int i = 0; i < n; i++) {
                buffer[offset + i] = (pos < speech.length) ? speech[pos] : 0; // parole puis silence pur
                pos++;
            }
            return n;
        }

        @Override public void close() { }
        @Override public boolean isAvailable() { return true; }
        @Override public String describe() { return "partial-read (real speech)"; }
        @Override public int getSampleRate() { return 16000; }
    }

    // --- helpers ---

    /** Proportion de trames de 50 ms classées « parole » dans un flux PCM. */
    private double speechFrameRatio(byte[] pcm) {
        boolean[] verdicts = frameVerdicts(pcm);
        long speech = 0;
        for (boolean v : verdicts) if (v) speech++;
        return verdicts.length == 0 ? 0 : (double) speech / verdicts.length;
    }

    private boolean[] frameVerdicts(byte[] pcm) {
        int frames = pcm.length / FRAME_BYTES;
        boolean[] out = new boolean[frames];
        for (int i = 0; i < frames; i++) {
            out[i] = detector.isSpeech(Arrays.copyOfRange(pcm, i * FRAME_BYTES, (i + 1) * FRAME_BYTES));
        }
        return out;
    }

    private static boolean modelPresent(String resource) {
        try (InputStream in = SileroSpeechDetectorTest.class.getClassLoader().getResourceAsStream(resource)) {
            return in != null;
        } catch (IOException e) {
            return false;
        }
    }

    /** Lit le PCM 16-bit LE mono d'un WAV RIFF (en-tête 44 octets, fixtures 16 kHz mono). */
    private static byte[] readPcm(Path wav) throws IOException {
        byte[] all = Files.readAllBytes(wav);
        // Les fixtures ont un en-tête canonique de 44 octets ; on saute jusqu'au chunk "data".
        int p = 12;
        while (p + 8 <= all.length) {
            String chunkId = new String(all, p, 4);
            int size = (all[p + 4] & 0xFF) | ((all[p + 5] & 0xFF) << 8)
                    | ((all[p + 6] & 0xFF) << 16) | ((all[p + 7] & 0xFF) << 24);
            if ("data".equals(chunkId)) {
                return Arrays.copyOfRange(all, p + 8, p + 8 + size);
            }
            p += 8 + size;
        }
        throw new IOException("Chunk 'data' introuvable dans " + wav);
    }
}
