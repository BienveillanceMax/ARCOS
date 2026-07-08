package org.arcos.Setup.UI;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import org.arcos.Setup.UI.WizardDisplay.MenuItem;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntConsumer;

/**
 * Interactive selection menu on the Lanterna Screen layer.
 * One item per row: selection bar (DEEP red background) + ▸ marker,
 * arrow/j/k navigation, digit accelerators, Enter to select, Esc to back out.
 * After selection the menu freezes: chosen row keeps an OK-colored marker,
 * the others drop to DIM — the menu itself becomes the record of the choice.
 */
public final class LanternaMenu {

    private LanternaMenu() {}

    /**
     * Runs the menu interaction loop. Blocking.
     *
     * @param startRow    absolute screen row of the first item
     * @param items       menu entries (max 9 — digit accelerators)
     * @param defaultIndex initially highlighted index (clamped)
     * @param onHighlight fired for the initial highlight and each navigation change; may be null
     * @return selected index, or {@link WizardDisplay#MENU_BACK} on Esc
     */
    public static int run(Screen screen, TextGraphics tg,
                          LayoutCalculator.ScreenLayout layout, LanternaPalette palette,
                          ReentrantLock lock, int startRow,
                          List<MenuItem> items, int defaultIndex,
                          IntConsumer onHighlight) {
        int selected = Math.max(0, Math.min(items.size() - 1, defaultIndex));
        int labelWidth = maxLabelWidth(items);

        drawAll(screen, tg, layout, palette, lock, startRow, items, selected, labelWidth);
        if (onHighlight != null) onHighlight.accept(selected);

        while (true) {
            KeyStroke key;
            try {
                key = screen.readInput();
            } catch (IOException e) {
                return selected;
            }

            int previous = selected;
            switch (key.getKeyType()) {
                case ArrowUp -> selected = (selected + items.size() - 1) % items.size();
                case ArrowDown -> selected = (selected + 1) % items.size();
                case Enter -> {
                    freeze(screen, tg, layout, palette, lock, startRow, items, selected, labelWidth);
                    return selected;
                }
                case Escape -> {
                    return WizardDisplay.MENU_BACK;
                }
                case EOF -> {
                    return selected;
                }
                case Character -> {
                    char c = key.getCharacter();
                    if (c == '\u0003') System.exit(130); // Ctrl+C
                    if (c == 'k' || c == 'K') selected = (selected + items.size() - 1) % items.size();
                    else if (c == 'j' || c == 'J') selected = (selected + 1) % items.size();
                    else if (c >= '1' && c < '1' + items.size()) {
                        selected = c - '1';
                        freeze(screen, tg, layout, palette, lock, startRow, items, selected, labelWidth);
                        return selected;
                    }
                }
                default -> { /* ignore */ }
            }

            if (selected != previous) {
                drawAll(screen, tg, layout, palette, lock, startRow, items, selected, labelWidth);
                if (onHighlight != null) onHighlight.accept(selected);
            }
        }
    }

    /** Redraws the whole menu with the active selection bar. */
    private static void drawAll(Screen screen, TextGraphics tg,
                                LayoutCalculator.ScreenLayout layout, LanternaPalette palette,
                                ReentrantLock lock, int startRow,
                                List<MenuItem> items, int selected, int labelWidth) {
        lock.lock();
        try {
            for (int i = 0; i < items.size(); i++) {
                drawItem(tg, layout, palette, startRow + i, items.get(i), labelWidth,
                        i == selected ? ItemState.SELECTED : ItemState.IDLE);
            }
            screen.refresh();
        } catch (IOException ignored) {
        } finally {
            lock.unlock();
        }
    }

    /** Final render after selection: chosen row marked, others dimmed. */
    private static void freeze(Screen screen, TextGraphics tg,
                               LayoutCalculator.ScreenLayout layout, LanternaPalette palette,
                               ReentrantLock lock, int startRow,
                               List<MenuItem> items, int selected, int labelWidth) {
        lock.lock();
        try {
            for (int i = 0; i < items.size(); i++) {
                drawItem(tg, layout, palette, startRow + i, items.get(i), labelWidth,
                        i == selected ? ItemState.CHOSEN : ItemState.DISCARDED);
            }
            screen.refresh();
        } catch (IOException ignored) {
        } finally {
            lock.unlock();
        }
    }

    private enum ItemState { SELECTED, IDLE, CHOSEN, DISCARDED }

    /**
     * Draws one menu row inside the frame:
     * {@code ┃   ▸ LABEL        annotation                  ┃}
     * SELECTED rows carry a DEEP red background bar across the content area.
     */
    private static void drawItem(TextGraphics tg, LayoutCalculator.ScreenLayout layout,
                                 LanternaPalette palette, int row, MenuItem item,
                                 int labelWidth, ItemState state) {
        int x = layout.leftMargin();
        int w = layout.frameWidth();
        int barStart = x + 3;               // selection bar starts after border + 2-space gap
        int barEnd = x + w - 3;             // symmetric gap before right border
        int barWidth = barEnd - barStart;

        TextColor background = state == ItemState.SELECTED ? palette.deep() : TextColor.ANSI.DEFAULT;
        TextColor markerColor = switch (state) {
            case SELECTED -> palette.bright();
            case CHOSEN -> palette.ok();
            default -> background;          // invisible marker cell
        };
        TextColor labelColor = switch (state) {
            case SELECTED, CHOSEN -> palette.text();
            case IDLE -> palette.muted();
            case DISCARDED -> palette.dim();
        };
        TextColor annotationColor = switch (state) {
            case SELECTED -> palette.muted();
            case CHOSEN -> palette.muted();
            case IDLE -> palette.dim();
            case DISCARDED -> palette.dim();
        };

        // Left border + gap
        tg.setBackgroundColor(TextColor.ANSI.DEFAULT);
        tg.setForegroundColor(palette.primary());
        tg.putString(x, row, "┃");
        tg.putString(x + 1, row, "  ");

        // Bar fill
        tg.setBackgroundColor(background);
        tg.putString(barStart, row, " ".repeat(barWidth));

        // Marker + label + annotation
        tg.setForegroundColor(markerColor);
        tg.putString(barStart + 1, row, state == ItemState.SELECTED || state == ItemState.CHOSEN ? "▸" : " ");

        String label = item.label();
        tg.setForegroundColor(labelColor);
        tg.putString(barStart + 3, row, label);

        if (item.annotation() != null && !item.annotation().isEmpty()) {
            int annotationX = barStart + 3 + labelWidth + 3;
            int maxLen = barEnd - annotationX;
            if (maxLen > 0) {
                String annotation = item.annotation();
                if (annotation.length() > maxLen) annotation = annotation.substring(0, maxLen);
                tg.setForegroundColor(annotationColor);
                tg.putString(annotationX, row, annotation);
            }
        }

        // Gap + right border
        tg.setBackgroundColor(TextColor.ANSI.DEFAULT);
        tg.putString(barEnd, row, "  ");
        tg.setForegroundColor(palette.primary());
        tg.putString(x + w - 1, row, "┃");
    }

    private static int maxLabelWidth(List<MenuItem> items) {
        int max = 0;
        for (MenuItem item : items) {
            max = Math.max(max, item.label().length());
        }
        return max;
    }
}
