package dev;

import com.dogepopcat.cpgui.Main;
import com.dogepopcat.cpgui.ui.EditorTab;
import com.dogepopcat.cpgui.ui.LineStatusPainter;
import com.dogepopcat.cpgui.ui.MainFrame;
import com.dogepopcat.cpgui.ui.SquigglePainter;
import com.dogepopcat.cpgui.ui.Theme;

import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.text.AttributeSet;
import javax.swing.text.Element;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Checks that every painted surface actually follows the palette.
 *
 * <p>A style is "not applied" in two ways the eye catches but a build does not:
 * a colour that was only ever read once (at construction) and never read again,
 * and a colour that changes in the palette but is left behind on a surface the
 * window never walks. Both show up here as a component, a text run or a
 * highlight painter still holding a colour that only exists in the other
 * palette, which is the one thing colour identity can prove.</p>
 *
 * <p>The window is filled with every kind of content first (console lines in all
 * console colours, a script with a diagnostic, a single-line mark), then the
 * palette is switched and each surface is looked at from the other side.</p>
 */
public final class StyleAuditProbe {

    private static final List<String> problems = new ArrayList<>();
    private static final List<String> unverified = new ArrayList<>();
    private static final Map<String, Integer> untouched = new TreeMap<>();
    private static final Map<String, String> untouchedColours = new LinkedHashMap<>();
    private static final Map<String, String> examples = new LinkedHashMap<>();
    private static final Map<String, Integer> stale = new LinkedHashMap<>();
    private static Map<Component, Color[]> seen = new IdentityHashMap<>();
    private static Map<Component, Color[]> previous = new IdentityHashMap<>();
    private static int components;
    private static int documents;
    private static int squiggles;
    private static int marks;

    private StyleAuditProbe() {
    }

    public static void main(String[] args) throws Exception {
        com.dogepopcat.cpgui.i18n.Messages.setLanguage(
                com.dogepopcat.cpgui.i18n.Language.CHINESE);
        Main.applyLookAndFeel();
        Theme.apply(Theme.Mode.DARK);

        File scratch = new File("D:\\cpgui-style");
        scratch.mkdirs();
        System.out.println("opening the window");
        MainFrame[] holder = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new MainFrame(scratch));
        MainFrame frame = holder[0];
        System.out.println("filling it with content");
        fill(frame);
        Thread.sleep(900);

        Map<String, Color> dark = palette();
        SwingUtilities.invokeAndWait(() -> Theme.apply(Theme.Mode.LIGHT));
        Thread.sleep(900);
        Map<String, Color> light = palette();
        System.out.println("auditing the light theme");
        audit(frame, "light theme", coloursOnly(dark, light));
        screenshot(frame, "style-light");

        SwingUtilities.invokeAndWait(() -> Theme.apply(Theme.Mode.DARK));
        Thread.sleep(900);
        // Saving a file refreshes the tab titles; they must not go back to the
        // colour they were created with after that.
        SwingUtilities.invokeAndWait(frame.editorTabs()::refreshTitles);
        Thread.sleep(300);
        System.out.println("auditing the dark theme");
        audit(frame, "dark theme", coloursOnly(light, dark));
        screenshot(frame, "style-dark");

        if (squiggles == 0) {
            unverified.add("no diagnostic underline was on screen to audit");
        }
        if (marks == 0) {
            unverified.add("no line result mark was on screen to audit");
        }
        System.out.printf("%n%d components, %d documents walked, %d underlines, %d marks%n",
                components, documents, squiggles, marks);
        report();
        SwingUtilities.invokeAndWait(() -> {
            for (Window window : Window.getWindows()) {
                window.dispose();
            }
        });
        boolean clean = problems.isEmpty() && unverified.isEmpty();
        System.exit(clean ? 0 : 1);    }

    /** Puts every kind of painted content into the window. */
    private static void fill(MainFrame frame) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            EditorTab tab = frame.editorTabs().current();
            tab.setText("echo style-audit\r\nformat c:\r\necho done");
            tab.textPane().setCaretPosition(0);
            tab.diagnostics().refresh();
            Color[] consoleColours = {
                    Theme.CONSOLE_FOREGROUND, Theme.CONSOLE_COMMAND, Theme.CONSOLE_STDERR,
                    Theme.CONSOLE_INFO, Theme.CONSOLE_SYSTEM, Theme.OK, Theme.ERROR,
                    Theme.WARNING};
            for (Color colour : consoleColours) {
                frame.console().appendLine("style audit " + name(colour), colour);
            }
            frame.setSize(1400, 900);
            frame.setVisible(true);
        });
        Thread.sleep(500);
        // A single line run leaves a LineStatusPainter on the text pane. It is
        // posted rather than awaited: a command that asks for confirmation runs a
        // modal dialog inside the call, and waiting for that would hang the probe
        // on a click that never comes.
        SwingUtilities.invokeAndWait(() -> frame.setSingleLineMode(true));
        Thread.sleep(300);
        System.out.println("running one line to leave a result mark");
        SwingUtilities.invokeLater(frame::runActiveScript);
        Thread.sleep(2600);
        EditorTab tab = frame.editorTabs().current();
        System.out.printf("findings=%d highlights=%d line0=%s caretLine=%d single=%s%n",
                tab.diagnostics().findings().size(),
                tab.textPane().getHighlighter().getHighlights().length,
                tab.lineResult(0), tab.caretLine(), frame.isSingleLineMode());
        System.out.println("content ready");
        // The console is the surface that kept the other palette's text colours,
        // so it belongs in the picture as well.
        SwingUtilities.invokeAndWait(
                () -> frame.selectBottomTab(MainFrame.BottomTab.OUTPUT));
        Thread.sleep(400);
    }

    /** Writes what the window looks like now, for a look that no check can make. */
    private static void screenshot(MainFrame frame, String name) throws Exception {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                frame.getWidth(), frame.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(() -> {
            java.awt.Graphics2D graphics = image.createGraphics();
            frame.printAll(graphics);
            graphics.dispose();
        });
        File file = new File("build\\" + name + ".png");
        javax.imageio.ImageIO.write(image, "png", file);
        System.out.println("wrote " + file.getPath());
    }

    private static void audit(MainFrame frame, String mode, Map<Color, String> foreign) {
        EditorTab active = frame.editorTabs().current();
        System.out.printf("  (%s starts with %d highlights on screen)%n", mode,
                active.textPane().getHighlighter().getHighlights().length);
        List<Component> roots = new ArrayList<>();
        roots.add(frame);
        for (Window window : Window.getWindows()) {
            if (!containsIdentity(roots, window)) {
                roots.add(window);
            }
        }
        JMenuBar bar = frame.getJMenuBar();
        if (bar != null) {
            for (int i = 0; i < bar.getMenuCount(); i++) {
                addPopup(roots, bar.getMenu(i));
            }
        }
        for (Component root : roots) {
            walk(root, shortName(root), mode, foreign);
        }
        previous = seen;
        seen = new IdentityHashMap<>();
    }

    private static boolean containsIdentity(List<Component> list, Component candidate) {
        for (Component component : list) {
            if (component == candidate) {
                return true;
            }
        }
        return false;
    }

    private static void addPopup(List<Component> roots, JMenu menu) {
        if (menu == null) {
            return;
        }
        JPopupMenu popup = menu.getPopupMenu();
        if (popup == null || roots.contains(popup)) {
            return;
        }
        roots.add(popup);
        for (Component item : popup.getComponents()) {
            if (item instanceof JMenu sub) {
                addPopup(roots, sub);
            }
        }
    }

    private static void walk(Component component, String path, String mode,
                             Map<Color, String> foreign) {
        if (component instanceof JComponent piece) {
            components++;
            Color background = piece.getBackground();
            Color foreground = piece.getForeground();
            seen.put(piece, new Color[]{background, foreground});
            Color[] before = previous.get(piece);
            if (before != null && before[0] != null && before[1] != null
                    && before[0].equals(background) && before[1].equals(foreground)) {
                untouched.merge(shortName(piece), 1, Integer::sum);
                untouchedColours.putIfAbsent(shortName(piece),
                        hex(background) + " on " + hex(foreground) + "   " + path);
            }
            flag(background, "background", piece, path, mode, foreign);
            flag(foreground, "foreground", piece, path, mode, foreign);
        }
        if (component instanceof JTextComponent text) {
            auditDocument(text, path, mode, foreign);
            auditHighlights(text, path, mode, foreign);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                walk(child, path + " > " + shortName(child), mode, foreign);
            }
        }
    }

    /** Every text run carries its own colour, which no component walk reaches. */
    private static void auditDocument(JTextComponent text, String path, String mode,
                                      Map<Color, String> foreign) {
        if (!(text.getDocument() instanceof StyledDocument document)) {
            return;
        }
        documents++;
        collect(document.getDefaultRootElement(), mode, foreign);
    }

    private static void collect(Element element, String mode, Map<Color, String> foreign) {
        if (element.getElementCount() == 0) {
            AttributeSet attributes = element.getAttributes();
            flag(StyleConstants.getForeground(attributes), "text run", element,
                    "document", mode, foreign);
            return;
        }
        for (int i = 0; i < element.getElementCount(); i++) {
            collect(element.getElement(i), mode, foreign);
        }
    }

    private static void auditHighlights(JTextComponent text, String path, String mode,
                                        Map<Color, String> foreign) {
        Highlighter highlighter = text.getHighlighter();
        if (highlighter == null) {
            return;
        }
        for (Highlighter.Highlight highlight : highlighter.getHighlights()) {
            Highlighter.HighlightPainter painter = highlight.getPainter();
            Color colour = null;
            String kind = null;
            if (painter instanceof SquigglePainter squiggle) {
                squiggles++;
                colour = squiggle.colour();
                kind = "diagnostic squiggle";
            } else if (painter instanceof LineStatusPainter status) {
                marks++;
                colour = status.colour();
                kind = "line result mark";
            }
            flag(colour, kind, painter, "highlighter", mode, foreign);
        }
    }

    private static void flag(Color colour, String role, Object owner, String path,
                             String mode, Map<Color, String> foreign) {
        if (colour == null) {
            return;
        }
        String otherName = foreign.get(colour);
        if (otherName == null) {
            return;
        }
        String who = owner instanceof Component component ? shortName(component)
                : owner == null ? "?" : owner.getClass().getSimpleName();
        String key = mode + ": " + who + " " + role + " is still " + otherName;
        stale.merge(key, 1, Integer::sum);
        examples.putIfAbsent(key, path);
        problems.add(key);
    }

    private static void report() {
        if (!stale.isEmpty()) {
            System.out.println("""

                    -- colours left behind by the other palette --
                    """);
            stale.forEach((key, count) -> System.out.printf(
                    "  stale  %-72s x%-3d %s%n", key, count, examples.get(key)));
        }
        if (!unverified.isEmpty()) {
            System.out.println("""

                    -- surfaces this run could not look at --
                    """);
            unverified.forEach(gap -> System.out.println("  missed " + gap));
        }
        System.out.println("""

                -- components whose colours never changed (informational) --
                """);
        untouched.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(30)
                .forEach(entry -> System.out.printf("  %-40s x%-3d %s%n",
                        entry.getKey(), entry.getValue(),
                        untouchedColours.get(entry.getKey())));
        boolean clean = stale.isEmpty() && unverified.isEmpty();
        System.out.println(clean
                ? "\nRESULT: PASS (every surface followed the palette both ways)"
                : "\nRESULT: FAIL (" + stale.size() + " stale style groups, "
                        + unverified.size() + " surfaces missed)");
    }

    private static Map<String, Color> palette() {
        Map<String, Color> map = new LinkedHashMap<>();
        map.put("WINDOW_BACKGROUND", Theme.WINDOW_BACKGROUND);
        map.put("WINDOW_FOREGROUND", Theme.WINDOW_FOREGROUND);
        map.put("WINDOW_MUTED", Theme.WINDOW_MUTED);
        map.put("EDITOR_BACKGROUND", Theme.EDITOR_BACKGROUND);
        map.put("EDITOR_FOREGROUND", Theme.EDITOR_FOREGROUND);
        map.put("EDITOR_CARET", Theme.EDITOR_CARET);
        map.put("EDITOR_SELECTION", Theme.EDITOR_SELECTION);
        map.put("CURRENT_LINE", Theme.CURRENT_LINE);
        map.put("GHOST_TEXT", Theme.GHOST_TEXT);
        map.put("LINE_LIGHT", Theme.LINE_LIGHT);
        map.put("LINE_SUCCESS", Theme.LINE_SUCCESS);
        map.put("LINE_FAILURE", Theme.LINE_FAILURE);
        map.put("GUTTER_BACKGROUND", Theme.GUTTER_BACKGROUND);
        map.put("GUTTER_FOREGROUND", Theme.GUTTER_FOREGROUND);
        map.put("GUTTER_CURRENT", Theme.GUTTER_CURRENT);
        map.put("CONSOLE_BACKGROUND", Theme.CONSOLE_BACKGROUND);
        map.put("CONSOLE_FOREGROUND", Theme.CONSOLE_FOREGROUND);
        map.put("CONSOLE_COMMAND", Theme.CONSOLE_COMMAND);
        map.put("CONSOLE_STDERR", Theme.CONSOLE_STDERR);
        map.put("CONSOLE_INFO", Theme.CONSOLE_INFO);
        map.put("CONSOLE_SYSTEM", Theme.CONSOLE_SYSTEM);
        map.put("BUTTON_BACKGROUND", Theme.BUTTON_BACKGROUND);
        map.put("BUTTON_BORDER", Theme.BUTTON_BORDER);
        map.put("BUTTON_HOVER", Theme.BUTTON_HOVER);
        map.put("BUTTON_ELEVATED", Theme.BUTTON_ELEVATED);
        map.put("BUTTON_ELEVATED_BORDER", Theme.BUTTON_ELEVATED_BORDER);
        map.put("BUTTON_ACTIVE", Theme.BUTTON_ACTIVE);
        map.put("BUTTON_ACTIVE_TEXT", Theme.BUTTON_ACTIVE_TEXT);
        map.put("BUTTON_ADMIN_AVAILABLE", Theme.BUTTON_ADMIN_AVAILABLE);
        map.put("BUTTON_ADMIN_BORDER", Theme.BUTTON_ADMIN_BORDER);
        map.put("SYNTAX_COMMAND", Theme.SYNTAX_COMMAND);
        map.put("SYNTAX_KEYWORD", Theme.SYNTAX_KEYWORD);
        map.put("SYNTAX_PARAMETER", Theme.SYNTAX_PARAMETER);
        map.put("SYNTAX_SWITCH", Theme.SYNTAX_SWITCH);
        map.put("SYNTAX_VARIABLE", Theme.SYNTAX_VARIABLE);
        map.put("SYNTAX_STRING", Theme.SYNTAX_STRING);
        map.put("SYNTAX_NUMBER", Theme.SYNTAX_NUMBER);
        map.put("SYNTAX_OPERATOR", Theme.SYNTAX_OPERATOR);
        map.put("SYNTAX_COMMENT", Theme.SYNTAX_COMMENT);
        map.put("SYNTAX_LABEL", Theme.SYNTAX_LABEL);
        map.put("WARNING", Theme.WARNING);
        map.put("ERROR", Theme.ERROR);
        map.put("OK", Theme.OK);
        return map;
    }

    /**
     * @return every colour that exists in one palette only, named by its role,
     *         which is what makes "still the other theme's colour" provable
     *
     * <p>Colours the current look and feel installs are excluded: the same shade
     * is often both a palette field in one theme and a control shade in the other,
     * and that is not a stale style.</p>
     */
    private static Map<Color, String> coloursOnly(Map<String, Color> other,
                                                  Map<String, Color> current) {
        Set<Color> shared = new HashSet<>(current.values());
        java.util.Enumeration<Object> keys = javax.swing.UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object value = javax.swing.UIManager.get(keys.nextElement());
            if (value instanceof Color colour) {
                shared.add(colour);
            }
        }
        Map<Color, String> unique = new LinkedHashMap<>();
        other.forEach((name, colour) -> {
            if (colour != null && !shared.contains(colour)) {
                unique.putIfAbsent(colour, name);
            }
        });
        return unique;
    }

    private static String name(Color colour) {
        for (Map.Entry<String, Color> entry : palette().entrySet()) {
            if (colour != null && colour.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return hex(colour);
    }

    private static String hex(Color colour) {
        return String.format("#%06X", colour == null ? 0 : colour.getRGB() & 0xFFFFFF);
    }

    private static String shortName(Object component) {
        String name = component.getClass().getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            name = name.substring(dot + 1);
        }
        if (name.startsWith("MainFrame$")) {
            return "MainFrame" + name.substring("MainFrame".length());
        }
        return name;
    }
}
