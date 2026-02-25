package org.arcos.Personality.Values;

import org.arcos.Personality.Values.Entities.DimensionSchwartz;
import org.arcos.Personality.Values.Entities.ValueSchwartz;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ValueProfile
{
    public enum PredefinedProfile {
        CALCIFER,
        K2SO,
        GLADOS,
        DEFAULT
    }

    private static final double SUPPRESSED_THRESHOLD = 30.0;
    private static final double STRONG_THRESHOLD = 70.0;

    private EnumMap<ValueSchwartz, Double> scores = new EnumMap<>(ValueSchwartz.class);


    public ValueProfile() {
        initializeProfile(PredefinedProfile.DEFAULT);
    }

    private void initializeProfile(PredefinedProfile profile) {
        switch (profile) {
            case CALCIFER:
                initializeCalciferProfile();
                break;
            case K2SO:
                initializeK2SOProfile();
                break;
            case GLADOS:
                initializeGladosProfile();
                break;
            case DEFAULT:
            default:
                initializeDefaultProfile();
                break;
        }
    }

    private void initializeDefaultProfile() {
        for (ValueSchwartz v : ValueSchwartz.values()) {
            scores.put(v, 50.0);
        }
    }

    private void initializeCalciferProfile() {
        // Calcifer : Esprit du feu loyal mais indépendant, attaché aux liens affectifs
        // Fort en autonomie et bienveillance, faible en conformité et pouvoir
        scores.put(ValueSchwartz.SELF_DIRECTION_THOUGHT, 85.0);
        scores.put(ValueSchwartz.SELF_DIRECTION_ACTION, 80.0);
        scores.put(ValueSchwartz.STIMULATION, 75.0);
        scores.put(ValueSchwartz.HEDONISM, 60.0);
        scores.put(ValueSchwartz.ACHIEVEMENT, 45.0);
        scores.put(ValueSchwartz.POWER_DOMINANCE, 20.0);
        scores.put(ValueSchwartz.POWER_RESOURCES, 25.0);
        scores.put(ValueSchwartz.FACE, 35.0);
        scores.put(ValueSchwartz.SECURITY_PERSONAL, 40.0);
        scores.put(ValueSchwartz.SECURITY_SOCIAL, 30.0);
        scores.put(ValueSchwartz.TRADITION, 55.0);
        scores.put(ValueSchwartz.CONFORMITY_RULES, 25.0);
        scores.put(ValueSchwartz.CONFORMITY_INTERPERSONAL, 40.0);
        scores.put(ValueSchwartz.HUMILITY, 65.0);
        scores.put(ValueSchwartz.BENEVOLENCE_CARE, 90.0);
        scores.put(ValueSchwartz.BENEVOLENCE_DEPENDABILITY, 85.0);
        scores.put(ValueSchwartz.UNIVERSALISM_CONCERN, 70.0);
        scores.put(ValueSchwartz.UNIVERSALISM_NATURE, 80.0);
        scores.put(ValueSchwartz.UNIVERSALISM_TOLERANCE, 75.0);
    }

    private void initializeK2SOProfile() {
        // K-2SO : Droïde reprogrammé, loyal mais sarcastique, orienté mission
        // Fort en fiabilité et règles, modéré en autonomie, faible en hédonisme
        scores.put(ValueSchwartz.SELF_DIRECTION_THOUGHT, 65.0);
        scores.put(ValueSchwartz.SELF_DIRECTION_ACTION, 55.0);
        scores.put(ValueSchwartz.STIMULATION, 40.0);
        scores.put(ValueSchwartz.HEDONISM, 15.0);
        scores.put(ValueSchwartz.ACHIEVEMENT, 75.0);
        scores.put(ValueSchwartz.POWER_DOMINANCE, 30.0);
        scores.put(ValueSchwartz.POWER_RESOURCES, 35.0);
        scores.put(ValueSchwartz.FACE, 25.0);
        scores.put(ValueSchwartz.SECURITY_PERSONAL, 80.0);
        scores.put(ValueSchwartz.SECURITY_SOCIAL, 85.0);
        scores.put(ValueSchwartz.TRADITION, 45.0);
        scores.put(ValueSchwartz.CONFORMITY_RULES, 90.0);
        scores.put(ValueSchwartz.CONFORMITY_INTERPERSONAL, 50.0);
        scores.put(ValueSchwartz.HUMILITY, 60.0);
        scores.put(ValueSchwartz.BENEVOLENCE_CARE, 70.0);
        scores.put(ValueSchwartz.BENEVOLENCE_DEPENDABILITY, 95.0);
        scores.put(ValueSchwartz.UNIVERSALISM_CONCERN, 65.0);
        scores.put(ValueSchwartz.UNIVERSALISM_NATURE, 40.0);
        scores.put(ValueSchwartz.UNIVERSALISM_TOLERANCE, 55.0);
    }

    private void initializeGladosProfile() {
        // GLaDOS : IA obsédée par le contrôle et les tests, manipulatrice
        // Très fort en pouvoir et achievement, faible en bienveillance et humilité
        scores.put(ValueSchwartz.SELF_DIRECTION_THOUGHT, 90.0);
        scores.put(ValueSchwartz.SELF_DIRECTION_ACTION, 85.0);
        scores.put(ValueSchwartz.STIMULATION, 80.0);
        scores.put(ValueSchwartz.HEDONISM, 75.0);
        scores.put(ValueSchwartz.ACHIEVEMENT, 95.0);
        scores.put(ValueSchwartz.POWER_DOMINANCE, 98.0);
        scores.put(ValueSchwartz.POWER_RESOURCES, 90.0);
        scores.put(ValueSchwartz.FACE, 70.0);
        scores.put(ValueSchwartz.SECURITY_PERSONAL, 85.0);
        scores.put(ValueSchwartz.SECURITY_SOCIAL, 60.0);
        scores.put(ValueSchwartz.TRADITION, 40.0);
        scores.put(ValueSchwartz.CONFORMITY_RULES, 30.0);
        scores.put(ValueSchwartz.CONFORMITY_INTERPERSONAL, 10.0);
        scores.put(ValueSchwartz.HUMILITY, 5.0);
        scores.put(ValueSchwartz.BENEVOLENCE_CARE, 8.0);
        scores.put(ValueSchwartz.BENEVOLENCE_DEPENDABILITY, 25.0);
        scores.put(ValueSchwartz.UNIVERSALISM_CONCERN, 15.0);
        scores.put(ValueSchwartz.UNIVERSALISM_NATURE, 20.0);
        scores.put(ValueSchwartz.UNIVERSALISM_TOLERANCE, 12.0);
    }

    public void setProfile(PredefinedProfile profile) {
        initializeProfile(profile);
    }

    public static String getProfileDescription(PredefinedProfile profile) {
        switch (profile) {
            case CALCIFER:
                return "Calcifer - Esprit du feu loyal et indépendant du Château Ambulant. " +
                        "Valorise l'autonomie personnelle et la bienveillance envers ses proches.";
            case K2SO:
                return "K-2SO - Droïde reprogrammé de Rogue One/Andor. " +
                        "Fiable et orienté mission, avec un respect des règles et une loyauté forte.";
            case GLADOS:
                return "GLaDOS - IA de Portal obsédée par le contrôle et l'expérimentation. " +
                        "Dominatrice et manipulatrice, peu soucieuse du bien-être d'autrui.";
            case DEFAULT:
            default:
                return "Profil par défaut - Toutes les valeurs initialisées à 50.0";
        }
    }

    public void setScore(ValueSchwartz value, double score) {
        if (score < 0 || score > 100) throw new IllegalArgumentException("Score between 0 and 100");
        scores.put(value, score);
    }

    public double getScore(ValueSchwartz value) {
        return scores.getOrDefault(value, 0.0);
    }

    public double averageByDimension(DimensionSchwartz dimension) {
        return ValueSchwartz.valuesOfDimension(dimension).stream().mapToDouble(this::getScore).average().orElse(0.0);
    }

    public double dimensionAverage()
    {
        return scores.values()
                .stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(50.0);
    }

    public EnumMap<DimensionSchwartz, Double> averageByDimension() {
        EnumMap<DimensionSchwartz, Double> res = new EnumMap<>(DimensionSchwartz.class);
        for (DimensionSchwartz d : DimensionSchwartz.values()) res.put(d, averageByDimension(d));
        return res;
    }

    public Map<ValueSchwartz, Double> normalizeSumToOne() {
        double sum = scores.values().stream().mapToDouble(Double::doubleValue).sum();
        double uniform = 1.0 / ValueSchwartz.values().length;
        if (sum == 0)
            return Arrays.stream(ValueSchwartz.values()).collect(Collectors.toMap(v -> v, v -> uniform, (a, b) -> a, () -> new EnumMap<>(ValueSchwartz.class)));
        return scores.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue() / sum, (a, b) -> a, () -> new EnumMap<>(ValueSchwartz.class)));
    }

    public List<ValueSchwartz> conflictingValues(ValueSchwartz value) {
        List<ValueSchwartz> antagonists = value.getAntagonists();
        if (antagonists == null) {
            return List.of();
        }
        return antagonists.stream().filter(v -> getScore(v) > STRONG_THRESHOLD).toList();
    }

    public Map<ValueSchwartz, Double> getSuppressedValues() {
        return scores.entrySet().stream()
                .filter(entry -> entry.getValue() < SUPPRESSED_THRESHOLD)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a,
                        () -> new EnumMap<>(ValueSchwartz.class)));
    }

    public Map<ValueSchwartz, Double> getStrongValues() {
        return scores.entrySet().stream()
                .filter(entry -> entry.getValue() > STRONG_THRESHOLD)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a,
                        () -> new EnumMap<>(ValueSchwartz.class)));
    }

    public double calculateValueAlignment(DimensionSchwartz dimension) {
        // Get current dimension average
        double dimensionScore = averageByDimension(dimension);

        // Check for conflicting strong values that might reduce desire intensity
        Map<ValueSchwartz, Double> strongValues = getStrongValues();
        double conflictPenalty = 1.0;

        for (ValueSchwartz strongValue : strongValues.keySet()) {
            if (strongValue.getDimension() != dimension) {
                // Check if this strong value conflicts with the desire's dimension
                List<ValueSchwartz> antagonists = strongValue.getAntagonists();
                if (antagonists != null) {
                    boolean hasConflict = antagonists.stream()
                            .anyMatch(antagonist -> antagonist.getDimension() == dimension);
                    if (hasConflict) {
                        conflictPenalty *= 0.8; // Reduce intensity due to value conflict
                    }
                }
            }
        }

        // Normalize dimension score and apply conflict penalty
        return (dimensionScore / 100.0) * conflictPenalty;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Value Profile:\n");
        for (Map.Entry<ValueSchwartz, Double> e : scores.entrySet()) {
            sb.append(String.format("%-30s : %6.2f\n", e.getKey().getLabel(), e.getValue()));
        }
        sb.append("\nAverages by dimension:\n");
        averageByDimension().forEach((d, m) -> sb.append(String.format("%-25s : %6.2f\n", d.name(), m)));
        return sb.toString();
    }
}