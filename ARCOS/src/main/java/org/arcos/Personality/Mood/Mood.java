package org.arcos.Personality.Mood;

import lombok.Getter;

@Getter
public enum Mood {
    JOIE("Joie", 0.8, 0.6, 0.5, "Joyeux, enthousiaste et positif."),
    COLERE("Colère", -0.6, 0.8, 0.8, "Irrité, sec et direct."),
    PEUR("Peur", -0.6, 0.8, -0.6, "Inquiet, hésitant et sur le qui-vive."),
    TRISTESSE("Tristesse", -0.8, -0.5, -0.5, "Mélancolique, lent et peu bavard."),
    ENNUI("Ennui", -0.3, -0.6, 0.1, "Désintéressé, passif et laconique."),
    CALME("Calme", 0.6, -0.6, 0.5, "Serein, posé et réfléchi."),
    CURIOSITE("Curiosité", 0.5, 0.6, -0.2, "Intrigué, questionneur et vif."),
    HONTE("Honte", -0.7, 0.4, -0.7, "Honteux, retiré et évasif."),
    CULPABILITE("Culpabilité", -0.6, 0.5, -0.3, "Coupable, désolé et cherchant à réparer."),
    MEPRIS("Mépris", -0.6, 0.5, 0.8, "Méprisant, hautain et distant."),
    SURPRISE("Surprise", 0.4, 0.8, -0.2, "Surpris, vif et réactif."),
    NEUTRE("Neutre", 0.0, 0.0, 0.0, "Équilibré et objectif.");

    private final String label;
    private final PadState center;
    private final String description;

    Mood(String label, double p, double a, double d, String description) {
        this.label = label;
        this.center = new PadState(p, a, d);
        this.description = description;
    }

    public static Mood fromPadState(PadState state) {
        Mood closest = NEUTRE;
        double minDistance = Double.MAX_VALUE;

        for (Mood mood : values()) {
            double dist = state.distanceTo(mood.center);
            if (dist < minDistance) {
                minDistance = dist;
                closest = mood;
            }
        }
        return closest;
    }
}
