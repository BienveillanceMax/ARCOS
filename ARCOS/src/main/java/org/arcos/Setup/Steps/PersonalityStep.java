package org.arcos.Setup.Steps;

import org.arcos.Setup.StepDefinition;
import org.arcos.Setup.UI.AnsiPalette;
import org.arcos.Setup.UI.WizardDisplay;
import org.arcos.Setup.WizardContext;
import org.arcos.Setup.WizardStep;

import java.util.List;

/**
 * Step III — ANIMA: Personality profile selection.
 * Compact horizontal gauge layout: 2 lines per profile.
 * All text in English.
 */
public class PersonalityStep implements WizardStep {

    /** Trait value for compact gauge display. */
    public record TraitValue(String abbreviation, int value) {}

    private record ProfileOption(String key, String displayName, String description,
                                 List<TraitValue> traits) {}

    private static final List<ProfileOption> PROFILES = List.of(
            new ProfileOption("CALCIFER", "CALCIFER",
                    "Fire spirit — loyal, curious, freedom-seeking",
                    List.of(new TraitValue("AUT", 85), new TraitValue("BNV", 90), new TraitValue("HED", 60))),
            new ProfileOption("K2SO", "K-2SO",
                    "Reprogrammed droid — reliable, blunt, sarcastic",
                    List.of(new TraitValue("FIA", 90), new TraitValue("REG", 80), new TraitValue("AUT", 70))),
            new ProfileOption("GLADOS", "GLaDOS",
                    "Control AI — analytical, cold, manipulative",
                    List.of(new TraitValue("POW", 90), new TraitValue("ACH", 90), new TraitValue("BNV", 10))),
            new ProfileOption("DEFAULT", "DEFAULT",
                    "Balanced profile — all values 50/100",
                    List.of())
    );

    @Override
    public String getName() {
        return "ANIMA";
    }

    @Override
    public boolean isRequired() {
        return true;
    }

    @Override
    public boolean isSkippable() {
        return false;
    }

    @Override
    public StepDefinition getStepDefinition() {
        return StepDefinition.ANIMA;
    }

    @Override
    public StepResult execute(WizardDisplay display, WizardContext context) {
        boolean color = display.isColorSupported();

        display.printLine("Select personality profile:");
        display.printLine("");

        for (int i = 0; i < PROFILES.size(); i++) {
            ProfileOption p = PROFILES.get(i);
            String nameColor = color ? AnsiPalette.BRIGHT : "";
            String reset = color ? AnsiPalette.RESET : "";

            display.printLine("[" + (i + 1) + "]  " + nameColor + p.displayName() + reset
                    + " \u2014 " + p.description());

            // Compact horizontal gauges on second line
            if (!p.traits().isEmpty()) {
                StringBuilder gauges = new StringBuilder("     ");
                for (int t = 0; t < p.traits().size(); t++) {
                    TraitValue tv = p.traits().get(t);
                    gauges.append(display.gaugeCompact(tv.abbreviation(), tv.value()));
                    if (t < p.traits().size() - 1) gauges.append("   ");
                }
                display.printLine(gauges.toString());
            }

            display.printLine("");
        }

        String currentProfile = context.getModel().getPersonalityProfile();
        String defaultHint = (currentProfile != null && !currentProfile.isBlank())
                ? " [current: " + currentProfile + "]" : "";

        while (true) {
            String input = display.readLine("\u25b8 (1-4)" + defaultHint + " ");

            if (input == null || input.isBlank()) {
                if (currentProfile != null && !currentProfile.isBlank()) {
                    display.printLine(okText("Profile kept: " + currentProfile, color));
                    return StepResult.success("Profile kept: " + currentProfile);
                }
                display.showError("Choose a profile (1-4).");
                continue;
            }

            try {
                int choice = Integer.parseInt(input.trim());
                if (choice >= 1 && choice <= PROFILES.size()) {
                    ProfileOption chosen = PROFILES.get(choice - 1);
                    context.getModel().setPersonalityProfile(chosen.key());
                    display.printLine(okText("Profile selected: " + chosen.displayName(), color));
                    return StepResult.success("Profile: " + chosen.key());
                } else {
                    display.showError("Choose between 1 and " + PROFILES.size() + ".");
                }
            } catch (NumberFormatException e) {
                // Accept profile name input
                String upper = input.trim().toUpperCase();
                for (ProfileOption p : PROFILES) {
                    if (p.key().equals(upper) || p.displayName().toUpperCase().equals(upper)) {
                        context.getModel().setPersonalityProfile(p.key());
                        display.printLine(okText("Profile selected: " + p.displayName(), color));
                        return StepResult.success("Profile: " + p.key());
                    }
                }
                display.showError("Unknown profile. Enter a number (1-4) or profile name.");
            }
        }
    }

    private String okText(String msg, boolean color) {
        if (color) return AnsiPalette.OK + "\u2713" + AnsiPalette.RESET + " " + msg;
        return "[OK] " + msg;
    }
}
