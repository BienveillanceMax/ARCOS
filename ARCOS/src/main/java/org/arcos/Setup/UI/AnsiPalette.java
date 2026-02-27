package org.arcos.Setup.UI;

/**
 * Palette de couleurs ANSI 256 centralisée pour tous les affichages terminal d'ARCOS.
 * Usage : AnsiPalette.ORANGE_BRIGHT + "mon texte" + AnsiPalette.RESET
 * En mode non-couleur (TTY absent, TERM=dumb), utiliser TerminalCapabilities.strip().
 */
public final class AnsiPalette {

    private AnsiPalette() {}

    public static final String RESET        = "\033[0m";
    public static final String BOLD         = "\033[1m";

    // Palette feu — couleurs principales d'ARCOS
    public static final String ORANGE_BRIGHT = "\033[38;5;208m"; // orange vif
    public static final String AMBER         = "\033[38;5;220m"; // ambre
    public static final String ORANGE_DARK   = "\033[38;5;130m"; // orange sombre (encadrement)

    // Statuts
    public static final String GREEN         = "\033[38;5;82m";  // ONLINE / succès
    public static final String RED           = "\033[38;5;196m"; // OFFLINE / erreur
    public static final String YELLOW        = "\033[38;5;226m"; // WARNING / dégradé

    // Texte
    public static final String CYAN          = "\033[38;5;87m";  // info / titres
    public static final String GRAY_LIGHT    = "\033[38;5;250m"; // texte secondaire
    public static final String WHITE         = "\033[38;5;255m"; // texte principal
}
