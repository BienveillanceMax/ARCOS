package org.arcos.Setup;

import org.jline.terminal.Terminal;

/**
 * Interface commune pour toutes les étapes du wizard de configuration ARCOS.
 * Chaque étape est responsable d'une partie de la collecte de configuration.
 * Aucune dépendance Spring — plain Java uniquement.
 */
public interface WizardStep {

    /** Nom court de l'étape, affiché dans les logs et les en-têtes. */
    String getName();

    /** true si cette étape est obligatoire (ex: clé Mistral AI). */
    boolean isRequired();

    /** true si l'utilisateur peut passer cette étape (affiche l'option "Passer"). */
    boolean isSkippable();

    /**
     * Exécute l'étape interactive.
     *
     * @param terminal terminal JLine3 pour l'entrée/sortie interactive
     * @param context  contexte mutable du wizard (les valeurs collectées sont stockées ici)
     * @return résultat de l'étape
     */
    StepResult execute(Terminal terminal, WizardContext context);

    /** Résultat de l'exécution d'une étape. */
    record StepResult(boolean success, boolean skipped, String message) {

        public static StepResult success(String message) {
            return new StepResult(true, false, message);
        }

        public static StepResult skipped(String message) {
            return new StepResult(true, true, message);
        }

        public static StepResult failure(String message) {
            return new StepResult(false, false, message);
        }
    }
}
