package com.dogepopcat.cpgui.ui;

import javax.swing.BorderFactory;
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
    /** Border colour for a visible edge. Darker in the light palette. */
    public static Color LINE_LIGHT;
    /** Wash behind a line that single line mode ran successfully. */
    public static Color LINE_SUCCESS;
    /** Wash behind a line whose command failed. */
    public static Color LINE_FAILURE;

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
    /** Used by a toggle button while it is switched on. */
    public static Color BUTTON_ACTIVE;
    /** Text colour on a switched on toggle button. */
    public static Color BUTTON_ACTIVE_TEXT;
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
        if (mode == Mode.SYSTEM) {
            // Re-read once per switch so a change in Windows is picked up.
            systemLight = null;
        }
        boolean dark = isDark();
        if (dark) {
            darkPalette();
        } else {
            lightPalette();
        }
        installUIDefaults(dark);
        // Components may only be touched on the event thread.
        Runnable notify = () -> {
            for (Runnable listener : List.copyOf(LISTENERS)) {
                listener.run();
            }
        };
        if (javax.swing.SwingUtilities.isEventDispatchThread()) {
            notify.run();
        } else {
            try {
                javax.swing.SwingUtilities.invokeAndWait(notify);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw new IllegalStateException("Could not apply the theme", e.getCause());
            }
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
    private static Boolean systemLight;

    /**
     * @return whether Windows is set to light app mode, read once
     *
     * <p>The answer is cached because it is consulted while painting, and
     * starting a process there made switching to "follow system" visibly
     * stutter.</p>
     */
    private static boolean systemPrefersLight() {
        if (systemLight == null) {
            systemLight = readSystemPrefersLight();
        }
        return systemLight;
    }

    /** @return the Windows app theme preference, read from the registry. */
    private static boolean readSystemPrefersLight() {
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
        LINE_SUCCESS = new Color(0x1E, 0x3A, 0x24);
        LINE_FAILURE = new Color(0x3E, 0x1E, 0x20);

        GUTTER_BACKGROUND = new Color(0x24, 0x25, 0x29);
        LINE_LIGHT = new Color(0x4A, 0x4E, 0x52);
        GUTTER_FOREGROUND = new Color(0x6E, 0x73, 0x7A);
        GUTTER_CURRENT = new Color(0xC8, 0xCC, 0xD2);

        CONSOLE_BACKGROUND = new Color(0x14, 0x15, 0x18);
        CONSOLE_FOREGROUND = new Color(0xCF, 0xD2, 0xD6);
        CONSOLE_COMMAND = new Color(0x4E, 0xC9, 0xB0);
        CONSOLE_STDERR = new Color(0xF4, 0x74, 0x6B);
        CONSOLE_INFO = new Color(0x7A, 0x86, 0x96);
        CONSOLE_SYSTEM = new Color(0xE5, 0xC0, 0x7B);

        // Near black, so the bevel is what makes a button read as a button.
        BUTTON_BACKGROUND = new Color(0x16, 0x17, 0x19);
        BUTTON_BORDER = new Color(0x5A, 0x5E, 0x64);
        BUTTON_HOVER = new Color(0x2E, 0x31, 0x35);
        BUTTON_ELEVATED = new Color(0x4A, 0x38, 0x14);
        BUTTON_ELEVATED_BORDER = new Color(0xC8, 0x8A, 0x18);
        BUTTON_ACTIVE = new Color(0x1E, 0x5C, 0x3A);
        BUTTON_ACTIVE_TEXT = new Color(0xF0, 0xFF, 0xF4);
        BUTTON_ADMIN_AVAILABLE = new Color(0x1E, 0x3A, 0x1E);
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
        LINE_SUCCESS = new Color(0xDC, 0xF2, 0xDE);
        LINE_FAILURE = new Color(0xFA, 0xE0, 0xE0);

        GUTTER_BACKGROUND = new Color(0xEC, 0xEE, 0xF1);
        LINE_LIGHT = new Color(0x8A, 0x90, 0x98);
        GUTTER_FOREGROUND = new Color(0x8A, 0x90, 0x98);
        GUTTER_CURRENT = new Color(0x24, 0x2A, 0x32);

        CONSOLE_BACKGROUND = new Color(0x1E, 0x1F, 0x22);
        CONSOLE_FOREGROUND = new Color(0xD6, 0xD6, 0xD6);
        CONSOLE_COMMAND = new Color(0x4E, 0xC9, 0xB0);
        CONSOLE_STDERR = new Color(0xF4, 0x74, 0x6B);
        CONSOLE_INFO = new Color(0x9A, 0xA4, 0xB2);
        CONSOLE_SYSTEM = new Color(0xE5, 0xC0, 0x7B);

        // White on a light grey tool bar, so the button reads as raised rather
        // than as a grey patch of the same shade as its background.
        BUTTON_BACKGROUND = new Color(0xFF, 0xFF, 0xFF);
        BUTTON_BORDER = new Color(0x3A, 0x3F, 0x46);
        BUTTON_HOVER = new Color(0xE4, 0xEC, 0xF6);
        BUTTON_ELEVATED = new Color(0xFF, 0xE2, 0xB8);
        BUTTON_ELEVATED_BORDER = new Color(0xE0, 0x93, 0x1F);
        BUTTON_ACTIVE = new Color(0x2F, 0x8B, 0x5C);
        BUTTON_ACTIVE_TEXT = java.awt.Color.WHITE;
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
        Color selection = dark ? new Color(0x2F, 0x54, 0x7A) : new Color(0xCC, 0xE0, 0xF5);
        Color line = dark ? new Color(0x4A, 0x4E, 0x52) : new Color(0xD0, 0xD4, 0xDA);
        Color muted = dark ? WINDOW_MUTED : new Color(0x5A, 0x60, 0x68);

        // The Windows look and feel paints buttons, combo boxes, tabs and text
        // fields from the system theme and ignores the colours set on them, which
        // is what left white buttons in the dark palette. The basic versions
        // honour the colours.
        set("ButtonUI", "javax.swing.plaf.basic.BasicButtonUI");
        set("ToggleButtonUI", "javax.swing.plaf.basic.BasicToggleButtonUI");
        set("ComboBoxUI", "javax.swing.plaf.basic.BasicComboBoxUI");
        set("TabbedPaneUI", "javax.swing.plaf.basic.BasicTabbedPaneUI");
        set("TextFieldUI", "javax.swing.plaf.basic.BasicTextFieldUI");
        set("TextAreaUI", "javax.swing.plaf.basic.BasicTextAreaUI");
        set("TextPaneUI", "javax.swing.plaf.basic.BasicTextPaneUI");
        set("EditorPaneUI", "javax.swing.plaf.basic.BasicEditorPaneUI");
        set("CheckBoxUI", "javax.swing.plaf.basic.BasicCheckBoxUI");
        set("RadioButtonUI", "javax.swing.plaf.basic.BasicRadioButtonUI");
        set("MenuBarUI", "javax.swing.plaf.basic.BasicMenuBarUI");
        set("MenuUI", "javax.swing.plaf.basic.BasicMenuUI");
        set("MenuItemUI", "javax.swing.plaf.basic.BasicMenuItemUI");
        set("PopupMenuUI", "javax.swing.plaf.basic.BasicPopupMenuUI");
        set("OptionPaneUI", "javax.swing.plaf.basic.BasicOptionPaneUI");

        set("ToolBarUI", "javax.swing.plaf.basic.BasicToolBarUI");
        set("ListUI", "javax.swing.plaf.basic.BasicListUI");
        set("TreeUI", "javax.swing.plaf.basic.BasicTreeUI");
        set("TableUI", "javax.swing.plaf.basic.BasicTableUI");
        set("LabelUI", "javax.swing.plaf.basic.BasicLabelUI");
        set("PanelUI", "javax.swing.plaf.basic.BasicPanelUI");
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
        // Buttons sit on the tool bar, so they use the button colour rather than
        // the grey control colour, which looked dingy in the light palette.
        set("Button.background", dark ? control : Color.WHITE);
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
        // The Windows scroll bar paints its own themed arrows and track, which
        // stay light in a dark palette, so the basic one is used and coloured.
        set("ScrollBarUI", "javax.swing.plaf.basic.BasicScrollBarUI");
        set("ScrollBar.width", 12);
        set("ScrollBar.background", dark ? new Color(0x2B, 0x2D, 0x30) : control);
        set("ScrollBar.foreground", text);
        set("ScrollBar.thumb", dark ? new Color(0x55, 0x59, 0x5E) : new Color(0xB4, 0xB9, 0xC0));
        set("ScrollBar.thumbDarkShadow", dark ? new Color(0x2B, 0x2D, 0x30) : control);
        set("ScrollBar.thumbHighlight", dark ? new Color(0x6A, 0x6F, 0x75) : new Color(0xD8, 0xDC, 0xE1));
        set("ScrollBar.thumbShadow", dark ? new Color(0x3A, 0x3D, 0x41) : new Color(0x9A, 0xA0, 0xA8));
        set("ScrollBar.track", dark ? new Color(0x21, 0x23, 0x26) : new Color(0xE8, 0xEA, 0xED));
        set("ScrollBar.trackHighlight", dark ? new Color(0x21, 0x23, 0x26) : new Color(0xE8, 0xEA, 0xED));
        set("ScrollBar.border", BorderFactory.createEmptyBorder());
        set("ScrollBar.gradient", null);
        set("SplitPane.background", background);
        set("SplitPane.dividerSize", 6);
        set("ToolTip.background", dark ? new Color(0x3C, 0x3F, 0x41) : new Color(0xFF, 0xFF, 0xE1));
        set("ToolTip.foreground", text);
        set("Separator.foreground", dark ? new Color(0x4A, 0x4E, 0x52) : new Color(0xD0, 0xD4, 0xDA));
        set("Panel.foreground", text);
        // Trees and tables paint their own background wherever there is no cell,
        // which is the empty area below the last row. Without these keys that
        // area stays white however carefully the cells are drawn.
        set("Tree.background", field);
        set("Tree.textBackground", field);
        set("Tree.foreground", text);
        set("Tree.textForeground", text);
        set("Tree.selectionBackground", selection);
        set("Tree.selectionForeground", dark ? Color.WHITE : text);
        set("Tree.hash", line);
        set("Tree.line", line);
        set("Tree.leftChildIndent", 12);
        set("Tree.rightChildIndent", 8);
        // The Windows look and feel draws a themed white gradient over the
        // header whatever its background is, so the basic header is used instead.
        set("TableHeaderUI", "javax.swing.plaf.basic.BasicTableHeaderUI");
        set("TableHeader.background", dark ? new Color(0x33, 0x36, 0x39) : new Color(0xE8, 0xEA, 0xED));
        set("TableHeader.foreground", text);
        set("TableHeader.cellBorder", BorderFactory.createMatteBorder(0, 0, 1, 1, line));
        set("Table.focusCellForeground", text);
        set("Table.focusCellBackground", field);
        set("EditorPane.background", field);
        set("EditorPane.foreground", text);
        set("EditorPane.caretForeground", text);
        set("FormattedTextField.background", field);
        set("FormattedTextField.foreground", text);
        set("PasswordField.background", field);
        set("PasswordField.foreground", text);
        set("TextPane.caretForeground", text);
        set("TextArea.caretForeground", text);
        set("TabbedPane.selectHighlight", background);
        set("TabbedPane.focus", background);
        set("TabbedPane.borderHightlightColor", background);
        set("TabbedPane.unselectedBackground", dark ? new Color(0x33, 0x36, 0x39) : control);
        set("TabbedPane.contentBorderInsets", new javax.swing.plaf.InsetsUIResource(1, 0, 0, 0));
        set("SplitPane.highlight", line);
        set("SplitPane.shadow", background);
        set("SplitPane.darkShadow", background);
        set("SplitPane.foreground", text);
        set("SplitPaneUI", "javax.swing.plaf.basic.BasicSplitPaneUI");
        set("RadioButton.background", background);
        set("RadioButton.foreground", text);
        set("CheckBox.background", background);
        set("CheckBox.foreground", text);
        set("RadioButtonMenuItem.background", background);
        set("RadioButtonMenuItem.foreground", text);
        set("CheckBoxMenuItem.background", background);
        set("CheckBoxMenuItem.foreground", text);
        set("Menu.acceleratorForeground", muted);
        set("MenuItem.acceleratorForeground", muted);
        set("MenuBar.border", BorderFactory.createEmptyBorder());
        set("ToolBar.background", background);
        set("ToolBar.foreground", text);
        set("ToolBar.border", BorderFactory.createEmptyBorder());
        set("ScrollPane.border", BorderFactory.createEmptyBorder());
        set("List.selectionBackground", selection);
        set("List.selectionForeground", dark ? Color.WHITE : text);
        set("Slider.background", background);
        set("Slider.foreground", text);
        set("ProgressBar.background", field);
        set("ProgressBar.foreground", text);
        set("ProgressBar.selectionBackground", text);
        set("ProgressBar.selectionForeground", field);
    }

    private static void set(String key, Object value) {
        if (value != null) {
            UIManager.put(key, value);
        }
    }

    /**
     * Colours a file chooser to match the palette.
     *
     * <p>The chooser keeps its own list and field colours, so setting the global
     * defaults is not enough: without this the list stays white while the text
     * colour follows the palette, which leaves grey text on white.</p>
     *
     * @param chooser the chooser to style
     */
    public static void styleChooser(javax.swing.JFileChooser chooser) {
        // The chooser builds its own components, so the look and feel is asked to
        // rebuild them against the current defaults before anything is coloured.
        javax.swing.SwingUtilities.updateComponentTreeUI(chooser);
        Color field = isDark() ? EDITOR_BACKGROUND : java.awt.Color.WHITE;
        Color chrome = isDark() ? WINDOW_BACKGROUND : new Color(0xF4, 0xF5, 0xF7);
        chooser.setBackground(chrome);
        chooser.setForeground(WINDOW_FOREGROUND);
        styleDescendants(chooser, field, chrome);
        chooser.repaint();

        // The Windows file pane builds its short cut side bar and other parts the
        // first time the dialog is shown, which is after this call. Restyling once
        // it is on screen is what colours those, and a background with an
        // unstyled black label on it is exactly the unreadable row reported.
        chooser.addHierarchyListener(new java.awt.event.HierarchyListener() {
            private javax.swing.Timer passes;

            @Override
            public void hierarchyChanged(java.awt.event.HierarchyEvent event) {
                if (!chooser.isShowing()) {
                    return;
                }
                if (passes != null) {
                    return;
                }
                // One pass is not enough: the file chooser builds its short cut
                // side bar and its labels while the dialog is being shown, and
                // the look and feel resets their colours as it goes. Several
                // passes over the first second and a half land after all of that,
                // which is what colours the labels that stayed black.
                final int[] remaining = {6};
                passes = new javax.swing.Timer(250, tick -> {
                    Color passField = isDark() ? EDITOR_BACKGROUND : java.awt.Color.WHITE;
                    Color passChrome = isDark() ? WINDOW_BACKGROUND
                            : new Color(0xF4, 0xF5, 0xF7);
                    styleDescendants(chooser, passField, passChrome);
                    chooser.repaint();
                    if (--remaining[0] <= 0) {
                        passes.stop();
                    }
                });
                passes.setInitialDelay(0);
                passes.start();
            }
        });
    }

    /**
     * Applies the palette to a chooser component and everything under it.
     *
     * <p>Every kind of component the chooser uses has to be covered. The details
     * view is a table, and a table that is missed keeps the light default, which
     * is what left the file list white inside an otherwise dark dialog.</p>
     */
    private static void styleDescendants(java.awt.Component component, Color field,
                                         Color chrome) {
        boolean leaf = true;
        if (component instanceof javax.swing.JTable table) {
            table.setBackground(field);
            table.setForeground(EDITOR_FOREGROUND);
            table.setGridColor(isDark() ? tint(chrome, 24) : new Color(0xD0, 0xD4, 0xDA));
            if (table.getTableHeader() != null) {
                table.getTableHeader().setBackground(isDark() ? tint(chrome, 16)
                        : new Color(0xE8, 0xEA, 0xED));
                table.getTableHeader().setForeground(WINDOW_FOREGROUND);
            }
        } else if (component instanceof javax.swing.JList<?> list) {
            list.setBackground(field);
            list.setForeground(EDITOR_FOREGROUND);
        } else if (component instanceof javax.swing.text.JTextComponent text) {
            text.setBackground(field);
            text.setForeground(EDITOR_FOREGROUND);
            text.setCaretColor(EDITOR_FOREGROUND);
        } else if (component instanceof javax.swing.JLabel label) {
            label.setForeground(isDark() ? WINDOW_FOREGROUND : new Color(0x1F, 0x23, 0x29));
        } else if (component instanceof javax.swing.JComboBox<?> combo) {
            combo.setBackground(field);
            combo.setForeground(EDITOR_FOREGROUND);
        } else if (component instanceof javax.swing.JButton button) {
            button.setBackground(BUTTON_BACKGROUND);
            button.setForeground(WINDOW_FOREGROUND);
        } else if (component instanceof javax.swing.JViewport viewport) {
            viewport.setBackground(field);
        } else if (component instanceof javax.swing.JScrollPane scroll) {
            scroll.setBackground(chrome);
            scroll.getViewport().setBackground(field);
        } else {
            leaf = false;
        }
        if (component instanceof java.awt.Container container) {
            // A list, table or field is a container too; its own background was
            // just set, and overwriting it here is what made them all the shade
            // of the surrounding chrome.
            if (!leaf) {
                container.setBackground(chrome);
            }
            for (java.awt.Component child : container.getComponents()) {
                styleDescendants(child, field, chrome);
            }
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
