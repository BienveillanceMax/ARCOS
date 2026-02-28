package org.arcos.Boot.Report;

import org.arcos.Boot.ServiceStatus;
import org.arcos.Boot.ServiceStatusEntry;
import org.arcos.Setup.UI.AnsiPalette;
import org.arcos.Setup.UI.TerminalCapabilities;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;

/**
 * Rendu du rapport de boot dans le terminal.
 * Affiche les statuts de services avec révélation séquentielle (100–150 ms/ligne).
 */
public class BootReportRenderer {

    private static final int DEFAULT_REVEAL_DELAY_MS = 120;
    private static final int NAME_COLUMN_WIDTH = 16;

    private final int revealDelayMs;

    public BootReportRenderer() {
        this(DEFAULT_REVEAL_DELAY_MS);
    }

    public BootReportRenderer(int revealDelayMs) {
        this.revealDelayMs = revealDelayMs;
    }

    /**
     * Affiche le rapport de boot avec révélation séquentielle.
     *
     * @param entries  liste des entrées de statuts
     * @param colorOn  true pour activer les codes ANSI
     */
    public void render(List<ServiceStatusEntry> entries, boolean colorOn) {
        Map<String, List<ServiceStatusEntry>> byCategory = groupByCategory(entries);

        printLine(colorOn ? sectionHeader("RAPPORT DE DÉMARRAGE") : "─── RAPPORT DE DÉMARRAGE ───", colorOn);
        printLine("", colorOn);

        for (Map.Entry<String, List<ServiceStatusEntry>> cat : byCategory.entrySet()) {
            printLine(categoryTitle(cat.getKey(), colorOn), colorOn);
            List<ServiceStatusEntry> catEntries = cat.getValue();
            for (int i = 0; i < catEntries.size(); i++) {
                boolean isLast = (i == catEntries.size() - 1);
                printLine(formatEntry(catEntries.get(i), isLast, colorOn), colorOn);
            }
            printLine("", colorOn);
        }

        // Section alertes
        List<ServiceStatusEntry> issues = entries.stream()
                .filter(e -> !e.isOnline())
                .collect(java.util.stream.Collectors.toList());
        if (!issues.isEmpty()) {
            printLine(colorOn ? AnsiPalette.YELLOW + "⚠ ATTENTION" + AnsiPalette.RESET : "⚠ ATTENTION", colorOn);
            for (int i = 0; i < issues.size(); i++) {
                boolean isLast = (i == issues.size() - 1);
                String prefix = isLast ? " └─ " : " ├─ ";
                String alertLine = prefix + issues.get(i).getName()
                        + " désactivé : " + issues.get(i).getDetail()
                        + " → Relancez avec --setup";
                printLine(colorOn ? AnsiPalette.YELLOW + alertLine + AnsiPalette.RESET : alertLine, colorOn);
            }
            printLine("", colorOn);
        }

        // Pied de page
        printLine(footer(entries, colorOn), colorOn);
        printLine("", colorOn);

        System.out.flush();
    }

    private String sectionHeader(String title) {
        String line = "─".repeat(54);
        return AnsiPalette.CYAN + AnsiPalette.BOLD + "  " + title + AnsiPalette.RESET
                + "\n" + AnsiPalette.ORANGE_DARK + "  " + line + AnsiPalette.RESET;
    }

    private String categoryTitle(String category, boolean colorOn) {
        if (!colorOn) return " " + category;
        return " " + AnsiPalette.GRAY_LIGHT + category + AnsiPalette.RESET;
    }

    private String formatEntry(ServiceStatusEntry entry, boolean isLast, boolean colorOn) {
        String prefix = isLast ? " └─ " : " ├─ ";
        String paddedName = padRight(entry.getName(), NAME_COLUMN_WIDTH);

        String statusSymbol;
        String statusColor;
        switch (entry.getStatus()) {
            case ONLINE:
                statusSymbol = "✓ ONLINE  ";
                statusColor = AnsiPalette.GREEN;
                break;
            case DEGRADED:
                statusSymbol = "⚠ DÉGRADÉ ";
                statusColor = AnsiPalette.YELLOW;
                break;
            default:
                statusSymbol = "✗ OFFLINE ";
                statusColor = AnsiPalette.RED;
        }

        if (!colorOn) {
            return prefix + paddedName + statusSymbol + entry.getDetail();
        }

        return prefix
                + AnsiPalette.WHITE + paddedName + AnsiPalette.RESET
                + statusColor + statusSymbol + AnsiPalette.RESET
                + AnsiPalette.GRAY_LIGHT + entry.getDetail() + AnsiPalette.RESET;
    }

    private String footer(List<ServiceStatusEntry> entries, boolean colorOn) {
        long online = entries.stream().filter(ServiceStatusEntry::isOnline).count();
        long issues = entries.stream().filter(e -> !e.isOnline()).count();
        int total = entries.size();

        String separator = colorOn ? AnsiPalette.ORANGE_DARK + "  " + "─".repeat(54) + AnsiPalette.RESET : "  " + "─".repeat(54);

        String tag;
        if (issues == 0) {
            tag = colorOn ? AnsiPalette.GREEN + "[ALL SYSTEMS GO]" + AnsiPalette.RESET : "[ALL SYSTEMS GO]";
        } else {
            tag = colorOn ? AnsiPalette.YELLOW + "[" + issues + " AVERTISSEMENT(S)]" + AnsiPalette.RESET : "[" + issues + " AVERTISSEMENT(S)]";
        }

        String summary = "  " + online + "/" + total + " SYSTÈMES ACTIFS · " + tag;
        if (colorOn) summary = "  " + AnsiPalette.WHITE + online + "/" + total + " SYSTÈMES ACTIFS" + AnsiPalette.RESET + " · " + tag;

        return separator + "\n" + summary;
    }

    private Map<String, List<ServiceStatusEntry>> groupByCategory(List<ServiceStatusEntry> entries) {
        Map<String, List<ServiceStatusEntry>> map = new LinkedHashMap<>();
        for (ServiceStatusEntry entry : entries) {
            map.computeIfAbsent(entry.getCategory(), k -> new ArrayList<>()).add(entry);
        }
        return map;
    }

    private void printLine(String line, boolean colorOn) {
        System.out.println(line);
        if (revealDelayMs > 0) {
            try {
                Thread.sleep(revealDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static String padRight(String s, int n) {
        if (s.length() >= n) return s.substring(0, n);
        return s + " ".repeat(n - s.length());
    }
}
