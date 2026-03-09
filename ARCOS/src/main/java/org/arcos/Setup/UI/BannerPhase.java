package org.arcos.Setup.UI;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.screen.Screen;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Animated banner phase on Lanterna screen.
 * Replaces AsciiBanner.print() for the full-screen path.
 * Choreography: borders → logo cascade → subtitle scramble-decode → hold.
 */
public final class BannerPhase {

    private BannerPhase() {}

    private static final String[] LOGO_LINES = {
        " \u2588\u2588\u2588\u2588\u2588\u2557 \u2588\u2588\u2588\u2588\u2588\u2588\u2557 \u2588\u2588\u2588\u2588\u2588\u2588\u2557 \u2588\u2588\u2588\u2588\u2588\u2588\u2557 \u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2557",
        "\u2588\u2588\u2554\u2550\u2550\u2588\u2588\u2557\u2588\u2588\u2554\u2550\u2550\u2588\u2588\u2557\u2588\u2588\u2554\u2550\u2550\u2550\u2550\u255D\u2588\u2588\u2554\u2550\u2550\u2550\u2588\u2588\u2557\u2588\u2588\u2554\u2550\u2550\u2550\u2550\u255D",
        "\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2551\u2588\u2588\u2588\u2588\u2588\u2588\u2554\u255D\u2588\u2588\u2551     \u2588\u2588\u2551   \u2588\u2588\u2551\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2557 ",
        "\u2588\u2588\u2554\u2550\u2550\u2588\u2588\u2551\u2588\u2588\u2554\u2550\u2550\u2588\u2588\u2557\u2588\u2588\u2551     \u2588\u2588\u2551   \u2588\u2588\u2551\u255A\u2550\u2550\u2550\u2550\u2588\u2588\u2551 ",
        "\u2588\u2588\u2551  \u2588\u2588\u2551\u2588\u2588\u2551  \u2588\u2588\u2551\u255A\u2588\u2588\u2588\u2588\u2588\u2588\u2557\u255A\u2588\u2588\u2588\u2588\u2588\u2588\u2554\u255D\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2551 ",
        "\u255A\u2550\u255D  \u255A\u2550\u255D\u255A\u2550\u255D  \u255A\u2550\u255D \u255A\u2550\u2550\u2550\u2550\u2550\u255D \u255A\u2550\u2550\u2550\u2550\u2550\u255D\u255A\u2550\u2550\u2550\u2550\u2550\u2550\u255D "
    };

    private static final String SUBTITLE = "ARTIFICIAL COGNITIVE SYSTEM";

    // Red gradient: bright → bright → primary → primary → deep → deep
    private static final int[] GRADIENT_INDICES = {196, 196, 160, 160, 52, 52};

    /**
     * Renders the animated banner into the given screen.
     */
    public static void render(Screen screen) {
        TextGraphics tg = screen.newTextGraphics();
        LanternaPalette palette = LanternaPalette.DEFAULT;
        ReentrantLock lock = new ReentrantLock();

        int termWidth = screen.getTerminalSize().getColumns();
        int termHeight = screen.getTerminalSize().getRows();
        LayoutCalculator.ScreenLayout layout = LayoutCalculator.calculate(termWidth, termHeight);

        // Calculate centered position for logo
        int logoWidth = LOGO_LINES[0].length();
        int logoX = Math.max(0, (termWidth - logoWidth) / 2);
        int logoStartY = Math.max(0, (termHeight - LOGO_LINES.length - 4) / 2);

        // T+0ms: Frame borders draw instantly
        lock.lock();
        try {
            LanternaComponents.drawHeaderBar(tg, layout, palette);
            LanternaComponents.drawFooter(tg, layout, palette);
            // Draw side borders for all rows
            for (int r = layout.headerRow() + 1; r < layout.footerRow(); r++) {
                int x = layout.leftMargin();
                int w = layout.frameWidth();
                tg.setForegroundColor(palette.primary());
                tg.putString(x, r, "┃");
                tg.putString(x + w - 1, r, "┃");
            }
            screen.refresh();
        } catch (IOException ignored) {
        } finally {
            lock.unlock();
        }
        Animations.sleep(100);

        // T+100ms: Logo lines cascade top-to-bottom with red gradient
        for (int i = 0; i < LOGO_LINES.length; i++) {
            TextColor lineColor = new TextColor.Indexed(GRADIENT_INDICES[i % GRADIENT_INDICES.length]);
            lock.lock();
            try {
                tg.setForegroundColor(lineColor);
                tg.putString(logoX, logoStartY + i, LOGO_LINES[i]);
                screen.refresh();
            } catch (IOException ignored) {
            } finally {
                lock.unlock();
            }
            Animations.sleep(80);
        }

        // T+~600ms: Subtitle scramble-decodes
        Animations.sleep(100);
        int subtitleX = Math.max(0, (termWidth - SUBTITLE.length()) / 2);
        int subtitleY = logoStartY + LOGO_LINES.length + 1;
        Animations.scrambleDecode(tg, screen, subtitleX, subtitleY,
                SUBTITLE, palette.dim(), palette.muted(), 400, lock);

        // T+~1200ms: Show prompt and wait for Enter
        String prompt = "[Setup Ready - Press Enter]";
        int promptX = layout.leftMargin() + (layout.frameWidth() - prompt.length()) / 2;
        int promptRow = layout.footerRow() - 2;

        lock.lock();
        try {
            tg.setForegroundColor(palette.muted());
            tg.putString(promptX, promptRow, prompt);
            screen.refresh();
        } catch (IOException ignored) {
        } finally {
            lock.unlock();
        }

        Animations.waitForEnter(screen);
    }
}
