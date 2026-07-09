package org.arcos.IO.InputHandling;

/**
 * Classifieur parole/silence d'une trame audio, consommé par la boucle de capture
 * ({@link UtteranceCaptureService}).
 *
 * <p>Interface volontairement minimale (ISP) : la boucle n'a besoin que de classer une trame
 * et de réinitialiser l'état entre deux énoncés. Les détails de cycle de vie du modèle
 * (disponibilité, description) vivent sur l'implémentation concrète, pas ici.
 *
 * <p>Elle existe comme <em>seam</em> de test : l'implémentation de production
 * ({@link SileroSpeechDetector}) est un modèle ONNX à état, qui classerait les trames
 * synthétiques des tests de la boucle de capture comme du non-parole. L'interface permet d'y
 * injecter un double trivial — même patron que {@link MicrophoneSource} et ses doubles de test.
 */
public interface SpeechDetector {

    /**
     * Retourne {@code true} si la trame contient de la parole. La trame est du PCM 16-bit
     * signé little-endian, mono, 16 kHz (une trame de 50 ms = 1600 octets, comme fournie par
     * la boucle de capture).
     */
    boolean isSpeech(byte[] frame16kMonoS16le);

    /**
     * Réinitialise tout état inter-trame (état récurrent du modèle, buffers). À appeler en tête
     * de chaque capture d'énoncé — sans quoi l'état d'un énoncé fuit dans le suivant.
     */
    void reset();
}
