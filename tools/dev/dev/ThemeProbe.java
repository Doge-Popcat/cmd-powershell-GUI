package dev;

import com.dogepopcat.cpgui.i18n.Language;
import com.dogepopcat.cpgui.i18n.Messages;
import com.dogepopcat.cpgui.ui.EditorTab;
import com.dogepopcat.cpgui.ui.MainFrame;
import com.dogepopcat.cpgui.ui.Theme;

import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;

/**
 * Developer check for the colour scheme.
 *
 * <p>It asserts the properties that were wrong before: no surface may be left
 * with the light look and feel default, at startup or after a switch, and the
 * chosen font must survive a switch.</p>
 *
 * <pre>
 *   java -cp "build/classes;build/tools" dev.ThemeProbe
 * </pre>
 */
public final class ThemeProbe {

    private static int failures;

    private ThemeProbe() {
    }

    public static void main(String[] args) {
        try {
            Messages.setLanguage(Language.CHINESE);
            run();
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.out.println("RESULT: FAIL (probe threw)");
            System.exit(1);
        }
        System.out.println(failures == 0 ? "RESULT: PASS" : "RESULT: FAIL (" + failures + ")");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void run() throws Exception {
        MainFrame[] holder = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new MainFrame();
            holder[0].setVisible(true);
        });
        MainFrame frame = holder[0];

        System.out.println("dark, as it starts");
        checkDark(frame);

        System.out.println("switching to light");
        SwingUtilities.invokeAndWait(() -> Theme.apply(Theme.Mode.LIGHT));
        Thread.sleep(600);
        checkLight(frame);

        System.out.println("switching back to dark");
        SwingUtilities.invokeAndWait(() -> Theme.apply(Theme.Mode.DARK));
        Thread.sleep(600);
        checkDark(frame);

        System.out.println("follow system");
        SwingUtilities.invokeAndWait(() -> Theme.apply(Theme.Mode.SYSTEM));
        Thread.sleep(600);
        report(Theme.mode() == Theme.Mode.SYSTEM, "the mode is recorded", Theme.mode().name());
        // Whichever way the system prefers, nothing may be left light-on-light.
        checkNoLightDefault(frame);

        System.out.println("fonts survive a switch");
        SwingUtilities.invokeAndWait(() -> Theme.apply(Theme.Mode.LIGHT));
        Thread.sleep(600);
        EditorTab tab = frame.editorTabs().current();
        String family = tab.textPane().getFont().getFamily();
        report(!family.isBlank(), "the editor still has a font", family);
        SwingUtilities.invokeAndWait(() -> Theme.apply(Theme.Mode.DARK));
        Thread.sleep(600);
        report(tab.textPane().getFont().getFamily().equals(family),
                "the font is unchanged by the switch", tab.textPane().getFont().getFamily());
    }

    /** The dark palette must leave no surface at the light default. */
    private static void checkDark(MainFrame frame) {
        report(Theme.isDark(), "the theme reports dark", Theme.mode().name());
        JTree tree = find(frame, JTree.class);
        report(tree != null && isDark(tree.getBackground()),
                "the file tree background is dark", describe(tree == null ? null
                        : tree.getBackground()));
        JTable table = find(frame, JTable.class);
        report(table != null && isDark(table.getTableHeader().getBackground()),
                "the problems header is dark", describe(table == null ? null
                        : table.getTableHeader().getBackground()));
        report(table != null && isDark(table.getBackground()),
                "the problems table is dark", describe(table == null ? null
                        : table.getBackground()));
        report(isDark(UIManager.getColor("Tree.background")),
                "the tree default is dark", describe(UIManager.getColor("Tree.background")));
        report(isDark(UIManager.getColor("TableHeader.background")),
                "the header default is dark", describe(UIManager.getColor("TableHeader.background")));
        report(isDark(UIManager.getColor("ScrollBar.track")),
                "the scroll bar track is dark", describe(UIManager.getColor("ScrollBar.track")));
    }

    private static void checkLight(MainFrame frame) {
        report(!Theme.isDark(), "the theme reports light", Theme.mode().name());
        report(isLight(UIManager.getColor("Panel.background")),
                "the panel default is light", describe(UIManager.getColor("Panel.background")));
        report(isLight(UIManager.getColor("Tree.background")),
                "the tree default is light", describe(UIManager.getColor("Tree.background")));
    }

    /** No component may still carry the light default while the theme is dark. */
    private static void checkNoLightDefault(MainFrame frame) {
        if (!Theme.isDark()) {
            report(true, "follow system resolved to light, nothing to check", "");
            return;
        }
        JTree tree = find(frame, JTree.class);
        report(tree != null && isDark(tree.getBackground()),
                "the tree is dark after following the system",
                describe(tree == null ? null : tree.getBackground()));
    }

    private static boolean isDark(Color colour) {
        return colour != null && brightness(colour) < 110;
    }

    private static boolean isLight(Color colour) {
        return colour != null && brightness(colour) > 170;
    }

    private static int brightness(Color colour) {
        return (int) (0.299 * colour.getRed() + 0.587 * colour.getGreen()
                + 0.114 * colour.getBlue());
    }

    private static String describe(Color colour) {
        if (colour == null) {
            return "null";
        }
        return String.format("#%02X%02X%02X brightness=%d",
                colour.getRed(), colour.getGreen(), colour.getBlue(), brightness(colour));
    }

    private static <T extends Component> T find(Container parent, Class<T> type) {
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof Container inner) {
                T found = find(inner, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void report(boolean ok, String label, String extra) {
        System.out.printf("  %-7s %-46s %s%n", ok ? "ok" : "FAIL", label, extra);
        if (!ok) {
            failures++;
        }
    }
}
