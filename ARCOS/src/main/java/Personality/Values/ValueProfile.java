package Personality.Values;

import Personality.Values.Entities.DimensionSchwartz;
import Personality.Values.Entities.ValueSchwartz;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ValueProfile
{

    private static final double SUPPRESSED_THRESHOLD = 30.0;
    private static final double STRONG_THRESHOLD = 70.0;

    private EnumMap<ValueSchwartz, Double> scores = new EnumMap<>(ValueSchwartz.class);

    public ValueProfile() {
        for (ValueSchwartz v : ValueSchwartz.values()) scores.put(v, 50.0); //TODO CHANGE FOR PERSONALISED
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

