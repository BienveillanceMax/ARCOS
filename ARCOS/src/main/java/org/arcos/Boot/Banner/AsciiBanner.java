package org.arcos.Boot.Banner;

import org.arcos.Setup.UI.AnsiPalette;
import org.arcos.Setup.UI.TerminalCapabilities;

/**
 * Bannière ASCII d'ARCOS avec dégradé de couleurs feu.
 * Dégradé vertical : ambre (sommet des flammes) → rouge (base).
 * Fallback TERM=dumb : texte brut sans codes ANSI.
 */
public final class AsciiBanner {

    private AsciiBanner() {}

    // Lignes du logo ARCOS en ASCII art (blocs unicode)
    private static final String[] LOGO_LINES = {
        " █████╗ ██████╗  ██████╗ ██████╗ ███████╗",
        "██╔══██╗██╔══██╗██╔════╝██╔═══██╗██╔════╝",
        "███████║██████╔╝██║     ██║   ██║███████╗ ",
        "██╔══██║██╔══██╗██║     ██║   ██║╚════██║ ",
        "██║  ██║██║  ██║╚██████╗╚██████╔╝███████║ ",
        "╚═╝  ╚═╝╚═╝  ╚═╝ ╚═════╝ ╚═════╝╚══════╝ "
    };

    // Dégradé feu : ambre → orange vif → orange sombre → rouge
    private static final String[] FIRE_GRADIENT = {
        AnsiPalette.AMBER,
        AnsiPalette.ORANGE_BRIGHT,
        AnsiPalette.ORANGE_BRIGHT,
        AnsiPalette.ORANGE_DARK,
        AnsiPalette.ORANGE_DARK,
        AnsiPalette.RED
    };

    private static final int BANNER_WIDTH = 58;

    public static void print() {
        if (!TerminalCapabilities.isColorSupported()) {
            printPlain();
            return;
        }
        printColored();
    }

    private static void printColored() {
        String border = AnsiPalette.ORANGE_DARK + "─".repeat(BANNER_WIDTH) + AnsiPalette.RESET;
        System.out.println();
        System.out.println(border);
        System.out.println();

        for (int i = 0; i < LOGO_LINES.length; i++) {
            String color = FIRE_GRADIENT[i];
            System.out.println("  " + color + LOGO_LINES[i] + AnsiPalette.RESET);
        }

        System.out.println();
        String subtitle = AnsiPalette.GRAY_LIGHT + "  ARTIFICIAL COGNITIVE SYSTEM" + AnsiPalette.RESET;
        System.out.println(subtitle);
        System.out.println();
        System.out.println(border);
        System.out.println();
        System.out.flush();
    }

    private static void printPlain() {
        System.out.println();
        System.out.println("══════════════════════════════════════════════════════════");
        System.out.println();
        for (String line : LOGO_LINES) {
            System.out.println("  " + line);
        }
        System.out.println();
        System.out.println("  ARTIFICIAL COGNITIVE SYSTEM");
        System.out.println();
        System.out.println("══════════════════════════════════════════════════════════");
        System.out.println();
        System.out.flush();
    }
}
