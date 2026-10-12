package com.dogepopcat.cpgui.ui;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

/**
 * The colour palette, which can be dark, light, or follow Windows.
 *
 * <p>The fields are mutable rather than final so a theme change is a single
 * reassignment that every existing {@code Theme.NAME} reference keeps working
 * with. Components that stored a colour at construction time re-apply theirs
 * through {@link #addListener}.</p>
 */
public final class Theme {

    /** Which palette to use. */
    public enum Mode {
        DARK("Dark"),
        LIGHT("Light"),
        SYSTEM("Follow system");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        /** @return the label shown in the menu, in the current language. */
        public String label() {
            return switch (this) {
                case DARK -> com.dogepopcat.cpgui.i18n.Msg.THEME_DARK.text();
                case LIGHT -> com.dogepopcat.cpgui.i18n.Msg.THEME_LIGHT.text();
                case SYSTEM -> com.dogepopcat.cpgui.i18n.Msg.THEME_SYSTEM.text();
            };
        }

        @Override
        public String toString() {
            return label();
        }
    }

    // --- window chrome -----------------------------------------------------
    public static Color WINDOW_BACKGROUND;
    public static Color WINDOW_FOREGROUND;
    public static Color WINDOW_MUTED;

    // --- editor ------------------------------------------------------------
    public static Color EDITOR_BACKGROUND;
    public static Color EDITOR_FOREGROUND;
    public static Color EDITOR_CARET;
    public static Color EDITOR_SELECTION;
    public static Color CURRENT_LINE;
    /** Greyed inline suggestion drawn after the caret. */
    public static Color GHOST_TEXT;

    // --- gutter ------------------------------------------------------------
    public static Color GUTTER_BACKGROUND;
    public static Color GUTTER_FOREGROUND;
    public static Color GUTTER_CURRENT;

    // --- console -----------------------------------------------------------
    public static Color CONSOLE_BACKGROUND;
    public static Color CONSOLE_FOREGROUND;
    public static Color CONSOLE_COMMAND;
    public static Color CONSOLE_STDERR;
    public static Color CONSOLE_INFO;
    public static Color CONSOLE_SYSTEM;

    // --- toolbar -----------------------------------------------------------
    public static Color BUTTON_BACKGROUND;
    public static Color BUTTON_BORDER;
    public static Color BUTTON_HOVER;
    /** Used by the administrator button while running elevated. */
    public static Color BUTTON_ELEVATED;
    public static Color BUTTON_ELEVATED_BORDER;
    /** Used by the administrator button while running as a normal user. */
    public static Color BUTTON_ADMIN_AVAILABLE;
    public static Color BUTTON_ADMIN_BORDER;

    // --- syntax highlighting ----------------------------------------------
    public static Color SYNTAX_COMMAND;
    public static Color SYNTAX_KEYWORD;
    public static Color SYNTAX_PARAMETER;
    public static Color SYNTAX_SWITCH;
    public static Color SYNTAX_VARIABLE;
    public static Color SYNTAX_STRING;
    public static Color SYNTAX_NUMBER;
    public static Color SYNTAX_OPERATOR;
    public static Color SYNTAX_COMMENT;
    public static Color SYNTAX_LABEL;

    // --- diagnostics -------------------------------------------------------
    public static Color WARNING;
    public static Color ERROR;
    public static Color OK;

    private static final List<Runnable> LISTENERS = new ArrayList<>();

    private static Mode mode = Mode.DARK;

    static {
        apply(Mode.DARK);
    }

    private Theme() {
    }

    /** @return the palette currently in use. */
    public static Mode mode() {
        return mode;
    }

    /**
     * Switches palette and tells every interested component to restyle itself.
     *
     * @param newMode the palette to use
     */
    public static void apply(Mode newMode) {
        mode = newMode == null ? Mode.DARK : newMode;
        boolean dark = isDark();
        if (dark) {
            darkPalette();
        } else {
            lightPalette();
        }
        installUIDefaults(dark);
        for (Runnable listener : List.copyOf(LISTENERS)) {
            listener.run();
        }
    }

    /**
     * @return {@code true} when the palette in use is dark, resolving
     *         {@link Mode#SYSTEM} against the Windows app theme
     */
    public static boolean isDark() {
        if (mode == Mode.SYSTEM) {
            return !systemPrefersLight();
        }
        return mode == Mode.DARK;
    }

    /**
     * Reads the Windows "apps use light theme" setting.
     *
     * <p>The value lives in the registry, which the application does not write
     * to; it is read through {@code reg query} and a missing value is treated as
     * dark, which is the application default.</p>
     */
    private static boolean systemPrefersLight() {
        Process process = null;
        try {
            process = new ProcessBuilder("reg.exe", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                    "/v", "AppsUseLightTheme")
                    .redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.ISO_8859_1);
            process.waitFor();
            return output.contains("0x1");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }
    }

    /** Registers a listener that restyles a component when the palette changes. */
    public static void addListener(Runnable listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    private static void darkPalette() {
        WINDOW_BACKGROUND = new Color(0x2B, 0x2D, 0x30);
        WINDOW_FOREGROUND = new Color(0xD6, 0xD6, 0xD6);
        WINDOW_MUTED = new Color(0x9A, 0xA0, 0xA6);

        EDITOR_BACKGROUND = new Color(0x1E, 0x1F, 0x22);
        EDITOR_FOREGROUND = new Color(0xD6, 0xD6, 0xD6);
        EDITOR_CARET = new Color(0xF0, 0xF0, 0xF0);
        EDITOR_SELECTION = new Color(0x2F, 0x54, 0x7A);
        CURRENT_LINE = new Color(0x28, 0x2A, 0x2E);
        GHOST_TEXT = new Color(0x60, 0x66, 0x70);

        GUTTER_BACKGROUND = new Color(0x24, 0x25, 0x29);
        GUTTER_FOREGROUND = new Color(0x6E, 0x73, 0x7A);
        GUTTER_CURRENT = new Color(0xC8, 0xCC, 0xD2);

        CONSOLE_BACKGROUND = new Color(0x14, 0x15, 0x18);
        CONSOLE_FOREGROUND = new Color(0xCF, 0xD2, 0xD6);
        CONSOLE_COMMAND = new Color(0x4E, 0xC9, 0xB0);
        CONSOLE_STDERR = new Color(0xF4, 0x74, 0x6B);
        CONSOLE_INFO = new Color(0x7A, 0x86, 0x96);
        CONSOLE_SYSTEM = new Color(0xE5, 0xC0, 0x7B);

        BUTTON_BACKGROUND = new Color(0x3C, 0x3F, 0x41);
        BUTTON_BORDER = new Color(0x51, 0x55, 0x5A);
        BUTTON_HOVER = new Color(0x4A, 0x4E, 0x52);
        BUTTON_ELEVATED = new Color(0x5A, 0x45, 0x1E);
        BUTTON_ELEVATED_BORDER = new Color(0xC8, 0x8A, 0x18);
        BUTTON_ADMIN_AVAILABLE = new Color(0x2C, 0x45, 0x2C);
        BUTTON_ADMIN_BORDER = new Color(0x4C, 0x9A, 0x4C);

        syntaxPaletteDark();
        diagnosticsPalette();
    }

    private static void lightPalette() {
        WINDOW_BACKGROUND = new Color(0xF4, 0xF5, 0xF7);
        WINDOW_FOREGROUND = new Color(0x1F, 0x23, 0x29);
        WINDOW_MUTED = new Color(0x5A, 0x60, 0x68);

        EDITOR_BACKGROUND = new Color(0xFF, 0xFF, 0xFF);
        EDITOR_FOREGROUND = new Color(0x1F, 0x23, 0x29);
        EDITOR_CARET = new Color(0x10, 0x14, 0x18);
        EDITOR_SELECTION = new Color(0xB6, 0xD6, 0xF5);
        CURRENT_LINE = new Color(0xF2, 0xF4, 0xF7);
        GHOST_TEXT = new Color(0xA8, 0xAE, 0xB6);

        GUTTER_BACKGROUND = new Color(0xEC, 0xEE, 0xF1);
        GUTTER_FOREGROUND = new Color(0x8A, 0x90, 0x98);
        GUTTER_CURRENT = new Color(0x24, 0x2A, 0x32);

        CONSOLE_BACKGROUND = new Color(0x1E, 0x1F, 0x22);
        CONSOLE_FOREGROUND = new Color(0xD6, 0xD6, 0xD6);
        CONSOLE_COMMAND = new Color(0x4E, 0xC9, 0xB0);
        CONSOLE_STDERR = new Color(0xF4, 0x74, 0x6B);
        CONSOLE_INFO = new Color(0x9A, 0xA4, 0xB2);
        CONSOLE_SYSTEM = new Color(0xE5, 0xC0, 0x7B);

        BUTTON_BACKGROUND = new Color(0xF4, 0xF5, 0xF7);
        BUTTON_BORDER = new Color(0xB8, 0xBD, 0xC6);
        BUTTON_HOVER = new Color(0xE4, 0xEC, 0xF6);
        BUTTON_ELEVATED = new Color(0xFF, 0xE2, 0xB8);
        BUTTON_ELEVATED_BORDER = new Color(0xE0, 0x93, 0x1F);
        BUTTON_ADMIN_AVAILABLE = new Color(0xE3, 0xF2, 0xE3);
        BUTTON_ADMIN_BORDER = new Color(0x4C, 0x9A, 0x4C);

        syntaxPaletteLight();
        diagnosticsPalette();
    }

    /**
     * The dark syntax palette.
     *
     * <p>The colours are spread across the wheel deliberately: commands are blue,
     * keywords magenta, variables purple, strings orange, switches cyan and
     * numbers green, so the eye can tell the parts of a command apart at a
     * glance.</p>
     */
    private static void syntaxPaletteDark() {
        SYNTAX_COMMAND = new Color(0x56, 0x9C, 0xD6);
        SYNTAX_KEYWORD = new Color(0xC5, 0x86, 0xC0);
        SYNTAX_PARAMETER = new Color(0x4E, 0xC9, 0xB0);
        SYNTAX_SWITCH = new Color(0x2A, 0xB8, 0xD8);
        SYNTAX_VARIABLE = new Color(0xC8, 0xA6, 0xFF);
        SYNTAX_STRING = new Color(0xCE, 0x91, 0x78);
        SYNTAX_NUMBER = new Color(0xB5, 0xCE, 0xA8);
        SYNTAX_OPERATOR = new Color(0xE0, 0xA8, 0x5C);
        SYNTAX_COMMENT = new Color(0x6A, 0x99, 0x55);
        SYNTAX_LABEL = new Color(0xDC, 0xDC, 0xAA);
    }

    /** The light syntax palette, darkened so it stays legible on white. */
    private static void syntaxPaletteLight() {
        SYNTAX_COMMAND = new Color(0x1F, 0x4E, 0x9C);
        SYNTAX_KEYWORD = new Color(0x9B, 0x2C, 0x9B);
        SYNTAX_PARAMETER = new Color(0x0F, 0x7A, 0x6B);
        SYNTAX_SWITCH = new Color(0x0B, 0x6E, 0x8A);
        SYNTAX_VARIABLE = new Color(0x6A, 0x3D, 0x9A);
        SYNTAX_STRING = new Color(0xA3, 0x1F, 0x1F);
        SYNTAX_NUMBER = new Color(0x1B, 0x6B, 0x2F);
        SYNTAX_OPERATOR = new Color(0xA0, 0x5A, 0x00);
        SYNTAX_COMMENT = new Color(0x3F, 0x7A, 0x35);
        SYNTAX_LABEL = new Color(0x7A, 0x63, 0x00);
    }

    private static void diagnosticsPalette() {
        WARNING = isDark() ? new Color(0xE5, 0xC0, 0x7B) : new Color(0xA5, 0x76, 0x00);
        ERROR = isDark() ? new Color(0xF4, 0x74, 0x6B) : new Color(0xC0, 0x28, 0x28);
        OK = isDark() ? new Color(0x8C, 0xCF, 0x7F) : new Color(0x2E, 0x7D, 0x32);
    }

    /**
     * Points the look and feel at the palette, which is what makes the menu bar,
     * tab strip, scroll bars and dialogs follow the choice.
     */
    private static void installUIDefaults(boolean dark) {
        Color control = dark ? new Color(0x3C, 0x3F, 0x41) : new Color(0xF0, 0xF0, 0xF0);
        Color background = dark ? WINDOW_BACKGROUND : new Color(0xF4, 0xF5, 0xF7);
        Color text = dark ? WINDOW_FOREGROUND : new Color(0x1F, 0x23, 0x29);
        Color field = dark ? new Color(0x2B, 0x2D, 0x30) : Color.WHITE;

        set("control", control);
        set("info", background);
        set("Panel.background", background);
        set("OptionPane.background", background);
        set("OptionPane.messageForeground", text);
        set("Label.foreground", text);
        set("Label.background", background);
        set("MenuBar.background", background);
        set("MenuBar.foreground", text);
        set("Menu.background", background);
        set("Menu.foreground", text);
        set("MenuItem.background", background);
        set("MenuItem.foreground", text);
        set("Menu.selectedBackground", dark ? new Color(0x4A, 0x4E, 0x52) : new Color(0xCC, 0xE0, 0xF5));
        set("MenuItem.selectionBackground", dark ? new Color(0x4A, 0x4E, 0x52) : new Color(0xCC, 0xE0, 0xF5));
        set("MenuItem.selectionForeground", text);
        set("PopupMenu.background", background);
        set("PopupMenu.foreground", text);
        set("TabbedPane.background", background);
        set("TabbedPane.foreground", text);
        set("TabbedPane.selected", dark ? new Color(0x4A, 0x4E, 0x52) : Color.WHITE);
        set("TabbedPane.contentAreaColor", dark ? new Color(0x4A, 0x4E, 0x52) : Color.WHITE);
        set("TabbedPane.tabAreaBackground", background);
        set("TabbedPane.darkShadow", dark ? new Color(0x1E, 0x1F, 0x22) : null);
        set("TabbedPane.light", background);
        set("TabbedPane.highlight", background);
        set("TabbedPane.shadow", dark ? new Color(0x1E, 0x1F, 0x22) : null);
        set("Button.background", control);
        set("Button.foreground", text);
        set("TextField.background", field);
        set("TextField.foreground", text);
        set("TextField.caretForeground", text);
        set("TextArea.background", field);
        set("TextArea.foreground", text);
        set("TextPane.background", field);
        set("TextPane.foreground", text);
        set("ComboBox.background", field);
        set("ComboBox.foreground", text);
        set("ComboBox.selectionBackground", dark ? new Color(0x2F, 0x54, 0x7A) : new Color(0xCC, 0xE0, 0xF5));
        set("ComboBox.selectionForeground", text);
        set("List.background", field);
        set("List.foreground", text);
        set("Table.background", field);
        set("Table.foreground", text);
        set("Table.gridColor", dark ? new Color(0x4A, 0x4E, 0x52) : new Color(0xD0, 0xD4, 0xDA));
        set("TableHeader.background", control);
        set("TableHeader.foreground", text);
        set("Table.selectionBackground", dark ? new Color(0x2F, 0x54, 0x7A) : new Color(0xCC, 0xE0, 0xF5));
        set("Table.selectionForeground", text);
        set("ScrollPane.background", background);
        set("Viewport.background", field);
        set("ScrollBar.background", control);
        set("ScrollBar.thumb", dark ? new Color(0x5A, 0x5E, 0x62) : new Color(0xC0, 0xC4, 0xCA));
        set("ScrollBar.thumbDarkShadow", control);
        set("ScrollBar.thumbHighlight", control);
        set("ScrollBar.thumbShadow", control);
        set("ScrollBar.track", background);
        set("ScrollBar.trackHighlight", background);
        set("SplitPane.background", background);
        set("SplitPane.dividerSize", 6);
        set("ToolTip.background", dark ? new Color(0x3C, 0x3F, 0x41) : new Color(0xFF, 0xFF, 0xE1));
        set("ToolTip.foreground", text);
        set("Separator.foreground", dark ? new Color(0x4A, 0x4E, 0x52) : new Color(0xD0, 0xD4, 0xDA));
        set("Panel.foreground", text);
    }

    private static void set(String key, Object value) {
        if (value != null) {
            UIManager.put(key, value);
        }
    }

    /**
     * @return a border that gives a control a raised, three dimensional edge so
     *         the buttons read as physical keys rather than flat rectangles
     */
    public static javax.swing.border.Border raisedBorder() {
        return javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createRaisedBevelBorder(),
                javax.swing.BorderFactory.createEmptyBorder(3, 8, 3, 8));
    }

    /**
     * @param colour the edge colour
     * @return a flat but visible one pixel edge
     */
    public static javax.swing.border.Border flatBorder(Color colour) {
        return javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createLineBorder(colour),
                javax.swing.BorderFactory.createEmptyBorder(3, 8, 3, 8));
    }

    /**
     * @return a slightly tinted version of {@code base}, used for hover states
     *         and hairlines
     */
    public static Color tint(Color base, int amount) {
        int r = Math.min(255, Math.max(0, base.getRed() + amount));
        int g = Math.min(255, Math.max(0, base.getGreen() + amount));
        int b = Math.min(255, Math.max(0, base.getBlue() + amount));
        return new Color(r, g, b);
    }

    /**
     * @return a font in the family a Windows console would use
     * @param size the point size
     */
    public static Font mono(int size) {
        return FontSupport.preferredFont(size);
    }

    /**
     * @param size  the point size
     * @param style the {@link Font} style bits
     * @return a monospaced font of the given size and style
     */
    public static Font mono(int size, int style) {
        return FontSupport.preferredFont(size).deriveFont(style);
    }
}
