package org.arcos.IO.InputHandling;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtLoggingLevel;
import ai.onnxruntime.OrtSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.arcos.Configuration.AudioProperties;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;

/**
 * Détection parole/silence par le modèle neuronal <b>Silero VAD v5</b> (ONNX), en remplacement
 * de l'ancien seuil RMS. Robuste au bruit de fond, à la musique et à la parole lointaine.
 *
 * <h2>Contrat ONNX (vérifié par inspection du modèle livré)</h2>
 * <ul>
 *   <li>Entrées : {@code input} float {@code [batch, 64+512]}, {@code state} float
 *       {@code [2, batch, 128]}, {@code sr} int64 scalaire = 16000.</li>
 *   <li>Sorties : {@code output} float {@code [batch, 1]} (probabilité de parole),
 *       {@code stateN} float {@code [2, batch, 128]} (état récurrent réinjecté).</li>
 * </ul>
 *
 * <p><b>Le préfixe de contexte de 64 échantillons est obligatoire, pas optionnel.</b> Le modèle
 * consomme des fenêtres de 512 échantillons (32 ms) mais reçoit en entrée {@code [64 échantillons
 * de contexte + 512 nouveaux]} = 576 échantillons ; le contexte de la fenêtre N est constitué des
 * 64 derniers échantillons de la fenêtre N-1. Sans lui, la probabilité s'effondre (≈0 sur de la
 * vraie parole — spike de validation 2026-07-09).
 *
 * <h2>Fenêtrage vs trames de capture</h2>
 * La boucle de capture fournit des trames de 50 ms (800 échantillons @16 kHz). On les
 * accumule dans un buffer et on exécute le modèle par pas de 512 échantillons, en conservant le
 * reliquat (&lt;512) pour la trame suivante. Une trame est jugée « parole » si au moins une des
 * fenêtres complètes qu'elle contient dépasse le seuil (le silence de fin d'énoncé est ensuite
 * confirmé en wall-clock par {@link UtteranceCaptureService}, pas ici).
 *
 * <p>Dégradation gracieuse : si le modèle est absent du classpath ou que l'init ORT échoue,
 * {@link #isAvailable()} vaut {@code false} et l'appelant désactive la capture vocale (patron
 * {@code CrossEncoderService}). La session est réutilisée entre captures (capture mono-thread,
 * un énoncé à la fois) ; {@link #reset()} la ré-initialise par énoncé.
 */
@Slf4j
@Component
public class SileroSpeechDetector implements SpeechDetector {

    private static final int WINDOW_SAMPLES = 512;   // fenêtre modèle @16kHz (32 ms)
    private static final int CONTEXT_SAMPLES = 64;    // préfixe de contexte v5 (obligatoire)
    private static final int STATE_DIM = 128;
    private static final long SAMPLE_RATE = 16000L;

    private final AudioProperties audioProperties;

    private OrtEnvironment env;
    private OrtSession session;
    private OnnxTensor srTensor;           // scalaire constant = SAMPLE_RATE, créé une fois
    private boolean available = false;

    // État inter-trame (capture mono-thread → pas de synchronisation nécessaire)
    private float[] state;                 // [2][1][128] aplati logiquement
    private final float[] context = new float[CONTEXT_SAMPLES];
    private final float[] pending = new float[WINDOW_SAMPLES];
    /** Buffer d'entrée réutilisé (contexte + fenêtre) — évite une alloc de 576 floats par fenêtre. */
    private final float[] input = new float[CONTEXT_SAMPLES + WINDOW_SAMPLES];
    private int pendingCount = 0;

    public SileroSpeechDetector(AudioProperties audioProperties) {
        this.audioProperties = audioProperties;
    }

    @PostConstruct
    public void initialize() {
        String resource = audioProperties.getVad().getModelResource();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                log.warn("Modèle Silero VAD introuvable dans le classpath ({}). VAD indisponible.", resource);
                return;
            }
            byte[] modelBytes = in.readAllBytes();
            env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setIntraOpNumThreads(1); // trame < 1 ms sur 1 thread ; évite la contention sur le chemin critique
            // Le modèle Silero déclenche ~100 warnings "Removing initializer" au chargement —
            // bruit inoffensif qui corromprait la TUI. On ne loggue que les vraies erreurs.
            opts.setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR);
            session = env.createSession(modelBytes, opts);
            // Le sample rate est un scalaire constant : un seul tenseur, réutilisé à chaque fenêtre.
            srTensor = OnnxTensor.createTensor(env,
                    java.nio.LongBuffer.wrap(new long[]{SAMPLE_RATE}), new long[]{});
            reset();
            available = true;
            log.info("SileroSpeechDetector initialisé : modèle={}, seuil={}",
                    resource, audioProperties.getVad().getSpeechThreshold());
        } catch (Exception e) {
            log.warn("Échec d'initialisation de Silero VAD ({}). VAD indisponible.", resource, e);
            available = false;
        }
    }

    @PreDestroy
    void shutdown() {
        if (srTensor != null) srTensor.close();
        try {
            if (session != null) session.close();
        } catch (OrtException e) {
            log.warn("Erreur à la fermeture de la session ONNX Silero", e);
        }
    }

    /** {@code true} si le modèle est chargé et prêt à classer des trames. */
    public boolean isAvailable() {
        return available;
    }

    public String describe() {
        return "Silero VAD v5 (ONNX, seuil=" + audioProperties.getVad().getSpeechThreshold() + ")";
    }

    @Override
    public void reset() {
        state = new float[2 * STATE_DIM]; // [2, batch=1, 128] aplati (batch=1)
        java.util.Arrays.fill(context, 0f);
        pendingCount = 0;
    }

    @Override
    public boolean isSpeech(byte[] frame16kMonoS16le) {
        if (!available) {
            // Sans modèle, l'appelant a déjà désactivé la capture ; ne jamais bloquer la boucle.
            return false;
        }
        boolean speech = false;
        int sampleCount = frame16kMonoS16le.length / 2;
        for (int i = 0; i < sampleCount; i++) {
            int lo = frame16kMonoS16le[2 * i] & 0xFF;
            int hi = frame16kMonoS16le[2 * i + 1];
            pending[pendingCount++] = ((short) ((hi << 8) | lo)) / 32768f;
            if (pendingCount == WINDOW_SAMPLES) {
                if (runWindow(pending) >= audioProperties.getVad().getSpeechThreshold()) {
                    speech = true;
                }
                pendingCount = 0;
            }
        }
        return speech;
    }

    /**
     * Exécute une fenêtre de 512 échantillons (préfixée des 64 échantillons de contexte), met à
     * jour l'état récurrent et le contexte, et retourne la probabilité de parole.
     */
    private float runWindow(float[] window) {
        System.arraycopy(context, 0, input, 0, CONTEXT_SAMPLES);
        System.arraycopy(window, 0, input, CONTEXT_SAMPLES, WINDOW_SAMPLES);
        // Contexte de la prochaine fenêtre = derniers 64 échantillons de CELLE-CI (avant l'appel).
        System.arraycopy(window, WINDOW_SAMPLES - CONTEXT_SAMPLES, context, 0, CONTEXT_SAMPLES);

        // input réutilisé entre fenêtres ; srTensor est un scalaire constant créé à l'init.
        // stateTensor reste par-appel : reset() réalloue `state` et flattenState() le réécrit.
        try (OnnxTensor inputTensor = OnnxTensor.createTensor(env,
                        java.nio.FloatBuffer.wrap(input), new long[]{1, input.length});
             OnnxTensor stateTensor = OnnxTensor.createTensor(env,
                        java.nio.FloatBuffer.wrap(state), new long[]{2, 1, STATE_DIM})) {

            try (OrtSession.Result result = session.run(Map.of(
                    "input", inputTensor, "state", stateTensor, "sr", srTensor))) {
                float[][] out = (float[][]) result.get(0).getValue();
                float[][][] newState = (float[][][]) result.get(1).getValue();
                flattenState(newState);
                return out[0][0];
            }
        } catch (OrtException e) {
            log.warn("Inférence Silero en échec sur une trame ; trame considérée non-parole", e);
            return 0f;
        }
    }

    /** Réaplatit l'état {@code [2][1][128]} retourné dans le buffer plat réinjecté au prochain run. */
    private void flattenState(float[][][] newState) {
        for (int a = 0; a < 2; a++) {
            System.arraycopy(newState[a][0], 0, state, a * STATE_DIM, STATE_DIM);
        }
    }
}
