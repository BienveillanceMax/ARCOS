package org.arcos.Setup.Steps;

import org.arcos.Setup.UI.AnsiPalette;
import org.arcos.Setup.UI.TerminalCapabilities;
import org.arcos.Setup.Validation.AudioDeviceEnumerator;
import org.arcos.Setup.WizardContext;
import org.arcos.Setup.WizardStep;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;

import java.io.PrintWriter;
import java.util.List;

/**
 * Étape 2 du wizard : sélection du microphone parmi les périphériques compatibles.
 * - Auto-sélection si un seul périphérique disponible
 * - Menu numéroté si plusieurs périphériques
 * - Sauvegarde arcos.audio.input-device-index dans WizardContext
 */
public class AudioDeviceStep implements WizardStep {

    @Override
    public String getName() {
        return "Microphone";
    }

    @Override
    public boolean isRequired() {
        return true;
    }

    @Override
    public boolean isSkippable() {
        return true;
    }

    @Override
    public StepResult execute(Terminal terminal, WizardContext context) {
        PrintWriter out = terminal.writer();
        boolean color = TerminalCapabilities.isColorSupported();

        printHeader(out, color);

        List<AudioDeviceEnumerator.AudioDevice> devices = AudioDeviceEnumerator.getInputDevices();

        if (devices.isEmpty()) {
            printWarning(out, "Aucun périphérique audio d'entrée détecté.", color);
            out.println("  ARCOS utilisera la sélection automatique du système.");
            out.println("  Vous pouvez configurer l'index manuellement dans application-local.yaml.");
            context.getModel().setAudioDeviceIndex(-1);
            context.addWarning("Aucun microphone détecté — index audio = -1 (auto)");
            return StepResult.success("Aucun microphone détecté — mode auto.");
        }

        if (devices.size() == 1) {
            AudioDeviceEnumerator.AudioDevice device = devices.get(0);
            context.getModel().setAudioDeviceIndex(device.index());
            String msg = String.format("✓ Micro auto-sélectionné : %s (index %d)", device.name(), device.index());
            printSuccess(out, msg, color);
            return StepResult.success(msg);
        }

        // Plusieurs périphériques : afficher un menu
        out.println("  Périphériques d'entrée compatibles détectés :");
        out.println();
        for (AudioDeviceEnumerator.AudioDevice device : devices) {
            String cyan = color ? AnsiPalette.CYAN : "";
            String reset = color ? AnsiPalette.RESET : "";
            out.printf("  %s[%d]%s %s%n", cyan, device.index(), reset, device.name());
        }
        out.println();

        // Lire le choix
        int currentIndex = context.getModel().getAudioDeviceIndex();
        String defaultHint = currentIndex >= 0 ? " [actuel: " + currentIndex + "]" : "";

        while (true) {
            String input = readLine(terminal, "  Index du microphone" + defaultHint + " : ");
            if (input == null || input.isBlank()) {
                if (currentIndex >= 0) {
                    printSuccess(out, "Index conservé : " + currentIndex, color);
                    return StepResult.success("Microphone conservé : index " + currentIndex);
                }
                printError(out, "Veuillez entrer un index valide.", color);
                continue;
            }
            if ("s".equalsIgnoreCase(input.trim()) || "skip".equalsIgnoreCase(input.trim())) {
                context.getModel().setAudioDeviceIndex(-1);
                return StepResult.skipped("Sélection audio ignorée — mode auto.");
            }
            try {
                int idx = Integer.parseInt(input.trim());
                AudioDeviceEnumerator.AudioDevice chosen = AudioDeviceEnumerator.getDeviceAt(idx);
                if (chosen != null) {
                    context.getModel().setAudioDeviceIndex(idx);
                    printSuccess(out, "Microphone sélectionné : " + chosen.name() + " (index " + idx + ")", color);
                    return StepResult.success("Microphone configuré : index " + idx);
                } else {
                    printError(out, "Index " + idx + " introuvable. Choisissez parmi les index listés.", color);
                }
            } catch (NumberFormatException e) {
                printError(out, "Entrez un nombre entier ou 's' pour passer.", color);
            }
        }
    }

    private void printHeader(PrintWriter out, boolean color) {
        String cyan = color ? AnsiPalette.CYAN : "";
        String bold = color ? AnsiPalette.BOLD : "";
        String reset = color ? AnsiPalette.RESET : "";
        out.println();
        out.println(cyan + bold + "── ÉTAPE 2 : Microphone ─────────────────────────────────" + reset);
        out.println("  Sélectionnez le microphone utilisé par ARCOS pour détecter le mot de réveil.");
        out.println();
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

    private void printWarning(PrintWriter out, String message, boolean color) {
        String yellow = color ? AnsiPalette.YELLOW : "";
        String reset = color ? AnsiPalette.RESET : "";
        out.println("  " + yellow + "⚠" + reset + " " + message);
    }

    private void printError(PrintWriter out, String message, boolean color) {
        String red = color ? AnsiPalette.RED : "";
        String reset = color ? AnsiPalette.RESET : "";
        out.println("  " + red + "✗" + reset + " " + message);
    }
}
