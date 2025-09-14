package Producers;

import EventBus.EventQueue;
import EventBus.Events.WakeWordEvent;
import IO.InputHandling.AudioForwarder;
import IO.InputHandling.SpeechToText;
import ai.picovoice.porcupine.Porcupine;
import ai.picovoice.porcupine.PorcupineException;
import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.jvm.JVMAudioInputStream;
import be.tarsos.dsp.resample.RateTransposer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class WakeWordProducer implements Runnable {

    private Porcupine porcupine;
    private final String[] keywords;
    private final int audioDeviceIndex;
    private final AudioForwarder audioForwarder;
    private final SpeechToText speechToText;
    private TargetDataLine micDataLine;
    private final EventQueue eventQueue;

    private static final float SOURCE_SAMPLE_RATE = 20000f;
    private static final int TARGET_SAMPLE_RATE = 16000;

    @EventListener(ApplicationReadyEvent.class)
    public void startAfterStartup() {
        Thread thread = new Thread(this);
        thread.setDaemon(true);
        thread.start();
    }

    @Autowired
    public WakeWordProducer(EventQueue eventQueue) {
        log.info("Initializing WakeWordProducer with resampling logic.");
        this.eventQueue = eventQueue;
        String keywordName = "Mon-ami_fr_linux_v3_0_0.ppn";
        String porcupineModelName = "porcupine_params_fr.pv";
        String[] keywordPaths;
        try {
            keywordPaths = new String[]{getKeywordPath("Calcifer.ppn")};
        } catch (Exception e) {
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
        this.speechToText = new SpeechToText(getWhisperModelPath());
        this.audioForwarder = new AudioForwarder(this.micDataLine, this.speechToText);
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

    private String getWhisperModelPath() {
        return extractResource("ggml-small.bin");
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
        // Open the microphone with its native sample rate
        AudioFormat sourceFormat = new AudioFormat(SOURCE_SAMPLE_RATE, 16, 1, true, false);
        DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, sourceFormat);

        try {
            this.micDataLine = getAudioDevice(this.audioDeviceIndex, dataLineInfo);
            this.micDataLine.open(sourceFormat);
            this.micDataLine.start();
        } catch (LineUnavailableException e) {
            log.error("Failed to get a valid capture device. Use --show_audio_devices to show available capture devices and their indices", e);
            System.exit(1);
        }
    }

    private static TargetDataLine getAudioDevice(int deviceIndex, DataLine.Info dataLineInfo) {
        if (deviceIndex >= 0) {
            try {
                Mixer.Info mixerInfo = AudioSystem.getMixerInfo()[deviceIndex];
                Mixer mixer = AudioSystem.getMixer(mixerInfo);
                if (mixer.isLineSupported(dataLineInfo)) {
                    return (TargetDataLine) mixer.getLine(dataLineInfo);
                } else {
                    log.warn("Audio capture device at index {} does not support the requested audio format. Using default.", deviceIndex);
                }
            } catch (Exception e) {
                log.warn("Could not get audio device at index {}. Using default.", deviceIndex);
            }
        }
        try {
            return (TargetDataLine) AudioSystem.getLine(dataLineInfo);
        } catch (LineUnavailableException e) {
            throw new RuntimeException("Default audio capture device does not support the requested audio format.", e);
        }
    }

    @Override
    public void run() {
        final int porcupineFrameLength = porcupine.getFrameLength();
        final double resampleFactor = (double) TARGET_SAMPLE_RATE / (double) SOURCE_SAMPLE_RATE;
        final int sourceBufferSize = (int) Math.ceil(porcupineFrameLength / resampleFactor);

        while (!Thread.currentThread().isInterrupted()) {
            final CompletableFuture<Void> detectionCompleter = new CompletableFuture<>();
            final AudioDispatcher dispatcher = new AudioDispatcher(new JVMAudioInputStream(new AudioInputStream(micDataLine)), sourceBufferSize, 0);

            RateTransposer rateTransposer = new RateTransposer(resampleFactor);
            dispatcher.addAudioProcessor(rateTransposer);
            dispatcher.addAudioProcessor(new AudioProcessor() {
                private final short[] porcupineBuffer = new short[porcupineFrameLength];

                @Override
                public boolean process(AudioEvent audioEvent) {
                    if(dispatcher.isStopped()){
                        return false;
                    }

                    float[] floatAudioBuffer = audioEvent.getFloatBuffer();
                    for (int i = 0; i < porcupineBuffer.length && i < floatAudioBuffer.length; i++) {
                        porcupineBuffer[i] = (short) (floatAudioBuffer[i] * 32767.0f);
                    }

                    try {
                        int result = porcupine.process(porcupineBuffer);
                        if (result >= 0) {
                            log.info("[{}] Detected '{}'",
                                    LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                                    keywords[result]);
                            dispatcher.stop();

                            CompletableFuture<String> messageFuture = audioForwarder.startForwarding();
                            messageFuture.whenComplete((message, throwable) -> {
                                if (throwable != null) {
                                    log.error("Error during transcription", throwable);
                                } else {
                                    if (message != null && !message.isEmpty()) {
                                        log.info(">>> TRANSCRIBED MESSAGE: {}", message);
                                        WakeWordEvent event = new WakeWordEvent(message, "default");
                                        eventQueue.offer(event);
                                    } else {
                                        log.info(">>> No speech detected or transcription failed");
                                    }
                                }
                                detectionCompleter.complete(null);
                            });
                        }
                    } catch (PorcupineException e) {
                        log.error("Error processing audio with Porcupine", e);
                    }
                    return true;
                }

                @Override
                public void processingFinished() {}
            });

            log.info("Starting audio processing pipeline... Listening for wake word.");
            dispatcher.run();

            try {
                detectionCompleter.get();
            } catch (Exception e) {
                log.error("Error waiting for transcription to complete.", e);
                Thread.currentThread().interrupt();
            }
        }
        log.info("WakeWordProducer thread finished.");
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