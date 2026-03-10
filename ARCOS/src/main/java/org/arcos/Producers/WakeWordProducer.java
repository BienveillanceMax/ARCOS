package org.arcos.Producers;

import org.arcos.Configuration.AudioProperties;
import org.arcos.EventBus.EventQueue;
import org.arcos.EventBus.Events.Event;
import org.arcos.EventBus.Events.EventPriority;
import org.arcos.EventBus.Events.EventType;
import org.arcos.EventBus.Events.WakeWordEvent;
import org.arcos.IO.InputHandling.JavaSoundMicrophoneSource;
import org.arcos.IO.InputHandling.MicrophoneSource;
import org.arcos.IO.InputHandling.PipeWireMicrophoneSource;
import org.arcos.IO.InputHandling.SpeechToText;
import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.IO.OuputHandling.StateHandler.UXEventType;
import org.arcos.IO.OuputHandling.StateHandler.FeedBackEvent;
import ai.picovoice.porcupine.Porcupine;
import ai.picovoice.porcupine.PorcupineException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    private SpeechToText speechToText;
    private MicrophoneSource micSource;
    private final EventQueue eventQueue;
    private final CentralFeedBackHandler centralFeedBackHandler;
    private final AudioProperties audioProperties;
    private final String fasterWhisperUrl;
    private volatile Thread wakeWordThread;
    private boolean porcupineEnabled = false;
    private boolean porcupineInitialized = false;

    private static final int PORCUPINE_SAMPLE_RATE = 16000;
    private static final int BYTES_PER_SAMPLE = 2;
    private int silenceThreshold;

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
    }

    @Autowired
    public WakeWordProducer(EventQueue eventQueue, @Value("${faster-whisper.url}") String fasterWhisperUrl,
                            CentralFeedBackHandler centralFeedBackHandler, AudioProperties audioProperties) {
        this.centralFeedBackHandler = centralFeedBackHandler;
        this.eventQueue = eventQueue;
        this.audioProperties = audioProperties;
        this.fasterWhisperUrl = fasterWhisperUrl;
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
                this.speechToText = new SpeechToText(fasterWhisperUrl);
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
                    log.error("Audio source returned -1 (stream ended). Stopping wake word detection.");
                    break;
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

    private void downsample(short[] input, int inputLength, short[] output, int outputLength) {
        double ratio = (double) inputLength / outputLength;
        for (int i = 0; i < outputLength; i++) {
            int index = (int) (i * ratio);
            if (index < inputLength) {
                output[i] = input[index];
            }
        }
    }

    private String recordAndTranscribe() {
        log.info("Started listening for speech with Faster Whisper...");

        speechToText.reset();

        final int micSampleRate = micSource.getSampleRate();
        final boolean needsResample = micSampleRate != PORCUPINE_SAMPLE_RATE;
        // Whisper frame: 50ms at 16kHz = 1600 bytes
        final int whisperFrameSize = PORCUPINE_SAMPLE_RATE * BYTES_PER_SAMPLE / 20;
        final int micFrameSize = needsResample
                ? (int) Math.ceil(whisperFrameSize * micSampleRate / (double) PORCUPINE_SAMPLE_RATE)
                : whisperFrameSize;

        byte[] micBuffer = new byte[micFrameSize];
        byte[] whisperBuffer = new byte[whisperFrameSize];

        long lastSoundTime = System.currentTimeMillis();
        long recordingStartTime = System.currentTimeMillis();
        boolean hasDetectedSpeech = false;

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

                    // Check for silence
                    boolean isSilent = isSilence(whisperBuffer);

                    if (!isSilent) {
                        lastSoundTime = System.currentTimeMillis();
                        if (!hasDetectedSpeech) {
                            hasDetectedSpeech = true;
                            log.info("Speech detected, recording...");
                        }
                    }

                    // Buffer audio for Whisper
                    speechToText.processAudio(whisperBuffer);

                    // Check if we should stop due to silence
                    if (hasDetectedSpeech && isSilent) {
                        long silenceDuration = System.currentTimeMillis() - lastSoundTime;
                        long silenceDurationMs = audioProperties.getSilenceDurationMs();
                        if (silenceDuration >= silenceDurationMs) {
                            log.info("Detected {}ms of silence, processing transcription...", silenceDurationMs);
                            break;
                        }
                    }

                    // Safety timeout
                    long totalRecordingTime = System.currentTimeMillis() - recordingStartTime;
                    long maxRecordingMs = (long) audioProperties.getMaxRecordingSeconds() * 1000;
                    if (totalRecordingTime > maxRecordingMs) {
                        log.info("Maximum recording time reached ({}s), processing transcription...",
                                audioProperties.getMaxRecordingSeconds());
                        break;
                    }
                }
            }

            // Process transcription
            if (speechToText.hasMinimumAudio()) {
                log.info("Processing {}ms of audio...", speechToText.getBufferedAudioDurationMs());
                return speechToText.getTranscription();
            } else {
                log.info("Not enough audio data for transcription");
                return "";
            }

        } catch (Exception e) {
            log.error("Error during transcription recording", e);
            return "";
        }
    }

    private boolean isSilence(byte[] audioData) {
        long sum = 0;
        int sampleCount = audioData.length / 2;

        for (int i = 0; i < audioData.length - 1; i += 2) {
            short sample = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));
            sum += sample * sample;
        }

        double rms = Math.sqrt((double) sum / sampleCount);
        return rms < silenceThreshold;
    }

    /**
     * Ouvre une fenêtre d'écoute en mode conversation (sans mot de réveil).
     * Appelée par l'Orchestrator après fin TTS si les conditions sont remplies.
     * Thread-safe : les champs volatile garantissent la visibilité cross-thread.
     *
     * @param durationMs Durée de la fenêtre en ms
     */
    public void openConversationWindow(int durationMs) {
        if (!porcupineEnabled) {
            log.debug("openConversationWindow ignorée : Porcupine non actif");
            return;
        }
        inConversationWindowMode = true;
        conversationWindowExpiry = System.currentTimeMillis() + durationMs;
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

        speechToText.reset();

        final int micSampleRate = micSource.getSampleRate();
        final boolean needsResample = micSampleRate != PORCUPINE_SAMPLE_RATE;
        final int whisperFrameSize = PORCUPINE_SAMPLE_RATE * BYTES_PER_SAMPLE / 20;
        final int micFrameSize = needsResample
                ? (int) Math.ceil(whisperFrameSize * micSampleRate / (double) PORCUPINE_SAMPLE_RATE)
                : whisperFrameSize;

        byte[] micBuffer = new byte[micFrameSize];
        byte[] whisperBuffer = new byte[whisperFrameSize];

        long lastSoundTime = System.currentTimeMillis();
        long recordingStartTime = System.currentTimeMillis();
        boolean hasDetectedSpeech = false;

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

                    boolean isSilent = isSilence(whisperBuffer);

                    if (!isSilent) {
                        lastSoundTime = System.currentTimeMillis();
                        if (!hasDetectedSpeech) {
                            hasDetectedSpeech = true;
                            log.info("[CONVERSATION] Parole détectée, enregistrement...");
                        }
                    }

                    speechToText.processAudio(whisperBuffer);

                    if (hasDetectedSpeech && isSilent) {
                        long silenceDuration = System.currentTimeMillis() - lastSoundTime;
                        if (silenceDuration >= audioProperties.getConversationSilenceMs()) {
                            log.info("[CONVERSATION] Silence de {}ms, traitement...", audioProperties.getConversationSilenceMs());
                            break;
                        }
                    }

                    long elapsed = System.currentTimeMillis() - recordingStartTime;
                    if (elapsed >= maxDurationMs) {
                        log.info("[CONVERSATION] Fenêtre de {}ms expirée", maxDurationMs);
                        break;
                    }
                }
            }

            if (speechToText.hasMinimumAudio()) {
                log.info("[CONVERSATION] Traitement de {}ms d'audio...", speechToText.getBufferedAudioDurationMs());
                return speechToText.getTranscription();
            } else {
                log.info("[CONVERSATION] Pas assez d'audio pour la transcription");
                return "";
            }

        } catch (Exception e) {
            log.error("[CONVERSATION] Erreur lors de l'enregistrement", e);
            return "";
        }
    }
}
