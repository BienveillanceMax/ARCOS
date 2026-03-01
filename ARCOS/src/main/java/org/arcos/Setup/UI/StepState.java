package org.arcos.Setup.UI;

/**
 * Immutable state of a wizard step for rendering the step index.
 */
public record StepState(String romanNumeral, String latinName, StepIndicator.Status status) {
}
