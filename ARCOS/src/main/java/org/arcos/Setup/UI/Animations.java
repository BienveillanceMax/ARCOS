package org.arcos.Setup.UI;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.screen.Screen;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

/**
 * Animation primitives for Lanterna Screen.
 * All methods are blocking and handle their own Thread.sleep timing.
 * Thread safety via ReentrantLock for concurrent animations (e.g. spinner + main thread).
 */
public final class Animations {

    private static final Random RNG = new Random();
    private static final String GLYPH_POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*(){}[]|/<>~";
    static final char[] SPINNER_FRAMES = {'◒', '◐', '◓', '◑'};

    private Animations() {}

    /**
     * Typewriter effect: character-by-character reveal with punctuation pauses.
     */
    public static void typewriter(TextGraphics tg, Screen screen, int x, int y,
                                  String text, TextColor color, int delayMs,
                                  ReentrantLock lock) {
        tg.setForegroundColor(color);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            lock.lock();
            try {
                tg.putString(x + i, y, String.valueOf(c));
                screen.refresh();
            } catch (IOException ignored) {
            } finally {
                lock.unlock();
            }
            int pause = delayMs;
            if (c == '.' || c == ',' || c == '!' || c == '?' || c == ':' || c == ';') {
                pause = delayMs * 4;
            } else if (c == ' ') {
                pause = delayMs / 2;
            }
            sleep(pause);
        }
    }

    /**
     * Scramble-decode effect: random glyphs resolve character-by-character into final text.
     */
    public static void scrambleDecode(TextGraphics tg, Screen screen, int x, int y,
                                      String text, TextColor scrambleColor, TextColor resolvedColor,
                                      int totalDurationMs, ReentrantLock lock) {
        scrambleDecode(tg, screen, x, y, text, scrambleColor, resolvedColor, totalDurationMs, lock, null);
    }

    /**
     * Scramble-decode effect with keyboard polling between ticks.
     * The inputHandler is called every tick; if it returns true, the animation aborts early
     * (final resolved state is still drawn).
     */
    public static void scrambleDecode(TextGraphics tg, Screen screen, int x, int y,
                                      String text, TextColor scrambleColor, TextColor resolvedColor,
                                      int totalDurationMs, ReentrantLock lock,
                                      java.util.function.BooleanSupplier inputHandler) {
        int len = text.length();
        if (len == 0) return;

        int tickMs = 30;
        int totalTicks = Math.max(1, totalDurationMs / tickMs);
        char[] current = new char[len];

        // Fill with random glyphs initially
        for (int i = 0; i < len; i++) {
            current[i] = randomGlyph();
        }

        for (int tick = 0; tick <= totalTicks; tick++) {
            // Poll keyboard between ticks
            if (inputHandler != null && inputHandler.getAsBoolean()) {
                break; // Abort animation early
            }

            float progress = (float) tick / totalTicks;
            int resolvedCount = Math.round(progress * len);

            lock.lock();
            try {
                for (int i = 0; i < len; i++) {
                    if (i < resolvedCount) {
                        tg.setForegroundColor(resolvedColor);
                        tg.putString(x + i, y, String.valueOf(text.charAt(i)));
                    } else {
                        current[i] = randomGlyph();
                        tg.setForegroundColor(scrambleColor);
                        tg.putString(x + i, y, String.valueOf(current[i]));
                    }
                }
                screen.refresh();
            } catch (IOException ignored) {
            } finally {
                lock.unlock();
            }
            sleep(tickMs);
        }

        // Final resolved state
        lock.lock();
        try {
            tg.setForegroundColor(resolvedColor);
            tg.putString(x, y, text);
            screen.refresh();
        } catch (IOException ignored) {
        } finally {
            lock.unlock();
        }
    }

    /**
     * Cascade effect: staggered row reveal.
     *
     * @param rowRenderer called with (rowIndex, TextGraphics) for each row
     */
    public static void cascade(TextGraphics tg, Screen screen, int rowCount,
                               int delayPerRowMs, BiConsumer<Integer, TextGraphics> rowRenderer,
                               ReentrantLock lock) {
        for (int i = 0; i < rowCount; i++) {
            lock.lock();
            try {
                rowRenderer.accept(i, tg);
                screen.refresh();
            } catch (IOException ignored) {
            } finally {
                lock.unlock();
            }
            sleep(delayPerRowMs);
        }
    }

    /**
     * Color fade: text transitions through dim → muted → final color.
     */
    public static void colorFade(TextGraphics tg, Screen screen, int x, int y,
                                 String text, TextColor[] fadeSteps, int delayMs,
                                 ReentrantLock lock) {
        for (TextColor color : fadeSteps) {
            lock.lock();
            try {
                tg.setForegroundColor(color);
                tg.putString(x, y, text);
                screen.refresh();
            } catch (IOException ignored) {
            } finally {
                lock.unlock();
            }
            sleep(delayMs);
        }
    }

    /**
     * Starts a spinner on a virtual thread. Call Thread.interrupt() on the returned thread to stop.
     *
     * @return the virtual thread running the spinner
     */
    public static Thread spinnerLoop(TextGraphics tg, Screen screen, int x, int y,
                                     String label, LanternaPalette palette,
                                     ReentrantLock lock) {
        char[] frames = SPINNER_FRAMES;
        Thread spinThread = Thread.ofVirtual().name("lanterna-spinner").start(() -> {
            int frame = 0;
            while (!Thread.currentThread().isInterrupted()) {
                char spinChar = frames[frame % frames.length];
                frame++;
                lock.lock();
                try {
                    tg.setForegroundColor(palette.primary());
                    tg.putString(x, y, String.valueOf(spinChar));
                    tg.setForegroundColor(palette.muted());
                    tg.putString(x + 2, y, label);
                    screen.refresh();
                } catch (IOException ignored) {
                } finally {
                    lock.unlock();
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        return spinThread;
    }

    /**
     * Blocks until Enter is pressed. Ctrl+C exits the JVM.
     */
    static void waitForEnter(Screen screen) {
        while (true) {
            try {
                KeyStroke key = screen.readInput();
                if (key.getKeyType() == KeyType.Enter) return;
                if (key.getKeyType() == KeyType.EOF) return;
                if (key.getKeyType() == KeyType.Character && key.getCharacter() == '\u0003') {
                    System.exit(130);
                }
            } catch (IOException e) {
                return;
            }
        }
    }

    /**
     * Sleep that polls for Ctrl+C every 50ms.
     */
    static void sleepWithPoll(Screen screen, int ms) {
        int elapsed = 0;
        int tick = 50;
        while (elapsed < ms) {
            try {
                KeyStroke key = screen.pollInput();
                if (key != null && key.getKeyType() == KeyType.Character && key.getCharacter() == '\u0003') {
                    System.exit(130);
                }
            } catch (IOException ignored) {}
            int sleepTime = Math.min(tick, ms - elapsed);
            sleep(sleepTime);
            elapsed += sleepTime;
        }
    }

    private static char randomGlyph() {
        return GLYPH_POOL.charAt(RNG.nextInt(GLYPH_POOL.length()));
    }

    static void sleep(int ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
