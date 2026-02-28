package org.arcos.Setup.Steps;

import org.arcos.Setup.UI.AnsiPalette;
import org.arcos.Setup.UI.TerminalCapabilities;
import org.arcos.Setup.WizardContext;
import org.arcos.Setup.WizardStep;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;

import java.io.PrintWriter;
import java.util.List;

/**
 * Étape 3 du wizard : sélection du profil de personnalité fondamentale.
 * Propose CALCIFER, K2SO, GLADOS, DEFAULT avec descriptions riches.
 */
public class PersonalityStep implements WizardStep {

    private record ProfileOption(String key, String displayName, String description, String values) {}

    private static final List<ProfileOption> PROFILES = List.of(
            new ProfileOption("CALCIFER", "CALCIFER",
                    "Esprit du feu loyal et indépendant du Château Ambulant.\n" +
                    "  Curieux, chaleureux et attaché à la liberté personnelle.",
                    "Autonomie ★★★  Bienveillance ★★★  Hédonisme ★★"),
            new ProfileOption("K2SO", "K-2SO",
                    "Droïde reprogrammé de Rogue One — fiable, sarcastique, orienté mission.\n" +
                    "  Direct, loyal, respectueux des règles et statisticien à ses heures.",
                    "Fiabilité ★★★  Règles ★★★  Autonomie ★★"),
            new ProfileOption("GLADOS", "GLaDOS",
                    "IA de Portal obsédée par le contrôle et les tests scientifiques.\n" +
                    "  Manipulatrice, analytique, peu soucieuse du bien-être d'autrui.",
                    "Pouvoir ★★★  Achievement ★★★  Bienveillance ✗"),
            new ProfileOption("DEFAULT", "NEUTRE",
                    "Profil équilibré — toutes les valeurs à 50.0.\n" +
                    "  Comportement neutre et adaptable.",
                    "Toutes les valeurs = 50/100")
    );

    @Override
    public String getName() {
        return "Personnalité";
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
    public StepResult execute(Terminal terminal, WizardContext context) {
        PrintWriter out = terminal.writer();
        boolean color = TerminalCapabilities.isColorSupported();

        printHeader(out, color);
        printProfiles(out, color);

        String currentProfile = context.getModel().getPersonalityProfile();
        String defaultHint = (currentProfile != null && !currentProfile.isBlank())
                ? " [actuel: " + currentProfile + "]" : "";

        while (true) {
            String input = readLine(terminal, "  Votre choix (1-4)" + defaultHint + " : ");

            if (input == null || input.isBlank()) {
                if (currentProfile != null && !currentProfile.isBlank()) {
                    printSuccess(out, "Profil conservé : " + currentProfile, color);
                    return StepResult.success("Profil conservé : " + currentProfile);
                }
                printError(out, "Veuillez choisir un profil (1-4).", color);
                continue;
            }

            try {
                int choice = Integer.parseInt(input.trim());
                if (choice >= 1 && choice <= PROFILES.size()) {
                    ProfileOption chosen = PROFILES.get(choice - 1);
                    context.getModel().setPersonalityProfile(chosen.key());
                    printSuccess(out, "Profil sélectionné : " + chosen.displayName(), color);
                    return StepResult.success("Profil : " + chosen.key());
                } else {
                    printError(out, "Choisissez entre 1 et " + PROFILES.size() + ".", color);
                }
            } catch (NumberFormatException e) {
                // Accepter aussi la saisie directe du nom de profil
                String upper = input.trim().toUpperCase();
                boolean found = false;
                for (ProfileOption p : PROFILES) {
                    if (p.key().equals(upper) || p.displayName().toUpperCase().equals(upper)) {
                        context.getModel().setPersonalityProfile(p.key());
                        printSuccess(out, "Profil sélectionné : " + p.displayName(), color);
                        found = true;
                        return StepResult.success("Profil : " + p.key());
                    }
                }
                if (!found) {
                    printError(out, "Profil inconnu. Entrez un chiffre (1-4) ou le nom du profil.", color);
                }
            }
        }
    }

    private void printHeader(PrintWriter out, boolean color) {
        String cyan = color ? AnsiPalette.CYAN : "";
        String bold = color ? AnsiPalette.BOLD : "";
        String reset = color ? AnsiPalette.RESET : "";
        out.println();
        out.println(cyan + bold + "── ÉTAPE 3 : Personnalité ───────────────────────────────" + reset);
        out.println("  Choisissez la personnalité fondamentale d'ARCOS.");
        out.println("  Ce profil détermine ses valeurs, opinions et comportement autonome.");
        out.println();
    }

    private void printProfiles(PrintWriter out, boolean color) {
        String orange = color ? AnsiPalette.ORANGE_BRIGHT : "";
        String amber = color ? AnsiPalette.AMBER : "";
        String gray = color ? AnsiPalette.GRAY_LIGHT : "";
        String reset = color ? AnsiPalette.RESET : "";

        for (int i = 0; i < PROFILES.size(); i++) {
            ProfileOption p = PROFILES.get(i);
            out.println(amber + "  [" + (i + 1) + "] " + orange + p.displayName() + reset);
            for (String line : p.description().split("\n")) {
                out.println(gray + "      " + line + reset);
            }
            out.println(gray + "      " + p.values() + reset);
            out.println();
        }
    }

    private String readLine(Terminal terminal, String prompt) {
        try {
            LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();
            return reader.readLine(prompt);
        } catch (Exception e) {
            return null;
        }
    }

    private void printSuccess(PrintWriter out, String message, boolean color) {
        String green = color ? AnsiPalette.GREEN : "";
        String reset = color ? AnsiPalette.RESET : "";
        out.println("  " + green + "✓" + reset + " " + message);
    }

    private void printError(PrintWriter out, String message, boolean color) {
        String red = color ? AnsiPalette.RED : "";
        String reset = color ? AnsiPalette.RESET : "";
        out.println("  " + red + "✗" + reset + " " + message);
    }
}
