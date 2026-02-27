package org.arcos.Setup.UI;

/**
 * Détection des capacités du terminal courant.
 * Utilisé pour désactiver les codes ANSI si le terminal ne les supporte pas.
 */
public final class TerminalCapabilities {

    private TerminalCapabilities() {}

    /**
     * Retourne true si le terminal supporte les couleurs ANSI.
     * False si TERM=dumb, si pas de TTY, ou si CLICOLOR=false.
     */
    public static boolean isColorSupported() {
        if ("false".equalsIgnoreCase(System.getenv("CLICOLOR"))) {
            return false;
        }
        if ("dumb".equalsIgnoreCase(System.getenv("TERM"))) {
            return false;
        }
        return System.console() != null;
    }

    /**
     * Retourne la largeur du terminal, ou 80 par défaut.
     */
    public static int getTerminalWidth() {
        String columns = System.getenv("COLUMNS");
        if (columns != null) {
            try {
                int w = Integer.parseInt(columns.trim());
                if (w > 20 && w < 500) return w;
            } catch (NumberFormatException ignored) {}
        }
        return 80;
    }

    /**
     * Supprime les codes ANSI d'une chaîne.
     * Utile pour les rendus non-couleur (logs, tests).
     */
    public static String strip(String text) {
        if (text == null) return null;
        return text.replaceAll("\033\\[[0-9;]*m", "");
    }

    /**
     * Applique la couleur uniquement si supportée.
     */
    public static String colorize(String ansiCode, String text) {
        if (isColorSupported()) {
            return ansiCode + text + AnsiPalette.RESET;
        }
        return text;
    }
}
