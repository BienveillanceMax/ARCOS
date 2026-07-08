package org.arcos.Setup.Steps;

import org.arcos.Setup.StepDefinition;
import org.arcos.Setup.UI.WizardDisplay;
import org.arcos.Setup.UI.WizardDisplay.MenuItem;
import org.arcos.Setup.WizardContext;
import org.arcos.Setup.WizardStep;

import java.util.ArrayList;
import java.util.List;

/**
 * Step IV — ANIMA: Personality profile selection.
 * Interactive menu with a live detail pane: navigating the profiles
 * instantly redraws the highlighted profile's description and value gauges
 * below the menu — the user reads the soul before binding it.
 */
public class PersonalityStep implements WizardStep {

    /** Trait value for gauge display. */
    public record TraitValue(String name, int value) {}

    private record ProfileOption(String key, String displayName, String tagline,
                                 String description, List<TraitValue> traits) {}

    private static final int TRAIT_LABEL_WIDTH = 13;

    private static final List<ProfileOption> PROFILES = List.of(
            new ProfileOption("CALCIFER", "CALCIFER", "fire spirit",
                    "Loyal, curious, attached to its freedom.",
                    List.of(new TraitValue("AUTONOMY", 85),
                            new TraitValue("BENEVOLENCE", 90),
                            new TraitValue("HEDONISM", 60))),
            new ProfileOption("K2SO", "K-2SO", "reprogrammed droid",
                    "Reliable, blunt, statistically pessimistic.",
                    List.of(new TraitValue("RELIABILITY", 90),
                            new TraitValue("CONFORMITY", 80),
                            new TraitValue("AUTONOMY", 70))),
            new ProfileOption("GLADOS", "GLaDOS", "control AI",
                    "Analytical, cold, quietly manipulative.",
                    List.of(new TraitValue("POWER", 90),
                            new TraitValue("ACHIEVEMENT", 90),
                            new TraitValue("BENEVOLENCE", 10))),
            new ProfileOption("DEFAULT", "DEFAULT", "balanced baseline",
                    "Neutral profile — every value at 50/100.",
                    List.of(new TraitValue("AUTONOMY", 50),
                            new TraitValue("BENEVOLENCE", 50),
                            new TraitValue("ACHIEVEMENT", 50))));

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
        display.setKeyHints("↑↓ NAV · ↵ SELECT · ESC BACK");

        List<MenuItem> items = new ArrayList<>();
        for (ProfileOption p : PROFILES) {
            items.add(new MenuItem(p.displayName(), p.tagline()));
        }

        String currentProfile = context.getModel().getPersonalityProfile();
        int defaultIndex = indexOfProfile(currentProfile);
        if (defaultIndex < 0) defaultIndex = 0;

        // Menu on rows 0..3; live detail pane below (rule + description + gauges)
        int detailRow = PROFILES.size() + 1;
        int choice = display.selectMenu(items, defaultIndex,
                highlighted -> drawDetailPane(display, detailRow, PROFILES.get(highlighted)));

        if (choice == WizardDisplay.MENU_BACK) {
            return StepResult.BACK;
        }

        ProfileOption chosen = PROFILES.get(choice);
        context.getModel().setPersonalityProfile(chosen.key());
        return StepResult.success("Profile: " + chosen.key());
    }

    /**
     * Redraws the detail pane for the highlighted profile: labeled light rule,
     * description line, three value gauges. Rewrites in place on navigation.
     */
    private void drawDetailPane(WizardDisplay display, int startRow, ProfileOption profile) {
        display.rule(startRow, profile.displayName().toUpperCase());
        display.printLine(startRow + 1, profile.description());
        List<TraitValue> traits = profile.traits();
        for (int t = 0; t < traits.size(); t++) {
            TraitValue trait = traits.get(t);
            display.gauge(startRow + 2 + t, trait.name(), trait.value(), TRAIT_LABEL_WIDTH);
        }
    }

    private static int indexOfProfile(String key) {
        if (key == null || key.isBlank()) return -1;
        for (int i = 0; i < PROFILES.size(); i++) {
            if (PROFILES.get(i).key().equalsIgnoreCase(key.trim())) return i;
        }
        return -1;
    }
}
