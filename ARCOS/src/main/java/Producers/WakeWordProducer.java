package Producers;

import EventBus.EventQueue;
import EventBus.Events.WakeWordEvent;
import IO.InputHandling.SpeechToText;
import ai.picovoice.porcupine.Porcupine;
import ai.picovoice.porcupine.PorcupineException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sound.sampled.*;
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
    private final String[] keywords;
    private final int audioDeviceIndex;
    private final SpeechToText speechToText;
    private TargetDataLine micDataLine;
    private final EventQueue eventQueue;

    private static final int MIC_SAMPLE_RATE = 44100;
    private static final int PORCUPINE_SAMPLE_RATE = 16000;
    private static final int BYTES_PER_SAMPLE = 2;

    // Silence detection parameters
    private static final int SILENCE_THRESHOLD = 1000;
    private static final long SILENCE_DURATION_MS = 2000;

    @EventListener(ApplicationReadyEvent.class)
    public void startAfterStartup() {
        if (this.micDataLine != null) {
            Thread thread = new Thread(this);
            thread.setDaemon(true);
            thread.start();
        } else {
            log.warn("No audio input device found or configured. Wake word detection thread will not start.");
        }
    }

    @Autowired
    public WakeWordProducer(EventQueue eventQueue, @Value("${faster-whisper.url}") String fasterWhisperUrl) {
        log.info("Initializing WakeWordProducer");
        this.eventQueue = eventQueue;
        String keywordName = "Mon-ami_fr_linux_v3_0_0.ppn";
        String porcupineModelName = "porcupine_params_fr.pv";
        String[] keywordPaths;

        try {
            keywordPaths = new String[]{getKeywordPath("Calcifer.ppn")};
        } catch (IllegalArgumentException e) {
            keywordPaths = new String[]{getKeywordPath(keywordName)};
        }
        String porcupineModelPath = getPorcupineModelPath(porcupineModelName);

        File keywordFile = new File(keywordPaths[0]);
        if (!keywordFile.exists()) {
            throw new IllegalArgumentException(String.format("Keyword file at '%s' does not exist", keywordPaths[0]));
        }
        this.keywords = keywordPaths;
        this.audioDeviceIndex = 10; // TODO SELECT THE RIGHT INPUT
        initializePorcupine(keywordPaths, porcupineModelPath);
        initializeAudio();
        if (this.micDataLine != null) {
            this.speechToText = new SpeechToText(fasterWhisperUrl);
        } else {
            this.speechToText = null;
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

    private void initializeAudio() {
        AudioFormat sourceFormat = new AudioFormat(MIC_SAMPLE_RATE, 16, 1, true, false);
        DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, sourceFormat);

        this.micDataLine = getAudioDevice(this.audioDeviceIndex, dataLineInfo);

        if (this.micDataLine != null) {
            try {
                this.micDataLine.open(sourceFormat);
                this.micDataLine.start();
                log.info("Successfully initialized audio device.");
            } catch (LineUnavailableException e) {
                log.error("Audio device is unavailable. Wake word detection will be disabled.", e);
                this.micDataLine = null;
            }
        }
    }

    private static TargetDataLine getAudioDevice(int deviceIndex, DataLine.Info dataLineInfo) {
        if (deviceIndex >= 0) {
            try {
                Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
                if (deviceIndex < mixerInfos.length) {
                    Mixer.Info mixerInfo = mixerInfos[deviceIndex];
                    Mixer mixer = AudioSystem.getMixer(mixerInfo);
                    if (mixer.isLineSupported(dataLineInfo)) {
                        return (TargetDataLine) mixer.getLine(dataLineInfo);
                    } else {
                        log.warn("Audio device at index {} does not support format. Using default.", deviceIndex);
                    }
                } else {
                    log.warn("Audio device index {} is out of bounds. Using default.", deviceIndex);
                }
            } catch (Exception e) {
                log.warn("Could not get audio device at index {}. Using default.", deviceIndex, e);
            }
        }
        try {
            return (TargetDataLine) AudioSystem.getLine(dataLineInfo);
        } catch (LineUnavailableException | IllegalArgumentException e) {
            log.warn("Could not get default audio capture device. Wake word detection will be disabled.");
            return null;
        }
    }

    @Override
    public void run() {
        final int porcupineFrameLength = porcupine.getFrameLength();
        final int micFrameSize = (int) Math.ceil(porcupineFrameLength * MIC_SAMPLE_RATE / (double) PORCUPINE_SAMPLE_RATE) * BYTES_PER_SAMPLE;

        byte[] micBuffer = new byte[micFrameSize];
        short[] porcupineBuffer = new short[porcupineFrameLength];
        short[] resampledBuffer = new short[porcupineFrameLength];

        log.info("Starting wake word detection loop...");

        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Read audio from microphone
                int bytesRead = micDataLine.read(micBuffer, 0, micFrameSize);

                if (bytesRead > 0) {
                    // Convert bytes to shorts and downsample to 16kHz
                    int samplesRead = bytesRead / BYTES_PER_SAMPLE;
                    short[] micSamples = new short[samplesRead];

                    ByteBuffer.wrap(micBuffer, 0, bytesRead)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .asShortBuffer()
                            .get(micSamples);

                    // Simple downsampling
                    downsample(micSamples, samplesRead, resampledBuffer, porcupineFrameLength);

                    // Check for wake word
                    int result = porcupine.process(resampledBuffer);

                    if (result >= 0) {
                        log.info("[{}] Detected '{}'",
                                LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                                keywords[result]);

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

        // Read audio at 16kHz for Whisper (downsample on the fly)
        final int whisperFrameSize = PORCUPINE_SAMPLE_RATE * BYTES_PER_SAMPLE / 20; // 50ms frames
        final int micFrameSize = (int) Math.ceil(whisperFrameSize * MIC_SAMPLE_RATE / (double) PORCUPINE_SAMPLE_RATE);

        byte[] micBuffer = new byte[micFrameSize];
        byte[] whisperBuffer = new byte[whisperFrameSize];

        long lastSoundTime = System.currentTimeMillis();
        long recordingStartTime = System.currentTimeMillis();
        boolean hasDetectedSpeech = false;

        try {
            while (true) {
                int bytesRead = micDataLine.read(micBuffer, 0, micFrameSize);

                if (bytesRead > 0) {
                    // Convert to 16-bit samples
                    int samplesRead = bytesRead / BYTES_PER_SAMPLE;
                    short[] micSamples = new short[samplesRead];
                    ByteBuffer.wrap(micBuffer, 0, bytesRead)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .asShortBuffer()
                            .get(micSamples);

                    // Downsample to 16kHz
                    int whisperSamples = whisperFrameSize / BYTES_PER_SAMPLE;
                    short[] downsampled = new short[whisperSamples];
                    downsample(micSamples, samplesRead, downsampled, whisperSamples);

                    // Convert back to bytes
                    ByteBuffer bb = ByteBuffer.wrap(whisperBuffer).order(ByteOrder.LITTLE_ENDIAN);
                    for (short s : downsampled) {
                        bb.putShort(s);
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
                        if (silenceDuration >= SILENCE_DURATION_MS) {
                            log.info("Detected {}ms of silence, processing transcription...", SILENCE_DURATION_MS);
                            break;
                        }
                    }

                    // Safety timeout
                    long totalRecordingTime = System.currentTimeMillis() - recordingStartTime;
                    if (totalRecordingTime > 30000) {
                        log.info("Maximum recording time reached (30s), processing transcription...");
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
        return rms < SILENCE_THRESHOLD;
    }

    public static void showAudioDevices() {
        Mixer.Info[] allMixerInfo = AudioSystem.getMixerInfo();
        Line.Info captureLine = new Line.Info(TargetDataLine.class);
        for (int i = 0; i < allMixerInfo.length; i++) {
            Mixer mixer = AudioSystem.getMixer(allMixerInfo[i]);
            if (mixer.isLineSupported(captureLine)) {
                log.info("Device {}: {}", i, allMixerInfo[i].getName());
            }
        }
    }
}
