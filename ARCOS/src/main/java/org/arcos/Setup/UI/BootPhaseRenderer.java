package org.arcos.Setup.UI;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.screen.Screen;
import org.arcos.Setup.Boot.ServiceStatusEntry;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Resolves the COGITO launch sequence into its final state after Spring loads.
 * Draws directly below the existing probe rows — no panel clear, no separate screen.
 * Sequence: summary line → greeting typewriter → [Enter] prompt.
 */
public class BootPhaseRenderer {

    private final Screen screen;
    private final TextGraphics tg;
    private final LanternaPalette palette;
    private final LayoutCalculator.ScreenLayout layout;
    private final ReentrantLock lock;

    public BootPhaseRenderer(Screen screen) {
        this.screen = screen;
        this.tg = screen.newTextGraphics();
        this.palette = LanternaPalette.DEFAULT;
        this.layout = LayoutCalculator.calculate(screen.getTerminalSize());
        this.lock = new ReentrantLock();
    }

    /**
     * Renders the boot resolution into the existing COGITO screen.
     * Called after the decoration thread has been interrupted.
     *
     * @param entries         service status entries (for online/total count)
     * @param greetingMessage personality greeting (may contain ANSI codes, will be stripped)
     */
    public void render(List<ServiceStatusEntry> entries, String greetingMessage) {
        int summaryRow = layout.panelContentStart() + 8;

        // 1. Clear decoration row
        clearRow(summaryRow);

        // 2. Summary line — left: "X/Y ONLINE", right: "ALL SYSTEMS OPERATIONAL"
        long online = entries.stream().filter(ServiceStatusEntry::isOnline).count();
        int total = entries.size();
        boolean allOnline = online == total;

        String leftSummary = online + "/" + total + " ONLINE";
        TextColor summaryColor = allOnline ? palette.ok() : palette.warn();

        String rightSummary = allOnline
                ? "ALL SYSTEMS OPERATIONAL"
                : online + " SYSTEMS OPERATIONAL";

        int leftX = layout.leftMargin() + 4;
        Animations.typewriter(tg, screen, leftX, summaryRow,
                leftSummary, summaryColor, 25, lock);

        // Right-align the status text within the content area
        int rightX = layout.leftMargin() + layout.frameWidth() - 2 - rightSummary.length();
        Animations.scrambleDecode(tg, screen, rightX, summaryRow,
                rightSummary, palette.dim(), summaryColor, 400, lock,
                () -> false);

        Animations.sleepWithPoll(screen, 400);

        // 3. Greeting — typewriter in PRIMARY
        String plain = TerminalCapabilities.strip(greetingMessage).trim();
        int greetingRow = summaryRow + 2;
        int maxWidth = layout.contentWidth() - 4;

        // Word-wrap if needed (max 2 lines)
        String[] lines = wordWrap(plain, maxWidth);
        for (int i = 0; i < lines.length && i < 2; i++) {
            Animations.typewriter(tg, screen, leftX, greetingRow + i,
                    lines[i], palette.primary(), 30, lock);
        }

        Animations.sleepWithPoll(screen, 300);

        // 4. [Enter] prompt centered near footer
        String enterPrompt = "[Enter]";
        int enterRow = Math.min(greetingRow + lines.length + 2, layout.footerRow() - 1);
        int enterX = layout.leftMargin() + (layout.frameWidth() - enterPrompt.length()) / 2;

        lock.lock();
        try {
            tg.setForegroundColor(palette.muted());
            tg.putString(enterX, enterRow, enterPrompt);
            screen.refresh();
        } catch (IOException ignored) {
        } finally {
            lock.unlock();
        }

        // 5. Wait for keypress
        Animations.waitForEnter(screen);
    }

    private void clearRow(int row) {
        lock.lock();
        try {
            LanternaComponents.drawEmptyRow(tg, layout, row, palette);
            screen.refresh();
        } catch (IOException ignored) {
        } finally {
            lock.unlock();
        }
    }

    private String[] wordWrap(String text, int maxWidth) {
        if (text.length() <= maxWidth) {
            return new String[]{text};
        }
        int breakAt = text.lastIndexOf(' ', maxWidth);
        if (breakAt <= 0) breakAt = maxWidth;

        String line1 = text.substring(0, breakAt).trim();
        String line2 = "   " + text.substring(breakAt).trim();
        return new String[]{line1, line2};
    }
}
