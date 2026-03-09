package org.arcos.Setup.UI;

import com.googlecode.lanterna.screen.Screen;

/**
 * Static holder bridging the pre-Spring Lanterna Screen into Spring context.
 * The Screen is created in main() before Spring boots and consumed by
 * BootReporter to render the boot report into the same full-screen session.
 */
public final class ScreenHolder {

    private static volatile Screen screen;

    private ScreenHolder() {}

    public static void set(Screen s) { screen = s; }

    public static Screen get() { return screen; }

    public static void clear() { screen = null; }
}
