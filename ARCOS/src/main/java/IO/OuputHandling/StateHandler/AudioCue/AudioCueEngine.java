package IO.OuputHandling.StateHandler.AudioCue;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AudioCueEngine {

    private static final Logger logger = LoggerFactory.getLogger(AudioCueEngine.class);

    // Cache pour garder les sons en mémoire (évite de recharger le fichier à chaque fois)
    private final Map<String, Clip> soundCache = new ConcurrentHashMap<>();

    public void play(String soundIdentifier) {
        try {
            Clip clip = soundCache.get(soundIdentifier);

            if (clip == null || !clip.isOpen()) {
                clip = loadClip(soundIdentifier);
                if (clip != null) {
                    soundCache.put(soundIdentifier, clip);
                }
            }

            if (clip != null) {
                // Rembobiner au début
                clip.setFramePosition(0);
                clip.start();
            }

        } catch (Exception e) {
            logger.error("Failed to play sound: {}", soundIdentifier, e);
        }
    }

    private Clip loadClip(String soundIdentifier) {
        try {
            InputStream audioSrc = getClass().getResourceAsStream("/sounds/" + soundIdentifier);
            if (audioSrc == null) audioSrc = getClass().getResourceAsStream("/" + soundIdentifier);

            if (audioSrc == null) {
                logger.warn("Sound file not found: {}", soundIdentifier);
                return null;
            }

            InputStream bufferedIn = new BufferedInputStream(audioSrc);
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(bufferedIn);

            // Récupérer un Clip mais ne pas le fermer automatiquement à la fin
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);

            return clip;
        } catch (Exception e) {
            logger.error("Error loading clip: {}", soundIdentifier, e);
            return null;
        }
    }

    // Nettoyage propre à l'arrêt de l'application Spring
    @PreDestroy
    public void cleanup() {
        soundCache.values().forEach(clip -> {
            if (clip.isOpen()) clip.close();
        });
        soundCache.clear();
    }
}