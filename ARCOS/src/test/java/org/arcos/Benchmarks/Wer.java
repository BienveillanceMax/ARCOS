package org.arcos.Benchmarks;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Word Error Rate minimaliste partagé par les benches STT (SttAccuracyBench, EouLatencyBench).
 * Normalisation : NFC, minuscules, apostrophes/ponctuation → espaces, groupes de chiffres
 * recollés ("3 500" → "3500").
 */
final class Wer {

    private Wer() { }

    static double compute(String reference, String hypothesis) {
        return wer(normalize(reference), normalize(hypothesis));
    }

    static List<String> normalize(String text) {
        String s = Normalizer.normalize(text, Normalizer.Form.NFC)
                .toLowerCase(Locale.FRENCH)
                .replaceAll("[’']", " ")
                .replaceAll("(\\d)\\s+(\\d)", "$1$2")
                .replaceAll("[^\\p{L}\\p{Nd}]+", " ")
                .strip();
        return s.isEmpty() ? List.of() : List.of(s.split("\\s+"));
    }

    /** Distance de Levenshtein au mot / longueur de la référence. */
    static double wer(List<String> ref, List<String> hyp) {
        if (ref.isEmpty()) return hyp.isEmpty() ? 0.0 : 1.0;
        int[] prev = new int[hyp.size() + 1];
        int[] curr = new int[hyp.size() + 1];
        for (int j = 0; j <= hyp.size(); j++) prev[j] = j;
        for (int i = 1; i <= ref.size(); i++) {
            curr[0] = i;
            for (int j = 1; j <= hyp.size(); j++) {
                int cost = ref.get(i - 1).equals(hyp.get(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[hyp.size()] / (double) ref.size();
    }
}
