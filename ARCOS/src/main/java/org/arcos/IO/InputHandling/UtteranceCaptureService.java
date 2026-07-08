package org.arcos.IO.InputHandling;

import lombok.extern.slf4j.Slf4j;
import org.arcos.Configuration.AudioProperties;
import org.arcos.IO.InputHandling.STT.SttCall;
import org.arcos.IO.InputHandling.STT.SttGate;
import org.arcos.IO.InputHandling.STT.SttResult;
import org.arcos.IO.Telemetry.TurnTimeline;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Capture d'un énoncé utilisateur : lecture des trames micro, VAD RMS, détection de fin
 * d'énoncé par silence, et transcription STT spéculative.
 *
 * Source unique de la boucle de capture — utilisée par le chemin wake-word, la fenêtre de
 * conversation ({@link CaptureConfig} porte leurs différences) et le bench EOU (avec une
 * {@link MicrophoneSource} de fixture), pour que production et mesure exercent le même code.
 *
 * STT spéculatif : la transcription part pendant l'attente de confirmation du silence, donc
 * EOU ≈ max(silenceMs, round-trip STT) au lieu de la somme. Deux gardes contre le gaspillage :
 * un débounce de {@value #SPECULATION_DEBOUNCE_FRAMES} trames évite de spéculer sur chaque
 * micro-pause, et une reprise de parole ANNULE l'appel HTTP orphelin (l'executor mono-thread
 * ne reste jamais bloqué derrière une requête dont le résultat sera jeté).
 */
@Slf4j
public class UtteranceCaptureService implements AutoCloseable {

    private static final int SAMPLE_RATE = 16000;
    private static final int BYTES_PER_SAMPLE = 2;
    /** Trame de 50ms @16kHz. */
    private static final int WHISPER_FRAME_SIZE = SAMPLE_RATE * BYTES_PER_SAMPLE / 20;
    /** Ring buffer ~200ms préservant l'attaque de la parole. */
    private static final int PRE_BUFFER_FRAMES = 4;
    /** Trames de silence consécutives avant de lancer la spéculation (~100ms de débounce). */
    private static final int SPECULATION_DEBOUNCE_FRAMES = 2;
    private static final long SPECULATION_AWAIT_TIMEOUT_S = 10;

    private final MicrophoneSource micSource;
    private final SttGate sttGate;
    private final TurnTimeline turnTimeline;
    private final int silenceThreshold;
    private final ExecutorService speculationExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "stt-speculation");
        t.setDaemon(true);
        return t;
    });

    public UtteranceCaptureService(MicrophoneSource micSource,
                                   SttGate sttGate,
                                   int silenceThreshold,
                                   TurnTimeline turnTimeline) {
        this.micSource = micSource;
        this.sttGate = sttGate;
        this.silenceThreshold = silenceThreshold;
        this.turnTimeline = turnTimeline;
    }

    /**
     * Seuil VAD effectif : la valeur de config si explicitement fixée (≥ 0),
     * sinon le seuil recommandé par la source micro. Production et bench passent
     * par cette même résolution.
     */
    public static int resolveSilenceThreshold(AudioProperties audio, MicrophoneSource micSource) {
        int configured = audio.getSilenceThreshold();
        return configured >= 0 ? configured : micSource.recommendedSilenceThreshold();
    }

    /** Spéculation en vol : l'appel HTTP annulable + le Future qui l'exécute. */
    private record Speculation(SttCall call, Future<SttResult> result) {
        void cancel() {
            call.cancel();
            result.cancel(true);
        }
    }

    /**
     * Capture un énoncé et retourne son résultat typé (transcript, pas de parole,
     * incompréhensible, ou erreur backend). Bloquant — à appeler depuis le thread de capture.
     */
    public SttResult capture(CaptureConfig config) {
        log.info("{}Écoute (fenêtre initiale {}ms, fin d'énoncé à {}ms de silence)...",
                config.label(), config.initialListenWindowMs(), config.silenceDurationMs());

        sttGate.reset();

        final int micSampleRate = micSource.getSampleRate();
        final boolean needsResample = micSampleRate != SAMPLE_RATE;
        final int micFrameSize = needsResample
                ? (int) Math.ceil(WHISPER_FRAME_SIZE * micSampleRate / (double) SAMPLE_RATE)
                : WHISPER_FRAME_SIZE;

        byte[] micBuffer = new byte[micFrameSize];
        byte[] whisperBuffer = new byte[WHISPER_FRAME_SIZE];
        byte[][] preBuffer = new byte[PRE_BUFFER_FRAMES][];
        int preBufferIndex = 0;

        long lastSoundTime = System.currentTimeMillis();
        long recordingStartTime = System.currentTimeMillis();
        boolean hasDetectedSpeech = false;
        int consecutiveSilentFrames = 0;
        Speculation speculation = null;

        try {
            while (true) {
                int bytesRead = micSource.read(micBuffer, 0, micFrameSize);
                if (bytesRead <= 0) {
                    continue;
                }

                if (needsResample) {
                    resampleInto(micBuffer, bytesRead, whisperBuffer);
                } else {
                    System.arraycopy(micBuffer, 0, whisperBuffer, 0, Math.min(bytesRead, WHISPER_FRAME_SIZE));
                }

                // Ring buffer d'attaque, seulement en attente de parole
                if (!hasDetectedSpeech) {
                    preBuffer[preBufferIndex % PRE_BUFFER_FRAMES] = whisperBuffer.clone();
                    preBufferIndex++;
                }

                boolean isSilent = AudioFraming.isSilence(whisperBuffer, silenceThreshold);

                if (!isSilent) {
                    lastSoundTime = System.currentTimeMillis();
                    consecutiveSilentFrames = 0;
                    if (!hasDetectedSpeech) {
                        hasDetectedSpeech = true;
                        turnTimeline.beginTurn();
                        log.info("{}Parole détectée, enregistrement...", config.label());
                        // Restitue les trames d'attaque (la trame courante est bufferisée juste après)
                        int oldest = Math.max(0, preBufferIndex - PRE_BUFFER_FRAMES);
                        for (int j = oldest; j < preBufferIndex - 1; j++) {
                            byte[] frame = preBuffer[j % PRE_BUFFER_FRAMES];
                            if (frame != null) {
                                sttGate.processAudio(frame);
                            }
                        }
                    }
                    // Reprise de parole : annule la spéculation orpheline pour libérer l'executor
                    if (speculation != null) {
                        log.debug("{}Reprise de parole ; annulation du STT spéculatif orphelin", config.label());
                        speculation.cancel();
                        speculation = null;
                    }
                    // Seules les trames non silencieuses sont bufferisées : le silence de fin
                    // n'apporte rien au modèle STT, et le buffer à l'instant du silence-onset
                    // égale déjà l'énoncé complet (clé de la validité de la spéculation).
                    sttGate.processAudio(whisperBuffer);
                } else if (hasDetectedSpeech) {
                    consecutiveSilentFrames++;
                    if (speculation == null && consecutiveSilentFrames >= SPECULATION_DEBOUNCE_FRAMES) {
                        log.debug("{}Silence confirmé ({} trames) ; lancement du STT spéculatif", config.label(), consecutiveSilentFrames);
                        SttCall call = sttGate.startTranscription();
                        speculation = new Speculation(call, speculationExecutor.submit(call::await));
                    }
                    long silenceDuration = System.currentTimeMillis() - lastSoundTime;
                    if (silenceDuration >= config.silenceDurationMs()) {
                        log.info("{}Fin d'énoncé ({}ms de silence), transcription...", config.label(), config.silenceDurationMs());
                        break;
                    }
                }

                // Timeout : fenêtre courte en attente de parole, durée max une fois la parole engagée
                long elapsed = System.currentTimeMillis() - recordingStartTime;
                long timeoutMs = hasDetectedSpeech ? config.maxRecordingMs() : config.initialListenWindowMs();
                if (elapsed >= timeoutMs) {
                    log.info("{}{} après {}ms", config.label(),
                            hasDetectedSpeech ? "Durée max d'enregistrement atteinte" : "Fenêtre expirée sans parole",
                            elapsed);
                    break;
                }
            }

            if (hasDetectedSpeech && sttGate.hasMinimumAudio()) {
                log.info("{}Traitement de {}ms d'audio...", config.label(), sttGate.getBufferedAudioDurationMs());
                turnTimeline.markSpeechEnd(lastSoundTime);
                SttResult result = awaitSpeculationOrTranscribe(speculation);
                turnTimeline.markSttDone();
                return result;
            } else {
                if (speculation != null) {
                    speculation.cancel();
                }
                log.info("{}{}", config.label(),
                        hasDetectedSpeech ? "Pas assez d'audio pour la transcription" : "Aucune parole détectée");
                return SttResult.noSpeech();
            }
        } catch (Exception e) {
            log.error("{}Erreur lors de la capture d'énoncé", config.label(), e);
            if (speculation != null) {
                speculation.cancel();
            }
            return SttResult.error();
        }
    }

    /**
     * Attend la spéculation en vol, ou retombe sur un appel synchrone si aucune n'a été
     * lancée (énoncé clos par timeout, débounce jamais atteint) ou si elle échoue.
     */
    private SttResult awaitSpeculationOrTranscribe(Speculation speculation) {
        if (speculation == null) {
            return sttGate.getTranscription();
        }
        try {
            return speculation.result().get(SPECULATION_AWAIT_TIMEOUT_S, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            log.warn("STT spéculatif sans réponse après {}s ; annulation et appel synchrone", SPECULATION_AWAIT_TIMEOUT_S);
            speculation.cancel();
            return sttGate.getTranscription();
        } catch (Exception e) {
            log.warn("STT spéculatif en échec ; appel synchrone : {}", e.toString());
            return sttGate.getTranscription();
        }
    }

    private static void resampleInto(byte[] micBuffer, int bytesRead, byte[] whisperBuffer) {
        int samplesRead = bytesRead / BYTES_PER_SAMPLE;
        short[] micSamples = new short[samplesRead];
        ByteBuffer.wrap(micBuffer, 0, bytesRead)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer()
                .get(micSamples);

        int whisperSamples = WHISPER_FRAME_SIZE / BYTES_PER_SAMPLE;
        short[] downsampled = new short[whisperSamples];
        AudioFraming.downsample(micSamples, samplesRead, downsampled, whisperSamples);

        ByteBuffer bb = ByteBuffer.wrap(whisperBuffer).order(ByteOrder.LITTLE_ENDIAN);
        for (short s : downsampled) {
            bb.putShort(s);
        }
    }

    @Override
    public void close() {
        speculationExecutor.shutdownNow();
    }
}
