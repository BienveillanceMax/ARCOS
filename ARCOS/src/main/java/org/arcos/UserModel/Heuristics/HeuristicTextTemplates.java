package org.arcos.UserModel.Heuristics;

import org.arcos.UserModel.Models.ObservationLeaf;
import org.arcos.UserModel.Models.ObservationSource;
import org.arcos.UserModel.Models.SignificantChange;

import java.util.*;

public class HeuristicTextTemplates {

    public List<ObservationLeaf> generateLeaves(List<SignificantChange> changes, int conversationCount) {
        if (conversationCount < 5) {
            return Collections.emptyList();
        }

        List<ObservationLeaf> leaves = new ArrayList<>();

        for (SignificantChange change : changes) {
            String text = generateText(change);
            if (text != null) {
                leaves.add(new ObservationLeaf(text, change.branch(), ObservationSource.HEURISTIC));
            }
        }

        return leaves;
    }

    private String generateText(SignificantChange change) {
        return switch (change.signalName()) {
            case "avg_word_count" -> generateAvgWordCountText(change.newValue());
            case "avg_word_length" -> generateAvgWordLengthText(change.newValue());
            case "question_ratio" -> generateQuestionRatioText(change.newValue());
            case "time_of_day" -> generateTimeOfDayText(change.newValue());
            case "vocabulary_diversity" -> generateVocabularyDiversityText(change.newValue());
            default -> null;
        };
    }

    private String generateAvgWordCountText(double newValue) {
        if (newValue < 11) {
            return "Mon créateur s'exprime de manière concise et directe.";
        } else if (newValue > 20) {
            return "Mon créateur s'exprime de manière détaillée et élaborée.";
        }
        return null;
    }

    private String generateAvgWordLengthText(double newValue) {
        if (newValue < 3.9) {
            return "Mon créateur utilise un vocabulaire simple et accessible.";
        } else if (newValue > 4.8) {
            return "Mon créateur utilise un vocabulaire riche et soutenu.";
        }
        return null;
    }

    private String generateQuestionRatioText(double newValue) {
        if (newValue < 0.13) {
            return "Mon créateur pose rarement des questions.";
        } else if (newValue > 0.40) {
            return "Mon créateur est très curieux et pose beaucoup de questions.";
        }
        return null;
    }

    private String generateTimeOfDayText(double newValue) {
        if (newValue >= 5 && newValue < 12) {
            return "Mon créateur interagit principalement le matin.";
        } else if (newValue >= 12 && newValue < 18) {
            return "Mon créateur interagit principalement l'après-midi.";
        } else if (newValue >= 18 && newValue < 22) {
            return "Mon créateur interagit principalement en soirée.";
        } else {
            return "Mon créateur interagit principalement la nuit.";
        }
    }

    private String generateVocabularyDiversityText(double newValue) {
        if (newValue < 0.4) {
            return "Mon créateur a un vocabulaire répétitif.";
        } else if (newValue > 0.7) {
            return "Mon créateur a un vocabulaire très varié.";
        }
        return null;
    }
}
