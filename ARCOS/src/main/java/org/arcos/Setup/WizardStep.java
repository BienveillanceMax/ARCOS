package org.arcos.Setup;

import org.arcos.Setup.UI.WizardDisplay;

/**
 * Interface for all wizard configuration steps.
 * Each step is responsible for one part of the configuration collection.
 * No Spring dependencies — plain Java only.
 */
public interface WizardStep {

    /** Short name for logs and headers. */
    String getName();

    /** True if this step is mandatory (e.g., Mistral API key). */
    boolean isRequired();

    /** True if the user can skip this step. */
    boolean isSkippable();

    /** Returns the step definition (Latin name, Roman numeral). */
    default StepDefinition getStepDefinition() {
        return StepDefinition.NEXUS; // override in subclasses
    }

    /**
     * Execute the interactive step.
     *
     * @param display wizard display abstraction for rendering and input
     * @param context mutable wizard context (collected values stored here)
     * @return step result
     */
    StepResult execute(WizardDisplay display, WizardContext context);

    /** Result of step execution. */
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
