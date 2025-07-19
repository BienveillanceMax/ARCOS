package EventLoop;

import org.springframework.stereotype.Component;
import ai.picovoice.porcupine.*;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Component
public class EventLoopRunner
{

    AudioInputStream audioInputStream;
    Porcupine porcupine;
    String[] keywords;
    int audioDeviceIndex;           //should be set manually


    public EventLoopRunner() {
        String keywordName = "Mon-ami_fr_linux_v3_0_0.ppn";
        String modelName = "porcupine_params_fr.pv";
        String[] keywordPaths = new String[]{getKeywordPath(keywordName)}; //loads only one keyword as of now
        String modelPath = getModelPath(modelName);

        File keywordFile = new File(keywordPaths[0]);
        if (!keywordFile.exists()) {
            throw new IllegalArgumentException(String.format("Keyword file at '%s' " +
                    "does not exist", keywordPaths[0]));
        }
        this.keywords = keywordPaths;
        this.audioDeviceIndex = 7;                      //magic number : to update, but it makes sense to keep it hardcoded for said usage
        initializePorcupine(keywordPaths,modelPath);
        startRecording();
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

    private String getModelPath(String model) {
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
        System.out.println(modelFile.getAbsoluteFile());
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

    private static TargetDataLine getAudioDevice(int deviceIndex, DataLine.Info dataLineInfo)
            throws LineUnavailableException {

        if (deviceIndex >= 0) {
            try {
                Mixer.Info mixerInfo = AudioSystem.getMixerInfo()[deviceIndex];
                Mixer mixer = AudioSystem.getMixer(mixerInfo);

                if (mixer.isLineSupported(dataLineInfo)) {
                    return (TargetDataLine) mixer.getLine(dataLineInfo);
                } else {
                    System.err.printf("Audio capture device at index %s does not support the " +
                                    "audio format required by Picovoice. Using default capture device.",
                            deviceIndex);
                }
            } catch (Exception e) {
                System.err.printf(
                        "No capture device found at index %s. Using default capture device.",
                        deviceIndex);
            }
        }
        // use default capture device if we couldn't get the one requested
        return getDefaultCaptureDevice(dataLineInfo);
    }


    private static TargetDataLine getDefaultCaptureDevice(DataLine.Info dataLineInfo)
            throws LineUnavailableException {

        if (!AudioSystem.isLineSupported(dataLineInfo)) {
            throw new LineUnavailableException(
                    "Default capture device does not support the format required by Picovoice (16kHz, 16-bit, linearly-encoded, single-channel PCM).");
        }

        return (TargetDataLine) AudioSystem.getLine(dataLineInfo);
    }

    private void startRecording() {


        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); //for communication purpose
        AudioFormat format = new AudioFormat(16000f, 16, 1, true, false);
        DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, format);
        TargetDataLine micDataLine;
        try {
            micDataLine = getAudioDevice(audioDeviceIndex, dataLineInfo);
            micDataLine.open(format);
        } catch (LineUnavailableException e) {
            System.err.println(
                    "Failed to get a valid capture device. Use --show_audio_devices to " +
                            "show available capture devices and their indices");
            System.exit(1);
            return;
        }


        try {
            micDataLine.start();
            System.out.print("Listening for {");
            for (String keyword : keywords) {
                System.out.println(keyword);
            }
            System.out.print(" }\n");

            // buffers for processing audio
            int frameLength = porcupine.getFrameLength();
            ByteBuffer captureBuffer = ByteBuffer.allocate(frameLength * 2);
            captureBuffer.order(ByteOrder.LITTLE_ENDIAN);
            short[] porcupineBuffer = new short[frameLength];

            int numBytesRead;
            long totalBytesCaptured = 0;

            while (System.in.available() == 0) {


                // read a buffer of audio
                numBytesRead = micDataLine.read(captureBuffer.array(), 0, captureBuffer.capacity());
                totalBytesCaptured += numBytesRead;

                // write to output if we're recording TODO : transmit audio
                if (outputStream != null) {
                    outputStream.write(captureBuffer.array(), 0, numBytesRead);
                }

                // don't pass to porcupine if we don't have a full buffer
                if (numBytesRead != frameLength * 2) {
                    continue;
                }

                // copy into 16-bit buffer
                captureBuffer.asShortBuffer().get(porcupineBuffer);

                // process with porcupine
                int result = porcupine.process(porcupineBuffer);
                if (result >= 0) {
                    System.out.printf("[%s] Detected '%s'\n",
                            LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                            keywords[result]);
                }
            }
        } catch (Exception e) {
            System.err.println(e.toString());
        }

    }



    /* Helper Function */
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

    public void EventLoop() {


    }
}
