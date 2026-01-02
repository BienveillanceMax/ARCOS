package IO.OuputHandling.StateHandler.AudioCue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.InputStream;

@Component
public class AudioCueEngine {

    private static final Logger logger = LoggerFactory.getLogger(AudioCueEngine.class);

    public void play(String soundIdentifier) {
        try {
            // Try to load the sound file from the resources
            InputStream audioSrc = getClass().getResourceAsStream("/sounds/" + soundIdentifier);

            if (audioSrc == null) {
                // If not found in /sounds/, try root
                audioSrc = getClass().getResourceAsStream("/" + soundIdentifier);
            }

            if (audioSrc == null) {
                logger.warn("Sound file not found: {}", soundIdentifier);
                return;
            }

            // Wrap in BufferedInputStream to support mark/reset (needed for some AudioInputStream implementations)
            InputStream bufferedIn = new BufferedInputStream(audioSrc);

            try {
                AudioInputStream audioStream = AudioSystem.getAudioInputStream(bufferedIn);

                // Get a clip resource
                Clip clip = AudioSystem.getClip();

                try {
                    // Open audio clip and load samples from the audio input stream
                    clip.open(audioStream);

                    // Start the clip
                    clip.start();

                    // Add a listener to close the clip and stream when done
                    clip.addLineListener(event -> {
                        if (event.getType() == LineEvent.Type.STOP) {
                            clip.close();
                            try {
                                audioStream.close();
                                bufferedIn.close();
                            } catch (Exception e) {
                                logger.error("Error closing streams after playing sound: {}", soundIdentifier, e);
                            }
                        }
                    });
                } catch (Exception e) {
                   logger.error("Error opening clip for sound: {}", soundIdentifier, e);
                   // Close streams if clip failed to open
                   audioStream.close();
                   bufferedIn.close();
                }

            } catch (Exception e) {
                logger.error("Error getting audio input stream for sound: {}", soundIdentifier, e);
                try {
                    bufferedIn.close();
                } catch (Exception ex) {
                    logger.error("Error closing buffered input stream", ex);
                }
            }

        } catch (Exception e) {
            logger.error("Unexpected error playing sound: {}", soundIdentifier, e);
        }
    }
}
