package EventLoop;

import EventLoop.InputHandling.SpeechToText;
import EventLoop.InputHandling.WakeWordDetector;
import EventLoop.OuputHandling.TTSHandler;
import com.arcos.bus.EventBus;
import com.arcos.events.WakeWordDetectedEvent;

import javax.sound.sampled.*;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

public class EventLoopRunner {

    private final WakeWordDetector wakeWordDetector;
    private final SpeechToText speechToText;
    private final TTSHandler ttsHandler;
    private final EventBus eventBus;
    private TargetDataLine micDataLine;

    public EventLoopRunner() {
        initializeAudio();
        this.wakeWordDetector = new WakeWordDetector(this.micDataLine);
        this.speechToText = new SpeechToText(getWhisperModelPath(), this.micDataLine);
        this.ttsHandler = new TTSHandler();
        this.eventBus = EventBus.getInstance();
    }

    public void run() {
        // Subscribe SpeechToText to WakeWordDetectedEvent
        eventBus.subscribe(WakeWordDetectedEvent.class, event -> speechToText.listenAndTranscribe());

        // Start the event bus
        eventBus.start();

        // Start the wake word detector thread
        Thread wakeWordDetectorThread = new Thread(wakeWordDetector);
        wakeWordDetectorThread.setDaemon(false); // This will keep the application alive
        wakeWordDetectorThread.start();

        // Start the TTS handler
        ttsHandler.initialize();

        // Start listening for the wake word
        wakeWordDetector.startListening();

        System.out.println("Event loop started.");
    }

    private void initializeAudio() {
        AudioFormat format = new AudioFormat(16000f, 16, 1, true, false);
        DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, format);

        try {
            // TODO: select the right audio device index
            this.micDataLine = getAudioDevice(-1, dataLineInfo); // -1 for default
            this.micDataLine.open(format);
            this.micDataLine.start();
        } catch (LineUnavailableException e) {
            System.err.println("Failed to get a valid capture device. Use --show_audio_devices to show available capture devices and their indices");
            System.exit(1);
        }
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

    private static TargetDataLine getDefaultCaptureDevice(DataLine.Info dataLineInfo) throws LineUnavailableException {
        if (!AudioSystem.isLineSupported(dataLineInfo)) {
            throw new LineUnavailableException("Default capture device does not support the format required by Picovoice (16kHz, 16-bit, linearly-encoded, single-channel PCM).");
        }
        return (TargetDataLine) AudioSystem.getLine(dataLineInfo);
    }

    private String getWhisperModelPath() {
        URL url = getClass().getClassLoader().getResource("ggml-small.bin");
        if (url == null) {
            throw new RuntimeException("Whisper model not found in resources.");
        }
        File modelFile;
        try {
            modelFile = new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return modelFile.getAbsolutePath();
    }
}
