package Producers;

import EventBus.EventQueue;
import EventBus.Events.WakeWordEvent;
import IO.InputHandling.AudioForwarder;
import IO.InputHandling.SpeechToText;
import ai.picovoice.porcupine.Porcupine;
import ai.picovoice.porcupine.PorcupineException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sound.sampled.*;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

@Component
public class WakeWordProducer implements Runnable
{
    private Porcupine porcupine;
    private final String[] keywords;
    private final int audioDeviceIndex;
    private final AudioForwarder audioForwarder;
    private final SpeechToText speechToText;
    private TargetDataLine micDataLine;
    private final EventQueue eventQueue;

    @Autowired
    public WakeWordProducer(EventQueue eventQueue) {
        this.eventQueue = eventQueue;
        String keywordName = "Mon-ami_fr_linux_v3_0_0.ppn";
        String porcupineModelName = "porcupine_params_fr.pv";
        String[] keywordPaths = new String[]{getKeywordPath(keywordName)};
        String porcupineModelPath = getPorcupineModelPath(porcupineModelName);

        File keywordFile = new File(keywordPaths[0]);
        if (!keywordFile.exists()) {
            throw new IllegalArgumentException(String.format("Keyword file at '%s' does not exist", keywordPaths[0]));
        }
        this.keywords = keywordPaths;
        this.audioDeviceIndex = 7;          //TODO SELECT THE RIGHT INPUT
        initializePorcupine(keywordPaths, porcupineModelPath);
        initializeAudio();
        this.speechToText = new SpeechToText(getWhisperModelPath());
        this.audioForwarder = new AudioForwarder(this.micDataLine, this.speechToText);
    }

    private String getWhisperModelPath() {
        // You can either put the model in resources or use an absolute path
        // For resources: ggml-medium.fr.bin ggml-small.bin
        URL url = getClass().getClassLoader().getResource("ggml-small.bin");
        //URL url = getClass().getClassLoader().getResource("ggml-medium.fr.bin");


        File modelFile = null;
        try {
            modelFile = new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return modelFile.getAbsolutePath();
    }

    /// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// Porcupine initialization

    private String getKeywordPath(String keyword) {
        URL url = getClass().getClassLoader().getResource(keyword);
        if (url == null) {
            throw new IllegalArgumentException("Keyword file not found in resources.");
        }
        File keywordFile = null;
        try {
            keywordFile = new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return keywordFile.getAbsolutePath();
    }

    private String getPorcupineModelPath(String model) {
        URL url = getClass().getClassLoader().getResource(model);
        if (url == null) {
            throw new IllegalArgumentException("Model file not found in resources.");
        }
        File modelFile = null;
        try {
            modelFile = new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return modelFile.getAbsolutePath();
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
                    System.err.printf("Audio capture device at index %s does not support the audio format required by Picovoice. Using default capture device.%n", deviceIndex);
                }
            } catch (Exception e) {
                System.err.printf("No capture device found at index %s. Using default capture device.%n", deviceIndex);
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
            System.err.println("Failed to get a valid capture device. Use --show_audio_devices to show available capture devices and their indices");
            System.exit(1);
        }
    }

    public String startRecording() {
        try {
            System.out.println("Starting recording ...");
            this.micDataLine.start();
            System.out.print("Listening for {");
            for (String keyword : this.keywords) {
                System.out.println(keyword);
            }
            System.out.print(" }\n");

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
                    System.out.printf("[%s] Detected '%s'\n",
                            LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                            this.keywords[result]);

                    // Start transcription and wait for the message
                    CompletableFuture<String> messageFuture = this.audioForwarder.startForwarding();

                    // Handle the result asynchronously
                    messageFuture.thenAccept(message -> {
                        if (!message.isEmpty()) {
                            System.out.println(">>> TRANSCRIBED MESSAGE: " + message);
                            // Here you can process the transcribed message
                        } else {
                            System.out.println(">>> No speech detected or transcription failed");
                        }
                    }).exceptionally(throwable -> {
                        System.err.println("Error during transcription: " + throwable.getMessage());
                        return null;
                    });
                    return messageFuture.get();
                }
            }
        } catch (Exception e) {
            System.err.println(e.toString());
        }
        return "";
    }

    public static void showAudioDevices() {
        Mixer.Info[] allMixerInfo = AudioSystem.getMixerInfo();
        Line.Info captureLine = new Line.Info(TargetDataLine.class);

        for (int i = 0; i < allMixerInfo.length; i++) {
            Mixer mixer = AudioSystem.getMixer(allMixerInfo[i]);
            if (mixer.isLineSupported(captureLine)) {
                System.out.printf("Device %d: %s\n", i, allMixerInfo[i].getName());
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