package EventLoop.InputHandling;

import ai.picovoice.porcupine.Porcupine;
import ai.picovoice.porcupine.PorcupineException;
import com.arcos.bus.EventBus;
import com.arcos.events.TranscriptionFinishedEvent;
import com.arcos.events.WakeWordDetectedEvent;

import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.TargetDataLine;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class WakeWordDetector implements Runnable {
    private Porcupine porcupine;
    private final String[] keywords;
    private final TargetDataLine micDataLine;
    private final EventBus eventBus;
    private volatile boolean isListening = false;

    public WakeWordDetector(TargetDataLine micDataLine) {
        this.eventBus = EventBus.getInstance();
        this.micDataLine = micDataLine;

        String keywordName = "Mon-ami_fr_linux_v3_0_0.ppn";
        String porcupineModelName = "porcupine_params_fr.pv";
        String[] keywordPaths = new String[]{getKeywordPath(keywordName)};
        String porcupineModelPath = getPorcupineModelPath(porcupineModelName);

        File keywordFile = new File(keywordPaths[0]);
        if (!keywordFile.exists()) {
            throw new IllegalArgumentException(String.format("Keyword file at '%s' does not exist", keywordPaths[0]));
        }
        this.keywords = new String[]{keywordFile.getName().split("_")[0]};

        initializePorcupine(keywordPaths, porcupineModelPath);

        eventBus.subscribe(TranscriptionFinishedEvent.class, event -> startListening());
    }

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

    public void startListening() {
        this.isListening = true;
        System.out.println("WakeWordDetector started listening.");
    }

    public void stopListening() {
        this.isListening = false;
        System.out.println("WakeWordDetector stopped listening.");
    }

    @Override
    public void run() {
        int frameLength = this.porcupine.getFrameLength();
        ByteBuffer captureBuffer = ByteBuffer.allocate(frameLength * 2);
        captureBuffer.order(ByteOrder.LITTLE_ENDIAN);
        short[] porcupineBuffer = new short[frameLength];

        while (!Thread.currentThread().isInterrupted()) {
            if (isListening) {
                try {
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
                        stopListening();
                        eventBus.publish(new WakeWordDetectedEvent());
                    }
                } catch (Exception e) {
                    System.err.println("Error while listening for wake word: " + e.getMessage());
                    stopListening(); // Stop listening on error
                }
            } else {
                try {
                    // wait until we are supposed to listen again
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        // Cleanup
        if (porcupine != null) {
            porcupine.delete();
        }
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
}