package org.arcos.Personality.Mood;

import org.springframework.stereotype.Component;

/**
 * Détient l'état émotionnel PAD (Pleasure-Arousal-Dominance) de Calcifer.
 * Singleton indépendant du cycle de vie des sessions conversationnelles :
 * l'humeur persiste même quand le contexte de conversation est réinitialisé.
 */
@Component
public class MoodStateHolder {

    private volatile PadState padState = new PadState();

    public PadState getPadState() {
        return padState;
    }

    public void setPadState(PadState padState) {
        this.padState = padState;
    }
}
