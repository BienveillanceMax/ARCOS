package org.arcos.Setup;

/**
 * Latin nomenclature and Roman numeral metadata for wizard steps.
 * Each step has an institutional designation — the system naming its own subsystems.
 */
public enum StepDefinition {

    NEXUS("I",    "NEXUS"),      // API key bindings
    VOX("II",     "VOX"),        // Voice / microphone input
    INTERPRES("III", "INTERPRES"), // STT backend selection
    ANIMA("IV",   "ANIMA"),      // Personality / soul
    CORPUS("V",   "CORPUS"),     // Service body check
    FIAT("",      "FIAT");       // Final save — no numeral

    private final String romanNumeral;
    private final String latinName;

    StepDefinition(String romanNumeral, String latinName) {
        this.romanNumeral = romanNumeral;
        this.latinName = latinName;
    }

    public String romanNumeral() { return romanNumeral; }
    public String latinName() { return latinName; }
}
