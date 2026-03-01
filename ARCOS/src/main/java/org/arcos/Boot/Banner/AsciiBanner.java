package org.arcos.Boot.Banner;

import org.arcos.Setup.UI.AnsiPalette;
import org.arcos.Setup.UI.TerminalCapabilities;

/**
 * ASCII banner for ARCOS with red identity gradient.
 * Gradient: BRIGHT → BRIGHT → PRIMARY → PRIMARY → DEEP → DEEP
 * Fallback TERM=dumb: plain text without ANSI codes.
 */
public final class AsciiBanner {

    private AsciiBanner() {}

    private static final String[] LOGO_LINES = {
        " █████╗ ██████╗  ██████╗ ██████╗ ███████╗",
        "██╔══██╗██╔══██╗██╔════╝██╔═══██╗██╔════╝",
        "███████║██████╔╝██║     ██║   ██║███████╗ ",
        "██╔══██║██╔══██╗██║     ██║   ██║╚════██║ ",
        "██║  ██║██║  ██║╚██████╗╚██████╔╝███████║ ",
        "╚═╝  ╚═╝╚═╝  ╚═╝ ╚═════╝ ╚═════╝╚══════╝ "
    };

    // Red identity gradient: bright → primary → deep
    private static final String[] RED_GRADIENT = {
        AnsiPalette.BRIGHT,
        AnsiPalette.BRIGHT,
        AnsiPalette.PRIMARY,
        AnsiPalette.PRIMARY,
        AnsiPalette.DEEP,
        AnsiPalette.DEEP
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
        String border = AnsiPalette.PRIMARY + "─".repeat(BANNER_WIDTH) + AnsiPalette.RESET;
        System.out.println();
        System.out.println(border);
        System.out.println();

        for (int i = 0; i < LOGO_LINES.length; i++) {
            String color = RED_GRADIENT[i];
            System.out.println("  " + color + LOGO_LINES[i] + AnsiPalette.RESET);
        }

        System.out.println();
        String subtitle = AnsiPalette.MUTED + "  ARTIFICIAL COGNITIVE SYSTEM" + AnsiPalette.RESET;
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
