package org.arcos.Setup.Validation;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
     * Recherche un device par nom (correspondance exacte).
     * Utilisé pour résoudre un nom stable vers l'index courant du runtime.
     *
     * @return le device ou null si introuvable
     */
    public static AudioDevice findByName(String name) {
        if (name == null || name.isBlank()) return null;
        return getInputDevices().stream()
                .filter(d -> name.equals(d.name()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Résultat d'un sondage audio sur un device.
     *
     * @param rms   niveau RMS mesuré, ou -1 si le device n'est pas utilisable
     * @param error message d'erreur si rms == -1, sinon null
     */
    public record ProbeResult(int rms, String error) {
        public static ProbeResult ok(int rms) { return new ProbeResult(rms, null); }
        public static ProbeResult fail(String error) { return new ProbeResult(-1, error); }
    }

    /**
     * Capture brièvement l'audio du device et retourne le niveau RMS.
     * Utilisé par le wizard pour indiquer quel micro capte du signal.
     *
     * @param deviceIndex index global du mixer
     * @param sampleRate  fréquence d'échantillonnage à utiliser
     * @param durationMs  durée de capture en ms (300-500 recommandé)
     * @return ProbeResult avec RMS ou message d'erreur
     */
    public static ProbeResult probeRmsLevel(int deviceIndex, int sampleRate, int durationMs) {
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, format);
        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();

        if (deviceIndex < 0 || deviceIndex >= mixerInfos.length) {
            return ProbeResult.fail("index out of bounds (" + deviceIndex + "/" + mixerInfos.length + ")");
        }

        try {
            Mixer mixer = AudioSystem.getMixer(mixerInfos[deviceIndex]);
            if (!mixer.isLineSupported(dataLineInfo)) {
                return ProbeResult.fail("format not supported (" + sampleRate + "Hz 16-bit mono)");
            }

            TargetDataLine line = (TargetDataLine) mixer.getLine(dataLineInfo);
            line.open(format);
            line.start();

            int totalBytes = sampleRate * 2 * durationMs / 1000;
            byte[] buf = new byte[totalBytes];
            int offset = 0;
            long deadline = System.currentTimeMillis() + durationMs + 500;
            while (offset < totalBytes && System.currentTimeMillis() < deadline) {
                int read = line.read(buf, offset, totalBytes - offset);
                if (read > 0) offset += read;
            }

            line.flush();
            line.stop();
            line.close();

            if (offset < 4) {
                return ProbeResult.fail("no data read (" + offset + " bytes)");
            }
            int samples = offset / 2;
            long sum = 0;
            var sb = ByteBuffer.wrap(buf, 0, offset).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
            for (int i = 0; i < samples; i++) {
                short s = sb.get(i);
                sum += (long) s * s;
            }
            return ProbeResult.ok((int) Math.sqrt((double) sum / samples));
        } catch (Exception e) {
            return ProbeResult.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
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
