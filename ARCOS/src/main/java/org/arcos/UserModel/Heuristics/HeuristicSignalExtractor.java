package org.arcos.UserModel.Heuristics;

import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

public class HeuristicSignalExtractor {

    private static final List<String> CORRECTION_MARKERS = List.of(
            "non", "en fait", "pardon", "je voulais dire", "plutôt"
    );

    private final Set<String> disfluenceWords;

    public HeuristicSignalExtractor(List<String> disfluenceWords) {
        this.disfluenceWords = new HashSet<>(disfluenceWords);
    }

    public Map<String, Double> extractSignals(List<String> userMessages, boolean hadInitiative) {
        Map<String, Double> signals = new LinkedHashMap<>();

        int messageCount = userMessages.size();
        signals.put("message_count", (double) messageCount);

        // Filtered words per message
        List<List<String>> filteredWordsPerMessage = userMessages.stream()
                .map(this::filterWords)
                .collect(Collectors.toList());

        // avg_word_count
        double avgWordCount = filteredWordsPerMessage.stream()
                .mapToInt(List::size)
                .average()
                .orElse(0.0);
        signals.put("avg_word_count", avgWordCount);

        // vocabulary_diversity (type/token ratio on all messages concatenated)
        List<String> allFilteredWords = filteredWordsPerMessage.stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
        double vocabularyDiversity = 0.0;
        if (!allFilteredWords.isEmpty()) {
            long uniqueCount = allFilteredWords.stream().distinct().count();
            vocabularyDiversity = (double) uniqueCount / allFilteredWords.size();
        }
        signals.put("vocabulary_diversity", vocabularyDiversity);

        // avg_word_length
        double avgWordLength = allFilteredWords.stream()
                .mapToInt(String::length)
                .average()
                .orElse(0.0);
        signals.put("avg_word_length", avgWordLength);

        // time_of_day
        signals.put("time_of_day", (double) LocalTime.now().getHour());

        // correction_frequency
        long correctionCount = userMessages.stream()
                .filter(this::containsCorrectionMarker)
                .count();
        double correctionFrequency = messageCount > 0 ? (double) correctionCount / messageCount : 0.0;
        signals.put("correction_frequency", correctionFrequency);

        // question_ratio — guard: only include if message_count >= 3
        if (messageCount >= 3) {
            long questionCount = userMessages.stream()
                    .filter(msg -> msg.trim().endsWith("?"))
                    .count();
            signals.put("question_ratio", (double) questionCount / messageCount);
        }

        // initiative_response
        signals.put("initiative_response", hadInitiative ? 1.0 : 0.0);

        // return_frequency — always 1.0, incremented externally via EMA over sessions
        signals.put("return_frequency", 1.0);

        return signals;
    }

    private List<String> filterWords(String text) {
        String[] tokens = text.toLowerCase().split("\\s+");
        return Arrays.stream(tokens)
                .filter(word -> !word.isEmpty())
                .filter(word -> !disfluenceWords.contains(word))
                .collect(Collectors.toList());
    }

    private boolean containsCorrectionMarker(String message) {
        String lower = message.toLowerCase();
        return CORRECTION_MARKERS.stream().anyMatch(lower::contains);
    }
}
