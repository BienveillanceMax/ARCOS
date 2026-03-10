package org.arcos.IO.InputHandling;

import lombok.extern.slf4j.Slf4j;

import javax.sound.sampled.*;

/**
 * Captures audio via Java Sound API (TargetDataLine).
 * Fallback for systems without PipeWire (e.g. Raspberry Pi with bare ALSA).
 */
@Slf4j
public class JavaSoundMicrophoneSource implements MicrophoneSource {

    private TargetDataLine line;
    private final String deviceDescription;

    public JavaSoundMicrophoneSource(int sampleRate, int deviceIndex) {
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, format);

        this.line = openDevice(deviceIndex, dataLineInfo, format);
        this.deviceDescription = line != null
                ? "Java Sound (" + sampleRate + "Hz, index " + deviceIndex + ")"
                : "Java Sound (unavailable)";
    }

    private TargetDataLine openDevice(int deviceIndex, DataLine.Info dataLineInfo, AudioFormat format) {
        TargetDataLine targetLine = getLine(deviceIndex, dataLineInfo);
        if (targetLine == null) return null;

        try {
            targetLine.open(format);
            targetLine.start();
            log.info("Java Sound audio device opened: {}", targetLine.getFormat());
            return targetLine;
        } catch (LineUnavailableException e) {
            log.error("Audio device unavailable: {}", e.getMessage());
            return null;
        }
    }

    private static TargetDataLine getLine(int deviceIndex, DataLine.Info dataLineInfo) {
        if (deviceIndex >= 0) {
            try {
                Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
                if (deviceIndex < mixerInfos.length) {
                    Mixer mixer = AudioSystem.getMixer(mixerInfos[deviceIndex]);
                    if (mixer.isLineSupported(dataLineInfo)) {
                        log.info("Using audio device: {} (index {})", mixerInfos[deviceIndex].getName(), deviceIndex);
                        return (TargetDataLine) mixer.getLine(dataLineInfo);
                    }
                }
                log.warn("Device index {} not usable, falling back to default", deviceIndex);
            } catch (Exception e) {
                log.warn("Could not get device at index {}: {}", deviceIndex, e.getMessage());
            }
        }
        try {
            TargetDataLine defaultLine = (TargetDataLine) AudioSystem.getLine(dataLineInfo);
            log.info("Using default audio device");
            return defaultLine;
        } catch (LineUnavailableException | IllegalArgumentException e) {
            log.warn("No audio capture device available");
            return null;
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int length) {
        if (line == null) return -1;
        return line.read(buffer, offset, length);
    }

    @Override
    public void close() {
        if (line != null) {
            line.stop();
            line.close();
            log.info("Java Sound audio device closed");
        }
    }

    @Override
    public boolean isAvailable() {
        return line != null;
    }

    @Override
    public String describe() {
        return deviceDescription;
    }
}
