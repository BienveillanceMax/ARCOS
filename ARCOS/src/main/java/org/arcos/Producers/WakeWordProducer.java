package org.arcos.Producers;

import org.arcos.Configuration.AudioProperties;
import org.arcos.EventBus.EventQueue;
import org.arcos.EventBus.Events.Event;
import org.arcos.EventBus.Events.EventPriority;
import org.arcos.EventBus.Events.EventType;
import org.arcos.EventBus.Events.WakeWordEvent;
import org.arcos.IO.InputHandling.AudioFraming;
import org.arcos.IO.InputHandling.JavaSoundMicrophoneSource;
import org.arcos.IO.InputHandling.MicrophoneSource;
import org.arcos.IO.InputHandling.PipeWireMicrophoneSource;
import org.arcos.Configuration.SpeechToTextProperties;
import org.arcos.IO.InputHandling.STT.SttGate;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

    /**
     * Single-thread executor that runs speculative STT calls launched the moment silence is
     * first detected. The blocking HTTP round-trip runs concurrently with the
     * {@code silenceDurationMs} confirmation wait, so end-of-utterance latency drops to
     * {@code max(silenceDurationMs, sttRoundTrip)} instead of {@code silenceDurationMs + sttRoundTrip}.
     * If the speaker resumes within the silence window, the in-flight future is orphaned
     * (compute is wasted but no extra user-perceived latency is incurred).
     */
    private final ExecutorService speculationExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "stt-speculation");
        t.setDaemon(true);
        return t;
    });
    private volatile Thread wakeWordThread;
    private boolean porcupineEnabled = false;
    private boolean porcupineInitialized = false;

    private static final int PORCUPINE_SAMPLE_RATE = 16000;
    private static final int BYTES_PER_SAMPLE = 2;
    private int silenceThreshold;
    private int micFailureCount = 0;

    /**
     * 21-tap low-pass FIR filter (Hamming window, fc=7200Hz at 44100Hz).
     * Moved to {@link org.arcos.IO.InputHandling.AudioFraming}; kept as a deprecated alias
     * so any external code that still depends on this constant continues to resolve.
     *
     * @deprecated use {@link org.arcos.IO.InputHandling.AudioFraming#LP_FILTER}
     */
    @Deprecated
    private static final double[] LP_FILTER = AudioFraming.LP_FILTER;

    private volatile boolean suspended = false;
    private volatile boolean needsDrain = false;
    private volatile boolean inConversationWindowMode = false;
    private volatile long conversationWindowExpiry = 0L;

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
        speculationExecutor.shutdownNow();
    }

    @Autowired
    public WakeWordProducer(EventQueue eventQueue,
                            CentralFeedBackHandler centralFeedBackHandler,
                            AudioCueFeedbackHandler audioCueFeedbackHandler,
                            AudioProperties audioProperties,
                            SpeechToTextProperties sttProperties) {
        this.centralFeedBackHandler = centralFeedBackHandler;
        this.audioCueFeedbackHandler = audioCueFeedbackHandler;
        this.eventQueue = eventQueue;
        this.audioProperties = audioProperties;
        this.sttProperties = sttProperties;
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
            initializeMicrophone();
            if (this.micSource != null && this.micSource.isAvailable()) {
                this.silenceThreshold = micSource.recommendedSilenceThreshold();
                log.info("Silence threshold: {} (from {})", silenceThreshold, micSource.describe());
                this.sttGate = SttGate.create(sttProperties.getBackend(), sttProperties);
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
                    String transcription = recordAndTranscribeForConversation((int) remaining);
                    inConversationWindowMode = false;
                    if (transcription != null && !transcription.isEmpty()) {
                        log.info(">>> [CONVERSATION] TRANSCRIBED: {}", transcription);
                        WakeWordEvent event = new WakeWordEvent(transcription, "conversation", true);
                        eventQueue.offer(event);
                    } else {
                        log.info(">>> [CONVERSATION] Aucune parole dans la fenêtre");
                        emitListeningWindowTimeout();
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

                    // Log RMS every 5 seconds to verify mic is capturing audio
                    long now = System.currentTimeMillis();
                    if (now - lastRmsLogTime > 5000) {
                        long sum = 0;
                        for (int i = 0; i < samplesRead; i++) {
                            sum += (long) micSamples[i] * micSamples[i];
                        }
                        double rms = Math.sqrt((double) sum / samplesRead);
                        log.info("Audio RMS level: {} (threshold: {}, samples: {}, source: {})",
                                (int) rms, silenceThreshold, samplesRead, micSource.describe());
                        lastRmsLogTime = now;
                    }

                    // Downsample to 16kHz if needed (PipeWire already outputs at 16kHz)
                    if (needsDownsampling) {
                        downsample(micSamples, samplesRead, resampledBuffer, porcupineFrameLength);
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
                        String transcription = recordAndTranscribe();

                        if (transcription != null && !transcription.isEmpty()) {
                            log.info(">>> TRANSCRIBED MESSAGE: {}", transcription);
                            WakeWordEvent event = new WakeWordEvent(transcription, "default");
                            eventQueue.offer(event);
                        } else {
                            log.info(">>> No speech detected or transcription failed");
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
                        this.silenceThreshold = micSource.recommendedSilenceThreshold();
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

    private void downsample(short[] input, int inputLength, short[] output, int outputLength) {
        AudioFraming.downsample(input, inputLength, output, outputLength);
    }

    private String recordAndTranscribe() {
        log.info("Started listening for speech...");

        sttGate.reset();

        final int micSampleRate = micSource.getSampleRate();
        final boolean needsResample = micSampleRate != PORCUPINE_SAMPLE_RATE;
        // Whisper frame: 50ms at 16kHz = 1600 bytes
        final int whisperFrameSize = PORCUPINE_SAMPLE_RATE * BYTES_PER_SAMPLE / 20;
        final int micFrameSize = needsResample
                ? (int) Math.ceil(whisperFrameSize * micSampleRate / (double) PORCUPINE_SAMPLE_RATE)
                : whisperFrameSize;

        byte[] micBuffer = new byte[micFrameSize];
        byte[] whisperBuffer = new byte[whisperFrameSize];

        // Pre-buffer: ring buffer of recent frames to preserve speech onset
        final int PRE_BUFFER_FRAMES = 4; // ~200ms at 50ms/frame
        byte[][] preBuffer = new byte[PRE_BUFFER_FRAMES][];
        int preBufferIndex = 0;

        long lastSoundTime = System.currentTimeMillis();
        long recordingStartTime = System.currentTimeMillis();
        boolean hasDetectedSpeech = false;
        // Speculative STT: launched on the first silent frame after speech, awaited at loop exit.
        // null = no speculation in flight (either we never started or the speaker resumed and we orphaned it).
        Future<String> speculation = null;

        try {
            while (true) {
                int bytesRead = micSource.read(micBuffer, 0, micFrameSize);

                if (bytesRead > 0) {
                    if (needsResample) {
                        int samplesRead = bytesRead / BYTES_PER_SAMPLE;
                        short[] micSamples = new short[samplesRead];
                        ByteBuffer.wrap(micBuffer, 0, bytesRead)
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .asShortBuffer()
                                .get(micSamples);

                        int whisperSamples = whisperFrameSize / BYTES_PER_SAMPLE;
                        short[] downsampled = new short[whisperSamples];
                        downsample(micSamples, samplesRead, downsampled, whisperSamples);

                        ByteBuffer bb = ByteBuffer.wrap(whisperBuffer).order(ByteOrder.LITTLE_ENDIAN);
                        for (short s : downsampled) {
                            bb.putShort(s);
                        }
                    } else {
                        System.arraycopy(micBuffer, 0, whisperBuffer, 0, Math.min(bytesRead, whisperFrameSize));
                    }

                    // Store frame in ring buffer before silence check (only while waiting for speech)
                    if (!hasDetectedSpeech) {
                        preBuffer[preBufferIndex % PRE_BUFFER_FRAMES] = whisperBuffer.clone();
                        preBufferIndex++;
                    }

                    // Check for silence
                    boolean isSilent = isSilence(whisperBuffer);

                    if (!isSilent) {
                        lastSoundTime = System.currentTimeMillis();
                        if (!hasDetectedSpeech) {
                            hasDetectedSpeech = true;
                            log.info("Speech detected, recording...");
                            // Flush pre-buffer: send prior frames that contain the speech onset
                            int oldest = Math.max(0, preBufferIndex - PRE_BUFFER_FRAMES);
                            for (int j = oldest; j < preBufferIndex - 1; j++) {
                                byte[] frame = preBuffer[j % PRE_BUFFER_FRAMES];
                                if (frame != null) {
                                    sttGate.processAudio(frame);
                                }
                            }
                        }
                        // Speaker resumed — orphan any in-flight speculative STT.
                        // We do not cancel the OkHttp call (saves complexity); its result is just discarded.
                        if (speculation != null) {
                            log.debug("Speech resumed; orphaning speculative STT");
                            speculation = null;
                        }
                    } else if (hasDetectedSpeech && speculation == null) {
                        // First silent frame after speech — launch the speculative STT call now,
                        // so its HTTP round-trip overlaps with the silenceDurationMs confirmation wait.
                        log.debug("Silence-onset; launching speculative STT");
                        speculation = speculationExecutor.submit(sttGate::getTranscription);
                    }

                    // Only buffer audio once speech has been detected.
                    // Skip trailing-silence frames — they add audio that the STT model
                    // has to process for no information gain. Inter-word brief pauses
                    // typically remain non-silent thanks to ambient/breath noise; pure
                    // tail-silence after the utterance is what gets dropped here.
                    if (hasDetectedSpeech && !isSilent) {
                        sttGate.processAudio(whisperBuffer);
                    }

                    // Check if we should stop due to silence
                    if (hasDetectedSpeech && isSilent) {
                        long silenceDuration = System.currentTimeMillis() - lastSoundTime;
                        long silenceDurationMs = audioProperties.getSilenceDurationMs();
                        if (silenceDuration >= silenceDurationMs) {
                            log.info("Detected {}ms of silence, processing transcription...", silenceDurationMs);
                            break;
                        }
                    }

                    // Timeout: short window while waiting for speech, full duration once speaking
                    long elapsed = System.currentTimeMillis() - recordingStartTime;
                    long timeoutMs = hasDetectedSpeech
                            ? (long) audioProperties.getMaxRecordingSeconds() * 1000
                            : audioProperties.getPostResponseListeningWindowMs();
                    if (elapsed >= timeoutMs) {
                        log.info(hasDetectedSpeech
                                ? "Maximum recording time reached, processing transcription..."
                                : "No speech detected within {}ms, aborting", timeoutMs);
                        break;
                    }
                }
            }

            // Process transcription only if speech was actually detected.
            // If we launched a speculative call at silence-onset, await it instead of a fresh blocking call.
            if (hasDetectedSpeech && sttGate.hasMinimumAudio()) {
                log.info("Processing {}ms of audio...", sttGate.getBufferedAudioDurationMs());
                return awaitSpeculationOrTranscribe(speculation);
            } else {
                if (speculation != null) speculation.cancel(true);
                log.info(hasDetectedSpeech ? "Not enough audio data for transcription" : "No speech detected");
                return "";
            }

        } catch (Exception e) {
            log.error("Error during transcription recording", e);
            return "";
        }
    }

    private boolean isSilence(byte[] audioData) {
        return AudioFraming.isSilence(audioData, silenceThreshold);
    }

    /**
     * If a speculative STT call was launched at silence-onset, await its result; otherwise
     * fall back to a synchronous {@code sttGate.getTranscription()}. Any exception or timeout
     * from the speculative path also falls back, so the request still completes.
     */
    private String awaitSpeculationOrTranscribe(Future<String> speculation) {
        if (speculation == null) {
            return sttGate.getTranscription();
        }
        try {
            return speculation.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            log.warn("Speculative STT timed out after 10s; cancelling and falling back to sync call");
            speculation.cancel(true);
            return sttGate.getTranscription();
        } catch (Exception e) {
            log.warn("Speculative STT failed; falling back to sync call: {}", e.toString());
            return sttGate.getTranscription();
        }
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
        if (!porcupineEnabled) {
            log.debug("openConversationWindow ignorée : Porcupine non actif");
            return;
        }
        // Set conversation state BEFORE clearing suspended, so the wakeword thread
        // sees the conversation window as soon as it resumes (avoids race condition
        // where thread wakes, drains instantly with JavaSound, and misses the flag).
        conversationWindowExpiry = System.currentTimeMillis() + durationMs;
        inConversationWindowMode = true;
        suspended = false;
        log.debug("Fenêtre conversation ouverte pour {}ms", durationMs);
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

    private String recordAndTranscribeForConversation(int maxDurationMs) {
        log.info("[CONVERSATION] Écoute pendant {}ms max...", maxDurationMs);

        sttGate.reset();

        final int micSampleRate = micSource.getSampleRate();
        final boolean needsResample = micSampleRate != PORCUPINE_SAMPLE_RATE;
        final int whisperFrameSize = PORCUPINE_SAMPLE_RATE * BYTES_PER_SAMPLE / 20;
        final int micFrameSize = needsResample
                ? (int) Math.ceil(whisperFrameSize * micSampleRate / (double) PORCUPINE_SAMPLE_RATE)
                : whisperFrameSize;

        byte[] micBuffer = new byte[micFrameSize];
        byte[] whisperBuffer = new byte[whisperFrameSize];

        // Pre-buffer: ring buffer of recent frames to preserve speech onset
        final int PRE_BUFFER_FRAMES = 4; // ~200ms at 50ms/frame
        byte[][] preBuffer = new byte[PRE_BUFFER_FRAMES][];
        int preBufferIndex = 0;

        long lastSoundTime = System.currentTimeMillis();
        long recordingStartTime = System.currentTimeMillis();
        boolean hasDetectedSpeech = false;
        // Speculative STT — see recordAndTranscribe() for rationale.
        Future<String> speculation = null;

        try {
            while (true) {
                int bytesRead = micSource.read(micBuffer, 0, micFrameSize);

                if (bytesRead > 0) {
                    if (needsResample) {
                        int samplesRead = bytesRead / BYTES_PER_SAMPLE;
                        short[] micSamples = new short[samplesRead];
                        ByteBuffer.wrap(micBuffer, 0, bytesRead)
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .asShortBuffer()
                                .get(micSamples);

                        int whisperSamples = whisperFrameSize / BYTES_PER_SAMPLE;
                        short[] downsampled = new short[whisperSamples];
                        downsample(micSamples, samplesRead, downsampled, whisperSamples);

                        ByteBuffer bb = ByteBuffer.wrap(whisperBuffer).order(ByteOrder.LITTLE_ENDIAN);
                        for (short s : downsampled) {
                            bb.putShort(s);
                        }
                    } else {
                        System.arraycopy(micBuffer, 0, whisperBuffer, 0, Math.min(bytesRead, whisperFrameSize));
                    }

                    // Store frame in ring buffer before silence check (only while waiting for speech)
                    if (!hasDetectedSpeech) {
                        preBuffer[preBufferIndex % PRE_BUFFER_FRAMES] = whisperBuffer.clone();
                        preBufferIndex++;
                    }

                    boolean isSilent = isSilence(whisperBuffer);

                    if (!isSilent) {
                        lastSoundTime = System.currentTimeMillis();
                        if (!hasDetectedSpeech) {
                            hasDetectedSpeech = true;
                            log.info("[CONVERSATION] Parole détectée, enregistrement...");
                            // Flush pre-buffer: send prior frames that contain the speech onset
                            int oldest = Math.max(0, preBufferIndex - PRE_BUFFER_FRAMES);
                            for (int j = oldest; j < preBufferIndex - 1; j++) {
                                byte[] frame = preBuffer[j % PRE_BUFFER_FRAMES];
                                if (frame != null) {
                                    sttGate.processAudio(frame);
                                }
                            }
                        }
                        if (speculation != null) {
                            log.debug("[CONVERSATION] Speech resumed; orphaning speculative STT");
                            speculation = null;
                        }
                    } else if (hasDetectedSpeech && speculation == null) {
                        log.debug("[CONVERSATION] Silence-onset; launching speculative STT");
                        speculation = speculationExecutor.submit(sttGate::getTranscription);
                    }

                    // Only buffer audio once speech has been detected; skip trailing silence (see initial loop).
                    if (hasDetectedSpeech && !isSilent) {
                        sttGate.processAudio(whisperBuffer);
                    }

                    if (hasDetectedSpeech && isSilent) {
                        long silenceDuration = System.currentTimeMillis() - lastSoundTime;
                        if (silenceDuration >= audioProperties.getConversationSilenceMs()) {
                            log.info("[CONVERSATION] Silence de {}ms, traitement...", audioProperties.getConversationSilenceMs());
                            break;
                        }
                    }

                    long elapsed = System.currentTimeMillis() - recordingStartTime;
                    // Window timeout only applies while waiting for speech to start.
                    // Once speech is detected, let silence detection handle the end,
                    // with maxRecordingSeconds as a safety backstop.
                    long timeout = hasDetectedSpeech
                            ? (long) audioProperties.getMaxRecordingSeconds() * 1000
                            : maxDurationMs;
                    if (elapsed >= timeout) {
                        log.info("[CONVERSATION] {} après {}ms",
                                hasDetectedSpeech ? "Durée max d'enregistrement atteinte" : "Fenêtre expirée sans parole",
                                elapsed);
                        break;
                    }
                }
            }

            if (hasDetectedSpeech && sttGate.hasMinimumAudio()) {
                log.info("[CONVERSATION] Traitement de {}ms d'audio...", sttGate.getBufferedAudioDurationMs());
                return awaitSpeculationOrTranscribe(speculation);
            } else {
                if (speculation != null) speculation.cancel(true);
                log.info(hasDetectedSpeech ? "[CONVERSATION] Pas assez d'audio pour la transcription" : "[CONVERSATION] Aucune parole détectée");
                return "";
            }

        } catch (Exception e) {
            log.error("[CONVERSATION] Erreur lors de l'enregistrement", e);
            return "";
        }
    }
}
