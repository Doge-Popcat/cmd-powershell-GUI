package com.dogepopcat.cpgui.shell;

import com.dogepopcat.cpgui.ui.Theme;

import java.awt.Color;
import java.util.Locale;
import java.util.Optional;

/**
 * The sixteen colour console palette and the {@code color} command syntax.
 *
 * <p>{@code color} sets attributes on the console screen buffer, and a process
 * whose output is captured through a pipe has no such buffer, so the command
 * always fails inside this application. The palette is applied to the console
 * pane instead, which is the surface the user is actually looking at.</p>
 */
public final class ConsolePalette {

    /** The classic Windows console colours, indexed by their hex digit. */
    private static final Color[] COLOURS = {
            new Color(0x0C, 0x0C, 0x0C), // 0 black
            new Color(0x00, 0x36, 0xA2), // 1 dark blue
            new Color(0x13, 0xA1, 0x0E), // 2 dark green
            new Color(0x00, 0xA8, 0xA8), // 3 dark cyan
            new Color(0xC5, 0x0F, 0x1F), // 4 dark red
            new Color(0x88, 0x17, 0x98), // 5 dark magenta
            new Color(0xC1, 0x9C, 0x00), // 6 dark yellow
            new Color(0xCC, 0xCC, 0xCC), // 7 grey
            new Color(0x76, 0x76, 0x76), // 8 dark grey
            new Color(0x3B, 0x78, 0xFF), // 9 blue
            new Color(0x16, 0xC6, 0x0C), // A green
            new Color(0x00, 0xCC, 0xCC), // B cyan
            new Color(0xE7, 0x48, 0x56), // C red
            new Color(0xB4, 0x00, 0x9E), // D magenta
            new Color(0xF9, 0xF1, 0xA5), // E yellow
            new Color(0xFF, 0xFF, 0xFF)  // F white
    };

    private ConsolePalette() {
    }

    /**
     * Parses a {@code color} argument.
     *
     * @param argument the text after {@code color}, which may be empty
     * @param current  the colours in use, so a single digit keeps the background
     * @return the background and foreground to apply, or empty when the argument
     *         is not a valid colour code
     */
    public static Optional<Color[]> parse(String argument, Color[] current) {
        String text = argument == null ? "" : argument.strip().toUpperCase(Locale.ROOT);
        if (text.isEmpty()) {
            return Optional.of(new Color[]{Theme.CONSOLE_BACKGROUND, Theme.CONSOLE_FOREGROUND});
        }
        if (text.length() > 2) {
            return Optional.empty();
        }
        Color background;
        Color foreground;
        if (text.length() == 1) {
            // A single digit changes only the foreground.
            background = current != null && current.length > 0 && current[0] != null
                    ? current[0] : Theme.CONSOLE_BACKGROUND;
            foreground = colour(text.charAt(0));
        } else {
            background = colour(text.charAt(0));
            foreground = colour(text.charAt(1));
        }
        if (background == null || foreground == null) {
            return Optional.empty();
        }
        if (background.equals(foreground)) {
            // cmd rejects this because nothing would be readable.
            return Optional.empty();
        }
        return Optional.of(new Color[]{background, foreground});
    }

    private static Color colour(char digit) {
        int index = Character.digit(digit, 16);
        return index < 0 ? null : COLOURS[index];
    }

    /** @return the colour for a palette digit, or {@code null}. */
    public static Color colourAt(int index) {
        return index >= 0 && index < COLOURS.length ? COLOURS[index] : null;
    }

    /**
     * @param foreground the foreground in use
     * @param background the background in use
     * @return a colour for echoed commands that stays readable on background
     */
    public static Color readableAccent(Color foreground, Color background) {
        // Pick whichever of the two bright accents contrasts better.
        Color green = COLOURS[0x0A];
        Color cyan = COLOURS[0x0B];
        return luminance(background) > 140 ? COLOURS[0x02] : green.equals(foreground) ? cyan : green;
    }

    private static int luminance(Color colour) {
        return (int) (0.299 * colour.getRed() + 0.587 * colour.getGreen()
                + 0.114 * colour.getBlue());
    }
}
