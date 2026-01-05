package org.arcos.IO.OuputHandling.StateHandler.AudioCue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.Arrays;

@Component
public class AudioCueEngine {

    private static final Logger logger = LoggerFactory.getLogger(AudioCueEngine.class);

    public void play(String soundIdentifier) {
        new Thread(() -> streamSound(soundIdentifier)).start();
    }

    private void streamSound(String soundIdentifier) {
        try {
            // 1. Chargement et Conversion Standard (44.1kHz, 16-bit)
            InputStream audioSrc = getClass().getResourceAsStream("/sounds/" + soundIdentifier);
            if (audioSrc == null) audioSrc = getClass().getResourceAsStream("/" + soundIdentifier);
            if (audioSrc == null) {
                logger.warn("Fichier son introuvable : {}", soundIdentifier);
                return;
            }

            InputStream bufferedIn = new BufferedInputStream(audioSrc);
            AudioInputStream sourceStream = AudioSystem.getAudioInputStream(bufferedIn);
            AudioFormat baseFormat = sourceStream.getFormat();

            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    44100, 16, baseFormat.getChannels(), baseFormat.getChannels() * 2, 44100, false
            );

            AudioInputStream audioStream = AudioSystem.getAudioInputStream(targetFormat, sourceStream);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, targetFormat);

            // 2. Recherche de la meilleure ligne de sortie (Cœur de la résilience)
            SourceDataLine line = getBestLine(info);

            if (line == null) {
                logger.error("FATAL: Aucun périphérique audio capable de jouer le son n'a été trouvé.");
                return;
            }

            // 3. Lecture
            line.open(targetFormat);

            if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                gain.setValue(6.0f);
            }

            line.start();

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = audioStream.read(buffer)) != -1) {
                line.write(buffer, 0, bytesRead);
            }

            line.drain();
            line.stop();
            line.close();
            audioStream.close();

        } catch (Exception e) {
            logger.error("Erreur lecture son: {}", soundIdentifier, e);
        }
    }

    /**
     * Tente de trouver une ligne audio valide selon une stratégie de repli.
     */
    private SourceDataLine getBestLine(DataLine.Info info) {
        SourceDataLine line = null;

        // ÉTAPE 1 : Essayer "PulseAudio" (souvent le mieux pour Docker/Linux)
        line = findLineByKeyword("PulseAudio", info);
        if (line != null) {
            logger.info("Utilisation du pont PulseAudio.");
            return line;
        }

        // ÉTAPE 2 : Essayer le Default System
        if (AudioSystem.isLineSupported(info)) {
            try {
                line = (SourceDataLine) AudioSystem.getLine(info);
                logger.info("Utilisation du périphérique par défaut du système.");
                return line;
            } catch (LineUnavailableException e) {
                logger.warn("Le périphérique par défaut est indisponible.");
            }
        }

        // ÉTAPE 3 : Mode "Brute Force" - Prendre le premier qui marche qui n'est pas le HDMI (souvent problématique)
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        for (Mixer.Info mixerInfo : mixers) {
            String name = mixerInfo.getName().toLowerCase();
            // On évite le HDMI "Port" qui ne sont souvent que des contrôleurs et pas des sorties streamables
            if (name.contains("hdmi")) continue;

            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            if (mixer.isLineSupported(info)) {
                try {
                    line = (SourceDataLine) mixer.getLine(info);
                    logger.info("Fallback : Utilisation du mixer trouvée '{}'", mixerInfo.getName());
                    return line;
                } catch (LineUnavailableException e) {
                    // Occupé, suivant
                }
            }
        }

        return null;
    }

    private SourceDataLine findLineByKeyword(String keyword, DataLine.Info info) {
        for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
            if (mixerInfo.getName().toLowerCase().contains(keyword.toLowerCase()) ||
                    mixerInfo.getDescription().toLowerCase().contains(keyword.toLowerCase())) {
                Mixer mixer = AudioSystem.getMixer(mixerInfo);
                if (mixer.isLineSupported(info)) {
                    try {
                        return (SourceDataLine) mixer.getLine(info);
                    } catch (LineUnavailableException e) {
                        logger.warn("Mixer trouvé pour '{}' mais indisponible.", keyword);
                    }
                }
            }
        }
        return null;
    }
}