package Personality.Mood;

import lombok.Getter;

@Getter
public class PadState {
    private double pleasure;
    private double arousal;
    private double dominance;

    public PadState() {
        this.pleasure = 0.0;
        this.arousal = 0.0;
        this.dominance = 0.0;
    }

    public PadState(double pleasure, double arousal, double dominance) {
        this.pleasure = clamp(pleasure);
        this.arousal = clamp(arousal);
        this.dominance = clamp(dominance);
    }

    private double clamp(double val) {
        return Math.max(-1.0, Math.min(1.0, val));
    }

    public void update(double deltaP, double deltaA, double deltaD) {
        this.pleasure = clamp(this.pleasure + deltaP);
        this.arousal = clamp(this.arousal + deltaA);
        this.dominance = clamp(this.dominance + deltaD);
    }

    public double distanceTo(PadState other) {
        return Math.sqrt(
            Math.pow(this.pleasure - other.pleasure, 2) +
            Math.pow(this.arousal - other.arousal, 2) +
            Math.pow(this.dominance - other.dominance, 2)
        );
    }

    @Override
    public String toString() {
        return String.format("PAD[%.2f, %.2f, %.2f]", pleasure, arousal, dominance);
    }
}
