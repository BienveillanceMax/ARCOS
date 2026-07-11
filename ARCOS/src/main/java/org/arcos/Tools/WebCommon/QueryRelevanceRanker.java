package org.arcos.Tools.WebCommon;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sélectionne les blocs de texte d'une page les plus pertinents pour une requête,
 * sous un budget de caractères. Aucune dépendance LLM : score lexical par
 * recouvrement de termes (accents strippés, stop-words français ignorés),
 * avec un léger bonus pour le début de document.
 */
@Component
public class QueryRelevanceRanker {

    private static final int MIN_TERM_LENGTH = 2;

    private static final Set<String> STOP_WORDS_FR = Set.of(
            "le", "la", "les", "de", "des", "du", "un", "une", "et", "ou", "a", "au", "aux",
            "en", "pour", "sur", "sous", "dans", "par", "avec", "sans", "vers", "chez",
            "que", "qui", "quoi", "dont", "quel", "quelle", "quels", "quelles",
            "ce", "cet", "cette", "ces", "se", "sa", "son", "ses", "leur", "leurs", "mon", "ma", "mes",
            "il", "elle", "ils", "elles", "on", "nous", "vous", "je", "tu", "me", "te", "moi", "toi",
            "ne", "pas", "plus", "moins", "tres", "bien", "tout", "tous", "toute", "toutes",
            "est", "sont", "etre", "avoir", "ete", "etait", "fait", "faire", "peut",
            "mais", "donc", "or", "ni", "car", "si", "comme", "alors", "aussi", "meme", "encore",
            "y", "quand", "comment", "pourquoi", "combien", "cela");

    private record ScoredBlock(int index, String block, double score) {}

    /**
     * @param query            requête utilisateur guidant la sélection
     * @param blocks           blocs de texte de la page, en ordre document
     * @param fallbackFullText texte complet, utilisé si aucun bloc ne matche
     * @param budgetChars      budget total en caractères du texte retourné
     */
    public String selectRelevant(String query, List<String> blocks, String fallbackFullText, int budgetChars) {
        List<String> terms = significantTerms(query);
        if (terms.isEmpty() || blocks.isEmpty()) {
            return truncate(fallbackFullText, budgetChars);
        }

        List<ScoredBlock> scored = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            double score = scoreBlock(terms, blocks.get(i));
            if (score > 0) {
                // Léger bonus pour les blocs en début de document (chapeau, intro).
                score = score * (1 + 1.0 / (1 + i / 10.0));
                scored.add(new ScoredBlock(i, blocks.get(i), score));
            }
        }
        if (scored.isEmpty()) {
            return truncate(fallbackFullText, budgetChars);
        }

        scored.sort(Comparator.comparingDouble(ScoredBlock::score).reversed());

        List<ScoredBlock> selected = new ArrayList<>();
        int total = 0;
        for (ScoredBlock candidate : scored) {
            int cost = candidate.block().length() + 1; // +1 pour le séparateur
            if (total + cost > budgetChars) {
                continue; // bloc trop gros pour le budget restant, on tente les suivants
            }
            selected.add(candidate);
            total += cost;
        }
        if (selected.isEmpty()) {
            // Le meilleur bloc dépasse à lui seul le budget : on le tronque.
            return truncate(scored.get(0).block(), budgetChars);
        }

        selected.sort(Comparator.comparingInt(ScoredBlock::index)); // lisibilité : ordre document
        return selected.stream().map(ScoredBlock::block).collect(Collectors.joining("\n"));
    }

    private double scoreBlock(List<String> terms, String block) {
        Map<String, Integer> frequencies = new HashMap<>();
        for (String token : tokenize(block)) {
            frequencies.merge(token, 1, Integer::sum);
        }
        double score = 0;
        for (String term : terms) {
            int count = frequencies.getOrDefault(term, 0);
            if (count > 0) {
                score += 1 + 0.5 * (count - 1);
            }
        }
        return score;
    }

    private List<String> significantTerms(String query) {
        return tokenize(query).stream()
                .filter(token -> token.length() >= MIN_TERM_LENGTH)
                .filter(token -> !STOP_WORDS_FR.contains(token))
                .distinct()
                .toList();
    }

    private static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = Normalizer.normalize(text.toLowerCase(Locale.FRENCH), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return java.util.Arrays.stream(normalized.split("[^a-z0-9]+"))
                .filter(token -> !token.isBlank())
                .toList();
    }

    private static String truncate(String text, int budgetChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= budgetChars) {
            return text;
        }
        return text.substring(0, budgetChars) + " [contenu tronqué]";
    }
}
