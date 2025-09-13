package Producers;

import EventBus.EventQueue;
import EventBus.Events.WakeWordEvent;
import IO.InputHandling.AudioForwarder;
import IO.InputHandling.SpeechToText;
import ai.picovoice.porcupine.Porcupine;
import ai.picovoice.porcupine.PorcupineException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.ByteOrder;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class WakeWordProducer implements Runnable
{
    private Porcupine porcupine;
    private final String[] keywords;
    private final int audioDeviceIndex;
    private final AudioForwarder audioForwarder;
    private final SpeechToText speechToText;
    private TargetDataLine micDataLine;
    private final EventQueue eventQueue;


    @EventListener(ApplicationReadyEvent.class)
    public void startAfterStartup() {
        Thread thread = new Thread(this);
        thread.setDaemon(true);
        thread.start();
    }

    @Autowired
    public WakeWordProducer(EventQueue eventQueue) {
        this.eventQueue = eventQueue;
        String keywordName = "Mon-ami_fr_linux_v3_0_0.ppn";
        String porcupineModelName = "porcupine_params_fr.pv";
        String[] keywordPaths;
        try {
            keywordPaths = new String[]{getKeywordPath("Calcifer.ppn")};
        }
        catch (IllegalArgumentException e)
        {
            keywordPaths = new String[]{getKeywordPath(keywordName)};
        }
        String porcupineModelPath = getPorcupineModelPath(porcupineModelName);

        File keywordFile = new File(keywordPaths[0]);
        if (!keywordFile.exists()) {
            throw new IllegalArgumentException(String.format("Keyword file at '%s' does not exist", keywordPaths[0]));
        }
        this.keywords = keywordPaths;
        this.audioDeviceIndex = 10;          //TODO SELECT THE RIGHT INPUT
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
        // You can either put the model in resources or use an absolute path
        // For resources: ggml-medium.fr.bin ggml-small.bin
        return extractResource("ggml-small.bin");
    }

    /// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// Porcupine initialization

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

    /// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// Audio initialization

    private static TargetDataLine getDefaultCaptureDevice(DataLine.Info dataLineInfo) throws LineUnavailableException {
        if (!AudioSystem.isLineSupported(dataLineInfo)) {
            throw new LineUnavailableException("Default capture device does not support the format required by Picovoice (16kHz, 16-bit, linearly-encoded, single-channel PCM).");
        }
        return (TargetDataLine) AudioSystem.getLine(dataLineInfo);
    }

    private static TargetDataLine getAudioDevice(int deviceIndex, DataLine.Info dataLineInfo) throws LineUnavailableException {
        if (deviceIndex >= 0) {
            try {
                Mixer.Info mixerInfo = AudioSystem.getMixerInfo()[deviceIndex];
                Mixer mixer = AudioSystem.getMixer(mixerInfo);

                if (mixer.isLineSupported(dataLineInfo)) {
                    return (TargetDataLine) mixer.getLine(dataLineInfo);
                } else {
                    log.warn("Audio capture device at index {} does not support the audio format required by Picovoice. Using default capture device.", deviceIndex);
                }
            } catch (Exception e) {
                log.warn("No capture device found at index {}. Using default capture device.", deviceIndex);
            }
        }
        return getDefaultCaptureDevice(dataLineInfo);
    }

    private void initializeAudio() {
        AudioFormat format = new AudioFormat(16000f, 16, 1, true, false);
        DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, format);

        try {
            this.micDataLine = getAudioDevice(this.audioDeviceIndex, dataLineInfo);
            this.micDataLine.open(format);
        } catch (LineUnavailableException e) {
            log.error("Failed to get a valid capture device. Use --show_audio_devices to show available capture devices and their indices", e);
            System.exit(1);
        }
    }

    public String startRecording() {
        try {
            log.info("Starting recording ...");
            this.micDataLine.start();
            log.info("Listening for {}", String.join(", ", this.keywords));

            int frameLength = this.porcupine.getFrameLength();
            ByteBuffer captureBuffer = ByteBuffer.allocate(frameLength * 2);
            captureBuffer.order(ByteOrder.LITTLE_ENDIAN);
            short[] porcupineBuffer = new short[frameLength];

            while (System.in.available() == 0) {
                int numBytesRead = this.micDataLine.read(captureBuffer.array(), 0, captureBuffer.capacity());
                if (numBytesRead != frameLength * 2) {
                    continue;
                }

                captureBuffer.asShortBuffer().get(porcupineBuffer);
                int result = this.porcupine.process(porcupineBuffer);
                if (result >= 0) {
                    log.info("[{}] Detected '{}'",
                            LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                            this.keywords[result]);

                    // Start transcription and wait for the message
                    CompletableFuture<String> messageFuture = this.audioForwarder.startForwarding();

                    // Handle the result asynchronously
                    messageFuture.thenAccept(message -> {
                        if (!message.isEmpty()) {
                            log.info(">>> TRANSCRIBED MESSAGE: {}", message);
                            // Here you can process the transcribed message
                        } else {
                            log.info(">>> No speech detected or transcription failed");
                        }
                    }).exceptionally(throwable -> {
                        log.error("Error during transcription", throwable);
                        return null;
                    });
                    return messageFuture.get();
                }
            }
        } catch (Exception e) {
            log.error("An error occurred during recording", e);
        }
        return "";
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

    @Override
    public void run() {
        while (true) {
            String transcribedMessage = startRecording();
            if (transcribedMessage != null) {
                WakeWordEvent event = new WakeWordEvent(transcribedMessage,"default");
                eventQueue.offer(event);
            }
        }

    }
}