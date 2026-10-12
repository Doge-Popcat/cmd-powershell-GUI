package com.dogepopcat.cpgui.ui;

import java.awt.Color;
import java.awt.Font;

/**
 * Central place for the colours and fonts used by the dark editor surfaces.
 *
 * <p>The window chrome keeps the native platform look and feel while the
 * editing and console surfaces use a dark scheme, which is what developers
 * expect from an IDE.</p>
 */
public final class Theme {

    // --- editor ------------------------------------------------------------
    public static final Color EDITOR_BACKGROUND = new Color(0x1E, 0x1F, 0x22);
    public static final Color EDITOR_FOREGROUND = new Color(0xD6, 0xD6, 0xD6);
    public static final Color EDITOR_CARET = new Color(0xF0, 0xF0, 0xF0);
    public static final Color EDITOR_SELECTION = new Color(0x2F, 0x54, 0x7A);
    public static final Color CURRENT_LINE = new Color(0x28, 0x2A, 0x2E);
    /** Greyed inline suggestion drawn after the caret. */
    public static final Color GHOST_TEXT = new Color(0x60, 0x66, 0x70);

    // --- toolbar -----------------------------------------------------------
    public static final Color BUTTON_BACKGROUND = new Color(0xF4, 0xF5, 0xF7);
    public static final Color BUTTON_BORDER = new Color(0xB8, 0xBD, 0xC6);
    public static final Color BUTTON_HOVER = new Color(0xE4, 0xEC, 0xF6);
    /** Used by the administrator button while running elevated. */
    public static final Color BUTTON_ELEVATED = new Color(0xFF, 0xE2, 0xB8);
    public static final Color BUTTON_ELEVATED_BORDER = new Color(0xE0, 0x93, 0x1F);
    /** Used by the administrator button while running as a normal user. */
    public static final Color BUTTON_ADMIN_AVAILABLE = new Color(0xE3, 0xF2, 0xE3);
    public static final Color BUTTON_ADMIN_BORDER = new Color(0x4C, 0x9A, 0x4C);

    // --- gutter ------------------------------------------------------------
    public static final Color GUTTER_BACKGROUND = new Color(0x24, 0x25, 0x29);
    public static final Color GUTTER_FOREGROUND = new Color(0x6E, 0x73, 0x7A);
    public static final Color GUTTER_CURRENT = new Color(0xC8, 0xCC, 0xD2);

    // --- console -----------------------------------------------------------
    public static final Color CONSOLE_BACKGROUND = new Color(0x14, 0x15, 0x18);
    public static final Color CONSOLE_FOREGROUND = new Color(0xCF, 0xD2, 0xD6);
    public static final Color CONSOLE_COMMAND = new Color(0x4E, 0xC9, 0xB0);
    public static final Color CONSOLE_STDERR = new Color(0xF4, 0x74, 0x6B);
    public static final Color CONSOLE_INFO = new Color(0x7A, 0x86, 0x96);
    public static final Color CONSOLE_SYSTEM = new Color(0xE5, 0xC0, 0x7B);

    // --- syntax highlighting ----------------------------------------------
    public static final Color SYNTAX_COMMAND = new Color(0x5C, 0xAD, 0xE8);
    public static final Color SYNTAX_KEYWORD = new Color(0xC5, 0x86, 0xC0);
    public static final Color SYNTAX_PARAMETER = new Color(0x9C, 0xDC, 0xFE);
    public static final Color SYNTAX_SWITCH = new Color(0x9C, 0xDC, 0xFE);
    public static final Color SYNTAX_VARIABLE = new Color(0xC8, 0xA6, 0xFF);
    public static final Color SYNTAX_STRING = new Color(0xCE, 0x91, 0x78);
    public static final Color SYNTAX_NUMBER = new Color(0xB5, 0xCE, 0xA8);
    public static final Color SYNTAX_OPERATOR = new Color(0xD4, 0xD4, 0xD4);
    public static final Color SYNTAX_COMMENT = new Color(0x6A, 0x99, 0x55);
    public static final Color SYNTAX_LABEL = new Color(0xDC, 0xDC, 0xAA);

    // --- diagnostics -------------------------------------------------------
    public static final Color WARNING = new Color(0xE5, 0xC0, 0x7B);
    public static final Color ERROR = new Color(0xF4, 0x74, 0x6B);
    public static final Color OK = new Color(0x8C, 0xCF, 0x7F);

    private Theme() {
    }

    /** @return a monospaced font of the given size. */
    public static Font mono(int size) {
        return new Font(Font.MONOSPACED, Font.PLAIN, size);
    }

    /** @return a monospaced font of the given size and style. */
    public static Font mono(int size, int style) {
        return new Font(Font.MONOSPACED, style, size);
    }

    /** @return a slightly tinted version of {@code base}, used for hover states. */
    public static Color tint(Color base, int amount) {
        int r = Math.min(255, Math.max(0, base.getRed() + amount));
        int g = Math.min(255, Math.max(0, base.getGreen() + amount));
        int b = Math.min(255, Math.max(0, base.getBlue() + amount));
        return new Color(r, g, b);
    }
}
