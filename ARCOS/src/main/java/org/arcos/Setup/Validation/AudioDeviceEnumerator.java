package org.arcos.Setup.Validation;

import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

/**
 * Énumère les périphériques audio d'entrée (microphones) compatibles avec ARCOS.
 * Compatibles = supportent TargetDataLine (16-bit, mono).
 * Aucune dépendance Spring.
 */
public class AudioDeviceEnumerator {

    private AudioDeviceEnumerator() {}

    /**
     * Représente un périphérique audio d'entrée détecté.
     */
    public record AudioDevice(int index, String name, String description) {
        @Override
        public String toString() {
            return String.format("[%d] %s", index, name);
        }
    }

    /**
     * Retourne la liste des périphériques audio d'entrée compatibles.
     *
     * @return liste non-null, possiblement vide si aucun microphone disponible
     */
    public static List<AudioDevice> getInputDevices() {
        List<AudioDevice> devices = new ArrayList<>();
        Line.Info captureLine = new Line.Info(TargetDataLine.class);
        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();

        for (int i = 0; i < mixerInfos.length; i++) {
            Mixer.Info info = mixerInfos[i];
            try {
                Mixer mixer = AudioSystem.getMixer(info);
                if (mixer.isLineSupported(captureLine)) {
                    devices.add(new AudioDevice(i, info.getName(), info.getDescription()));
                }
            } catch (Exception ignored) {
                // Certains mixers peuvent jeter des exceptions lors de l'inspection
            }
        }
        return devices;
    }

    /**
     * Retourne le périphérique à l'index donné, ou null si introuvable.
     */
    public static AudioDevice getDeviceAt(int index) {
        return getInputDevices().stream()
                .filter(d -> d.index() == index)
                .findFirst()
                .orElse(null);
    }

    /**
     * Vérifie que le périphérique à l'index donné supporte le format audio ARCOS
     * (16-bit mono, fréquence d'échantillonnage donnée).
     */
    public static boolean supportsFormat(int deviceIndex, int sampleRate) {
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, format);
        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();

        if (deviceIndex < 0 || deviceIndex >= mixerInfos.length) return false;
        try {
            Mixer mixer = AudioSystem.getMixer(mixerInfos[deviceIndex]);
            return mixer.isLineSupported(dataLineInfo);
        } catch (Exception e) {
            return false;
        }
    }
}
