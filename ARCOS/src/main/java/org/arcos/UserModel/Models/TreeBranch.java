package org.arcos.UserModel.Models;

public enum TreeBranch {

    IDENTITE(DecayClass.SCENE_CONSISTENT, true, 25),
    COMMUNICATION(DecayClass.SCENE_CONSISTENT, true, 30),
    HABITUDES(DecayClass.SCENE_RELATED, false, 0),
    OBJECTIFS(DecayClass.SCENE_RELATED, false, 0),
    EMOTIONS(DecayClass.SCENE_RELATED, false, 0),
    INTERETS(DecayClass.SCENE_RELATED, false, 0);

    private final DecayClass decayClass;
    private final boolean alwaysInjected;
    private final int budgetTokens;

    TreeBranch(DecayClass decayClass, boolean alwaysInjected, int budgetTokens) {
        this.decayClass = decayClass;
        this.alwaysInjected = alwaysInjected;
        this.budgetTokens = budgetTokens;
    }

    public DecayClass getDecayClass() {
        return decayClass;
    }

    public boolean isAlwaysInjected() {
        return alwaysInjected;
    }

    public int getBudgetTokens() {
        return budgetTokens;
    }

    public enum DecayClass {
        SCENE_CONSISTENT(0.99),
        SCENE_RELATED(0.80);

        private final double retentionFactor;

        DecayClass(double retentionFactor) {
            this.retentionFactor = retentionFactor;
        }

        public double getRetentionFactor() {
            return retentionFactor;
        }
    }
}
