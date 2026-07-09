package org.arcos.Producers;

import org.arcos.Configuration.AudioProperties;
import org.arcos.EventBus.EventQueue;
import org.arcos.EventBus.Events.Event;
import org.arcos.EventBus.Events.EventPriority;
import org.arcos.EventBus.Events.EventType;
import org.arcos.EventBus.Events.WakeWordEvent;
import org.arcos.IO.InputHandling.AudioFraming;
import org.arcos.IO.InputHandling.CaptureConfig;
import org.arcos.IO.InputHandling.JavaSoundMicrophoneSource;
import org.arcos.IO.InputHandling.MicrophoneSource;
import org.arcos.IO.InputHandling.PipeWireMicrophoneSource;
import org.arcos.IO.InputHandling.SileroSpeechDetector;
import org.arcos.IO.InputHandling.UtteranceCaptureService;
import org.arcos.Configuration.SpeechToTextProperties;
import org.arcos.IO.InputHandling.STT.SttGate;
import org.arcos.IO.InputHandling.STT.SttResult;
import org.arcos.IO.Telemetry.TurnTimeline;
import org.arcos.IO.OuputHandling.StateHandler.AudioCue.AudioCueFeedbackHandler;
import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.IO.OuputHandling.StateHandler.UXEventType;
import org.arcos.IO.OuputHandling.StateHandler.FeedBackEvent;
import ai.picovoice.porcupine.Porcupine;
import ai.picovoice.porcupine.PorcupineException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Component
@Slf4j
public class WakeWordProducer implements Runnable {

    private Porcupine porcupine;
    private String[] keywords;
    private SttGate sttGate;
    private MicrophoneSource micSource;
    private final EventQueue eventQueue;
    private final CentralFeedBackHandler centralFeedBackHandler;
    private final AudioCueFeedbackHandler audioCueFeedbackHandler;
    private final AudioProperties audioProperties;
    private final SpeechToTextProperties sttProperties;
    private final TurnTimeline turnTimeline;
    private final SileroSpeechDetector speechDetector;

    /**
     * Boucle de capture partagée (VAD + fin d'énoncé + STT spéculatif) — créée une fois
     * la source micro et le SttGate initialisés, recréée si la source micro est remplacée.
     */
    private UtteranceCaptureService captureService;
    private volatile Thread wakeWordThread;
    private boolean porcupineEnabled = false;
    private boolean porcupineInitialized = false;

    private static final int PORCUPINE_SAMPLE_RATE = 16000;
    private static final int BYTES_PER_SAMPLE = 2;
    private int micFailureCount = 0;

    private volatile boolean suspended = false;
    private volatile boolean needsDrain = false;
    private volatile boolean inConversationWindowMode = false;
    private volatile long conversationWindowExpiry = 0L;
    /** Silence de fin d'énoncé pour la prochaine fenêtre de conversation (endpointing contextuel). */
    private volatile long nextConversationSilenceMs = -1;

    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    public void startAfterStartup() {
        initializePorcupineAndAudio();
        if (!porcupineEnabled) {
            log.info("WakeWordProducer désactivé — thread non démarré.");
            return;
        }
        if (this.micSource != null && this.micSource.isAvailable()) {
            wakeWordThread = new Thread(this, "wakeword-producer");
            wakeWordThread.setDaemon(true);
            wakeWordThread.start();
        } else {
            log.warn("Aucun device audio disponible. Wake word non démarré.");
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("WakeWordProducer arrêt");
        if (wakeWordThread != null) {
            wakeWordThread.interrupt();
        }
        if (micSource != null) {
            micSource.close();
        }
        if (porcupine != null) {
            porcupine.delete();
        }
        if (captureService != null) {
            captureService.close();
        }
    }

    @Autowired
    public WakeWordProducer(EventQueue eventQueue,
                            CentralFeedBackHandler centralFeedBackHandler,
                            AudioCueFeedbackHandler audioCueFeedbackHandler,
                            AudioProperties audioProperties,
                            SpeechToTextProperties sttProperties,
                            TurnTimeline turnTimeline,
                            SileroSpeechDetector speechDetector) {
        this.centralFeedBackHandler = centralFeedBackHandler;
        this.audioCueFeedbackHandler = audioCueFeedbackHandler;
        this.eventQueue = eventQueue;
        this.audioProperties = audioProperties;
        this.sttProperties = sttProperties;
        this.turnTimeline = turnTimeline;
        this.speechDetector = speechDetector;
    }

    /**
     * Deferred init: runs on ApplicationReadyEvent (after BootReporter closes the Lanterna screen)
     * so that Porcupine's native [INFO] messages don't corrupt the TUI.
     */
    private void initializePorcupineAndAudio() {
        if (porcupineInitialized) return;
        porcupineInitialized = true;
        log.info("Initialisation WakeWordProducer");
        try {
            String keywordName = "Mon-ami_fr_linux_v3_0_0.ppn";
            String porcupineModelName = "porcupine_params_fr.pv";
            String[] keywordPaths;

            try {
                keywordPaths = new String[]{getKeywordPath("Calcifer.ppn")};
            } catch (IllegalArgumentException e) {
                log.debug("Calcifer.ppn absent, fallback vers {}", keywordName);
                keywordPaths = new String[]{getKeywordPath(keywordName)};
            }
            String porcupineModelPath = getPorcupineModelPath(porcupineModelName);

            File keywordFile = new File(keywordPaths[0]);
            if (!keywordFile.exists()) {
                throw new IllegalArgumentException(String.format("Fichier keyword '%s' inexistant", keywordPaths[0]));
            }
            this.keywords = keywordPaths;
            initializePorcupine(keywordPaths, porcupineModelPath);
            if (!speechDetector.isAvailable()) {
                // Le VAD Silero est le seul détecteur de parole : sans lui, aucune capture n'est
                // possible. On désactive la voix proprement plutôt que de capturer à l'aveugle.
                centralFeedBackHandler.handleFeedBack(new FeedBackEvent(UXEventType.FAILURE));
                log.error("VAD Silero indisponible (modèle absent ou init ONNX en échec). "
                        + "Capture vocale désactivée — ARCOS démarrera sans reconnaissance de la parole.");
                this.porcupineEnabled = false;
                return;
            }
            initializeMicrophone();
            if (this.micSource != null && this.micSource.isAvailable()) {
                log.info("VAD: {}", speechDetector.describe());
                this.sttGate = SttGate.create(sttProperties.getBackend(), sttProperties);
                rebuildCaptureService();
            }
            this.porcupineEnabled = true;
            log.info("WakeWordProducer initialisé avec succès.");
        } catch (Exception e) {
            log.warn("Wake word désactivé : {}. ARCOS démarrera sans détection du mot de réveil.", e.getMessage());
            this.porcupineEnabled = false;
        }
    }

    /**
     * Tries PipeWire first (handles device routing and resampling natively), falls back to Java Sound API.
     */
    private void initializeMicrophone() {
        // Try PipeWire first — captures at 16kHz natively (no downsampling needed)
        if (PipeWireMicrophoneSource.isPipeWireAvailable()) {
            PipeWireMicrophoneSource pwSource = new PipeWireMicrophoneSource();
            if (pwSource.isAvailable()) {
                this.micSource = pwSource;
                log.info("Audio source: {}", micSource.describe());
                return;
            }
            pwSource.close();
        }

        // Fallback to Java Sound API (Raspberry Pi, systems without PipeWire)
        log.info("PipeWire not available, falling back to Java Sound API");
        JavaSoundMicrophoneSource jsSource = new JavaSoundMicrophoneSource(
                audioProperties.getSampleRate(), audioProperties.getInputDeviceIndex());
        if (jsSource.isAvailable()) {
            this.micSource = jsSource;
            log.info("Audio source: {}", micSource.describe());
        } else {
            log.warn("No audio source available");
            this.micSource = null;
        }
    }

    private String extractResource(String resourceName) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IllegalArgumentException("Resource not found: " + resourceName);
            }

            File tempFile = File.createTempFile(resourceName, "");
            tempFile.deleteOnExit();

            Files.copy(in, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return tempFile.getAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract resource: " + resourceName, e);
        }
    }

    private String getKeywordPath(String keyword) {
        return extractResource(keyword);
    }

    private String getPorcupineModelPath(String model) {
        return extractResource(model);
    }

    private void initializePorcupine(String[] keywords, String modelPath) {
        try {
            this.porcupine = new Porcupine.Builder()
                    .setAccessKey(System.getenv("PORCUPINE_ACCESS_KEY"))
                    .setKeywordPaths(keywords)
                    .setModelPath(modelPath)
                    .build();
        } catch (PorcupineException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        if (!porcupineEnabled || porcupine == null) {
            log.warn("WakeWordProducer.run() appelé mais Porcupine non initialisé.");
            return;
        }
        final int micSampleRate = micSource.getSampleRate();
        final boolean needsDownsampling = micSampleRate != PORCUPINE_SAMPLE_RATE;
        final int porcupineFrameLength = porcupine.getFrameLength();
        final int micFrameSize = needsDownsampling
                ? (int) Math.ceil(porcupineFrameLength * micSampleRate / (double) PORCUPINE_SAMPLE_RATE) * BYTES_PER_SAMPLE
                : porcupineFrameLength * BYTES_PER_SAMPLE;

        byte[] micBuffer = new byte[micFrameSize];
        short[] resampledBuffer = new short[porcupineFrameLength];

        log.info("Starting wake word detection loop (frameLength={}, micFrameSize={} bytes, micRate={}, porcupineRate={}, downsampling={})",
                porcupineFrameLength, micFrameSize, micSampleRate, PORCUPINE_SAMPLE_RATE, needsDownsampling);

        long lastRmsLogTime = 0;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // --- Suspended: skip mic processing while TTS is playing ---
                if (suspended) {
                    Thread.sleep(50);
                    continue;
                }

                // --- Just exited suspension: drain stale mic data (TTS echo) ---
                if (needsDrain) {
                    log.debug("Draining mic buffer after TTS playback");
                    micSource.drain();
                    needsDrain = false;
                }

                // --- Mode conversation : bypass Porcupine ---
                if (inConversationWindowMode) {
                    long remaining = conversationWindowExpiry - System.currentTimeMillis();
                    if (remaining <= 0) {
                        inConversationWindowMode = false;
                        log.info("Fenêtre de conversation expirée sans parole détectée");
                        emitListeningWindowTimeout();
                        continue;
                    }
                    long silenceMs = nextConversationSilenceMs > 0
                            ? nextConversationSilenceMs
                            : audioProperties.getConversationSilenceMs();
                    SttResult result = captureService.capture(
                            CaptureConfig.forConversation(audioProperties, remaining, silenceMs));
                    inConversationWindowMode = false;
                    switch (result.status()) {
                        case TRANSCRIPT -> {
                            log.info(">>> [CONVERSATION] TRANSCRIBED: {}", result.text());
                            eventQueue.offer(new WakeWordEvent(result.text(), "conversation", true));
                        }
                        case UNINTELLIGIBLE -> emitSttEvent(EventType.STT_UNINTELLIGIBLE);
                        case ERROR -> emitSttEvent(EventType.STT_ERROR);
                        case NO_SPEECH -> {
                            log.info(">>> [CONVERSATION] Aucune parole dans la fenêtre");
                            emitListeningWindowTimeout();
                        }
                    }
                    continue;
                }

                // --- Mode veille standard : boucle Porcupine ---
                int bytesRead = micSource.read(micBuffer, 0, micFrameSize);

                if (bytesRead > 0) {
                    int samplesRead = bytesRead / BYTES_PER_SAMPLE;
                    short[] micSamples = new short[samplesRead];

                    ByteBuffer.wrap(micBuffer, 0, bytesRead)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .asShortBuffer()
                            .get(micSamples);

                    // Log RMS every 5 seconds to verify mic is capturing audio (santé micro,
                    // pas de la détection — celle-ci est faite par le VAD Silero dans la capture)
                    long now = System.currentTimeMillis();
                    if (now - lastRmsLogTime > 5000) {
                        long sum = 0;
                        for (int i = 0; i < samplesRead; i++) {
                            sum += (long) micSamples[i] * micSamples[i];
                        }
                        double rms = Math.sqrt((double) sum / samplesRead);
                        log.info("Audio RMS level: {} (samples: {}, source: {})",
                                (int) rms, samplesRead, micSource.describe());
                        lastRmsLogTime = now;
                    }

                    // Downsample to 16kHz if needed (PipeWire already outputs at 16kHz)
                    if (needsDownsampling) {
                        AudioFraming.downsample(micSamples, samplesRead, resampledBuffer, porcupineFrameLength);
                    } else {
                        System.arraycopy(micSamples, 0, resampledBuffer, 0, Math.min(samplesRead, porcupineFrameLength));
                    }

                    // Check for wake word
                    int result = porcupine.process(resampledBuffer);

                    if (result >= 0) {
                        log.info("[{}] Detected '{}'",
                                LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                                keywords[result]);
                        centralFeedBackHandler.handleFeedBack(new FeedBackEvent(UXEventType.WAKEUP_SHORT));
                        audioCueFeedbackHandler.playWakeUpSoundSoftSync(); // blocks until cue finishes — prevents mic bleed

                        // Switch to transcription mode
                        SttResult sttResult = captureService.capture(CaptureConfig.forWake(audioProperties));

                        switch (sttResult.status()) {
                            case TRANSCRIPT -> {
                                log.info(">>> TRANSCRIBED MESSAGE: {}", sttResult.text());
                                eventQueue.offer(new WakeWordEvent(sttResult.text(), "default"));
                            }
                            case UNINTELLIGIBLE -> emitSttEvent(EventType.STT_UNINTELLIGIBLE);
                            case ERROR -> emitSttEvent(EventType.STT_ERROR);
                            case NO_SPEECH -> log.info(">>> No speech detected");
                        }
                    }
                } else if (bytesRead < 0) {
                    micFailureCount++;
                    long backoff = backoffMillis(micFailureCount);
                    log.error("Source audio PipeWire morte (échec #{}). Nouvelle tentative dans {}ms (on reste sur PipeWire).",
                            micFailureCount, backoff);
                    centralFeedBackHandler.handleFeedBack(new FeedBackEvent(UXEventType.FAILURE));
                    Thread.sleep(backoff);                 // interruptible — InterruptedException exits via the loop's catch
                    recreatePipeWireSource();
                    if (micSource.isAvailable()) {         // micSource is never null — see recreatePipeWireSource()
                        rebuildCaptureService();           // le service référence la source remplacée
                        log.info("Source PipeWire rétablie après {} échec(s).", micFailureCount);
                        micFailureCount = 0;               // recovered: reset
                    }
                    // do NOT break — never give up; next loop iteration re-reads (or backs off again)
                }
            } catch (PorcupineException e) {
                log.error("Error processing audio with Porcupine", e);
            } catch (Exception e) {
                log.error("Error in wake word detection loop", e);
                break;
            }
        }

        log.info("WakeWordProducer thread finished.");
    }

    /** Bounded exponential backoff: 1s, 2s, 4s … capped at 30s. Never 0, never unbounded. */
    private long backoffMillis(int failureCount) {
        return Math.min(30_000L, 1000L * (1L << Math.min(failureCount - 1, 5)));
    }

    /** Recreate a PipeWire source ONLY — never downgrade to JavaSound (owner decision: stay on PipeWire). */
    private void recreatePipeWireSource() {
        PipeWireMicrophoneSource pw = new PipeWireMicrophoneSource();
        if (pw.isAvailable()) {
            if (micSource != null) micSource.close();
            this.micSource = pw;
        } else {
            pw.close();
            // NEVER set micSource to null: the loop top calls micSource.read() with NO null
            // guard, and an NPE there lands in the outer catch(Exception) which break;s —
            // killing the producer for good. Keeping the old (dead) source means the next
            // read() returns -1 → back into the backoff branch, which is the retry we want.
        }
    }

    /** (Re)crée la boucle de capture — à appeler quand micSource change. */
    private void rebuildCaptureService() {
        if (captureService != null) {
            captureService.close();
        }
        this.captureService = new UtteranceCaptureService(micSource, sttGate, speechDetector, turnTimeline);
    }

    /**
     * Ouvre une fenêtre d'écoute en mode conversation (sans mot de réveil).
     * Appelée par l'Orchestrator après fin TTS si les conditions sont remplies.
     * Thread-safe : les champs volatile garantissent la visibilité cross-thread.
     *
     * @param durationMs Durée de la fenêtre en ms
     */
    public void suspend() {
        suspended = true;
        needsDrain = true;
        log.debug("WakeWordProducer suspended (TTS playing)");
    }

    public void resumeDetection() {
        // Drain happens on the wakeword-producer thread when it exits suspension
        suspended = false;
        log.debug("WakeWordProducer resumed");
    }

    public void openConversationWindow(int durationMs) {
        openConversationWindow(durationMs, -1);
    }

    /**
     * @param silenceDurationMs silence de fin d'énoncé contextuel pour cette fenêtre
     *                          (EndpointingPolicyService) ; -1 = valeur de config
     */
    public void openConversationWindow(int durationMs, long silenceDurationMs) {
        if (!porcupineEnabled) {
            log.debug("openConversationWindow ignorée : Porcupine non actif");
            return;
        }
        nextConversationSilenceMs = silenceDurationMs;
        // Set conversation state BEFORE clearing suspended, so the wakeword thread
        // sees the conversation window as soon as it resumes (avoids race condition
        // where thread wakes, drains instantly with JavaSound, and misses the flag).
        conversationWindowExpiry = System.currentTimeMillis() + durationMs;
        inConversationWindowMode = true;
        suspended = false;
        log.debug("Fenêtre conversation ouverte pour {}ms (silence {}ms)", durationMs, silenceDurationMs);
    }

    private void emitListeningWindowTimeout() {
        Event<Void> timeout = new Event<>(
                EventType.LISTENING_WINDOW_TIMEOUT,
                EventPriority.LOW,
                null,
                "WakeWordProducer"
        );
        eventQueue.offer(timeout);
    }

    /** Parole incompréhensible ou backend STT en panne — l'Orchestrator porte la réaction utilisateur. */
    private void emitSttEvent(EventType type) {
        log.info(">>> {} — délégué à l'Orchestrator", type);
        eventQueue.offer(new Event<>(type, EventPriority.HIGH, null, "WakeWordProducer"));
    }

}
