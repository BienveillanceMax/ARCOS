package org.arcos.IO.InputHandling;

/**
 * Audio framing utilities shared by the mic-capture path ({@link org.arcos.Producers.WakeWordProducer})
 * and benchmarks.
 *
 * <p>{@link #downsample(short[], int, short[], int)} — 21-tap FIR low-pass + point-pick resampler
 * (e.g. 44.1 kHz mic input → 16 kHz STT input).
 *
 * <p>La détection parole/silence, jadis un seuil RMS ici ({@code isSilence}), est désormais
 * assurée par {@link SpeechDetector} (Silero VAD neuronal).
 */
public final class AudioFraming {

    private AudioFraming() {}

    /** 21-tap low-pass FIR (Hamming window, fc=7200Hz at 44100Hz, ~44dB stopband). */
    public static final double[] LP_FILTER;
    static {
        int N = 21;
        double fc = 7200.0 / 44100.0;
        LP_FILTER = new double[N];
        double sum = 0;
        int M = N / 2;
        for (int i = 0; i < N; i++) {
            double n = i - M;
            double sinc = (n == 0) ? 2 * Math.PI * fc : Math.sin(2 * Math.PI * fc * n) / (Math.PI * n);
            double hamming = 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (N - 1));
            LP_FILTER[i] = sinc * hamming;
            sum += LP_FILTER[i];
        }
        for (int i = 0; i < N; i++) LP_FILTER[i] /= sum;
    }

    /**
     * Anti-aliased downsampling using the 21-tap FIR. Behavior mirrors the legacy in-line
     * implementation in {@code WakeWordProducer}: nearest-neighbor index selection with FIR
     * smoothing centered on each output sample.
     */
    public static void downsample(short[] input, int inputLength, short[] output, int outputLength) {
        double ratio = (double) inputLength / outputLength;
        int halfTaps = LP_FILTER.length / 2;
        for (int i = 0; i < outputLength; i++) {
            int center = (int) (i * ratio);
            double acc = 0;
            for (int t = 0; t < LP_FILTER.length; t++) {
                int idx = center - halfTaps + t;
                if (idx >= 0 && idx < inputLength) {
                    acc += input[idx] * LP_FILTER[t];
                }
            }
            output[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(acc)));
        }
    }
}
