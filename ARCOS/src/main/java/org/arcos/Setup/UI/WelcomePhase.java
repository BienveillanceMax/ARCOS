package org.arcos.Setup.UI;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import org.arcos.Setup.ConfigurationModel;
import org.arcos.Setup.Detection.ConfigurationDetector;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Welcome screen phase: always shown after the banner.
 * If config exists: shows summary + [1] BOOT / [2] RECONFIGURE choice.
 * If no config: auto-enters wizard (returns RECONFIGURE).
 */
public final class WelcomePhase {

    private WelcomePhase() {}

    /**
     * Shows the welcome screen and returns the user's choice.
     */
    public static WelcomeResult show(Screen screen) {
        TextGraphics tg = screen.newTextGraphics();
        LanternaPalette palette = LanternaPalette.DEFAULT;
        ReentrantLock lock = new ReentrantLock();

        int termWidth = screen.getTerminalSize().getColumns();
        int termHeight = screen.getTerminalSize().getRows();
        LayoutCalculator.ScreenLayout layout = LayoutCalculator.calculate(termWidth, termHeight);

        // Clear interior
        clearInterior(tg, layout, palette);

        // Draw panel divider
        drawWelcomeDivider(tg, layout, palette);

        ConfigurationDetector detector = new ConfigurationDetector();
        boolean configComplete = detector.isConfigurationComplete();

        if (!configComplete) {
            return showNoConfig(tg, screen, layout, palette, lock);
        }

        ConfigurationModel config = detector.loadExistingConfiguration();
        return showConfigSummary(tg, screen, layout, palette, config, lock);
    }

    private static WelcomeResult showNoConfig(TextGraphics tg, Screen screen,
                                              LayoutCalculator.ScreenLayout layout,
                                              LanternaPalette palette, ReentrantLock lock) {
        int startRow = layout.panelContentStart();

        lock.lock();
        try {
            LanternaComponents.drawContentRow(tg, layout, startRow,
                    "No configuration detected.", palette.text(), palette);
            LanternaComponents.drawContentRow(tg, layout, startRow + 1,
                    "Entering setup wizard...", palette.muted(), palette);
            screen.refresh();
        } catch (IOException ignored) {
        } finally {
            lock.unlock();
        }

        Animations.sleep(1000);
        return WelcomeResult.RECONFIGURE;
    }

    private static WelcomeResult showConfigSummary(TextGraphics tg, Screen screen,
                                                   LayoutCalculator.ScreenLayout layout,
                                                   LanternaPalette palette,
                                                   ConfigurationModel config,
                                                   ReentrantLock lock) {
        int startRow = layout.panelContentStart();
        int row = startRow;
        int stagger = 60;

        // "Configuration detected:" header
        drawRowWithStagger(tg, screen, layout, palette, row,
                "Configuration detected:", palette.text(), stagger, lock);
        row += 2;

        // ANIMA (personality)
        String personality = config.getPersonalityProfile() != null ? config.getPersonalityProfile() : "DEFAULT";
        drawStatusRow(tg, screen, layout, row, "ANIMA", personality, null, palette.ok(), palette, stagger, lock);
        row++;

        // LLM (Mistral key)
        String llmValue;
        TextColor llmColor;
        if (config.getMistralApiKey() != null && !config.getMistralApiKey().isBlank()) {
            String key = config.getMistralApiKey();
            String masked = "Mistral (" + maskKey(key) + ")";
            llmValue = masked;
            llmColor = palette.ok();
        } else {
            llmValue = "not configured";
            llmColor = palette.warn();
        }
        drawStatusRow(tg, screen, layout, row, "LLM", llmValue, null, llmColor, palette, stagger, lock);
        row++;

        // VOX (audio device)
        String voxValue = config.getAudioDeviceIndex() >= 0
                ? "index " + config.getAudioDeviceIndex()
                : "auto";
        drawStatusRow(tg, screen, layout, row, "VOX", voxValue, null, palette.info(), palette, stagger, lock);
        row++;

        // BRAVE
        String braveValue = (config.getBraveSearchApiKey() != null && !config.getBraveSearchApiKey().isBlank())
                ? "configured" : "not configured";
        TextColor braveColor = (config.getBraveSearchApiKey() != null && !config.getBraveSearchApiKey().isBlank())
                ? palette.ok() : palette.muted();
        drawStatusRow(tg, screen, layout, row, "BRAVE", braveValue, null, braveColor, palette, stagger, lock);
        row++;

        // PORCUPINE
        String porcValue = (config.getPorcupineAccessKey() != null && !config.getPorcupineAccessKey().isBlank())
                ? "configured" : "not configured";
        TextColor porcColor = (config.getPorcupineAccessKey() != null && !config.getPorcupineAccessKey().isBlank())
                ? palette.ok() : palette.muted();
        drawStatusRow(tg, screen, layout, row, "PORCUPINE", porcValue, null, porcColor, palette, stagger, lock);
        row += 2;

        // Choice options
        lock.lock();
        try {
            LanternaComponents.drawContentRow(tg, layout, row,
                    "[1]  BOOT — proceed with current configuration", palette.text(), palette);
            row++;
            LanternaComponents.drawContentRow(tg, layout, row,
                    "[2]  RECONFIGURE — run setup wizard", palette.text(), palette);
            row += 2;

            // Prompt
            int cx = layout.leftMargin() + 4;
            tg.setForegroundColor(palette.primary());
            tg.putString(layout.leftMargin(), row, "┃");
            tg.putString(cx, row, "▸ ");
            tg.setForegroundColor(palette.text());
            // Pad rest
            int remaining = layout.leftMargin() + layout.frameWidth() - 1 - (cx + 2);
            if (remaining > 0) tg.putString(cx + 2, row, " ".repeat(remaining));
            tg.setForegroundColor(palette.primary());
            tg.putString(layout.leftMargin() + layout.frameWidth() - 1, row, "┃");

            screen.refresh();
        } catch (IOException ignored) {
        } finally {
            lock.unlock();
        }

        // Wait for input: 1 or 2
        while (true) {
            try {
                KeyStroke key = screen.readInput();
                if (key.getKeyType() == KeyType.Character) {
                    char c = key.getCharacter();
                    if (c == '1') return WelcomeResult.BOOT;
                    if (c == '2') return WelcomeResult.RECONFIGURE;
                }
                if (key.getKeyType() == KeyType.Enter) return WelcomeResult.BOOT;
                if (key.getKeyType() == KeyType.EOF) return WelcomeResult.BOOT;
            } catch (IOException e) {
                return WelcomeResult.BOOT;
            }
        }
    }

    private static void drawStatusRow(TextGraphics tg, Screen screen,
                                      LayoutCalculator.ScreenLayout layout, int row,
                                      String label, String value, String detail,
                                      TextColor statusColor, LanternaPalette palette,
                                      int staggerMs, ReentrantLock lock) {
        lock.lock();
        try {
            LanternaComponents.drawStatusLine(tg, layout, row, label, value, detail, statusColor, palette);
            screen.refresh();
        } catch (IOException ignored) {
        } finally {
            lock.unlock();
        }
        Animations.sleep(staggerMs);
    }

    private static void drawRowWithStagger(TextGraphics tg, Screen screen,
                                           LayoutCalculator.ScreenLayout layout,
                                           LanternaPalette palette, int row,
                                           String text, TextColor color,
                                           int staggerMs, ReentrantLock lock) {
        lock.lock();
        try {
            LanternaComponents.drawContentRow(tg, layout, row, text, color, palette);
            screen.refresh();
        } catch (IOException ignored) {
        } finally {
            lock.unlock();
        }
        Animations.sleep(staggerMs);
    }

    private static void drawWelcomeDivider(TextGraphics tg, LayoutCalculator.ScreenLayout layout,
                                           LanternaPalette palette) {
        int row = layout.panelDividerRow();
        int x = layout.leftMargin();
        int w = layout.frameWidth();
        String label = " VIGILIA ";
        int fillLen = w - 2 - label.length();
        if (fillLen < 0) fillLen = 0;

        tg.setForegroundColor(palette.primary());
        tg.putString(x, row, "┣━");
        tg.setForegroundColor(palette.bright());
        tg.putString(x + 2, row, label);
        tg.setForegroundColor(palette.primary());
        tg.putString(x + 2 + label.length(), row, "━".repeat(Math.max(0, fillLen - 1)) + "┫");
    }

    private static void clearInterior(TextGraphics tg, LayoutCalculator.ScreenLayout layout,
                                      LanternaPalette palette) {
        for (int r = layout.headerRow() + 1; r < layout.footerRow(); r++) {
            LanternaComponents.drawEmptyRow(tg, layout, r, palette);
        }
    }

    private static String maskKey(String key) {
        if (key.length() <= 8) return "****";
        return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
    }
}
