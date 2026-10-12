package dev;

import com.dogepopcat.cpgui.ui.EditorTab;
import com.dogepopcat.cpgui.ui.FontChooserDialog;
import com.dogepopcat.cpgui.ui.MainFrame;

import javax.swing.SwingUtilities;
import java.awt.Font;
import java.awt.GraphicsEnvironment;

/**
 * Developer check for the font selection.
 *
 * <p>The dialog itself is modal, so this verifies the part that matters
 * programmatically: applying a font reaches every editor tab, the console and
 * any tab opened afterwards.</p>
 *
 * <pre>
 *   java -cp "build/classes;build/tools" dev.FontProbe
 * </pre>
 */
public final class FontProbe {

    private static int failures;

    private FontProbe() {
    }

    public static void main(String[] args) {
        try {
            run();
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.out.println("RESULT: FAIL (probe threw)");
            System.exit(1);
        }
    }

    private static void run() throws Exception {
        MainFrame[] holder = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new MainFrame();
            holder[0].setVisible(true);
        });
        MainFrame frame = holder[0];

        String family = pickMonospacedFamily();
        Font editorChoice = new Font(family, Font.PLAIN, 17);
        Font consoleChoice = new Font(family, Font.PLAIN, 15);

        SwingUtilities.invokeAndWait(() ->
                frame.applyFont(FontChooserDialog.Target.EDITOR, editorChoice));
        SwingUtilities.invokeAndWait(() ->
                frame.applyFont(FontChooserDialog.Target.CONSOLE, consoleChoice));
        Thread.sleep(400);

        EditorTab first = frame.editorTabs().current();
        report(first.editorFont().getSize() == 17 && first.editorFont().getFamily()
                        .equalsIgnoreCase(editorChoice.getFamily()),
                "editor font applied", describe(first.editorFont()));
        report(frame.console().consoleFont().getSize() == 15,
                "console font applied", describe(frame.console().consoleFont()));

        // A tab opened later must inherit the chosen editor font.
        EditorTab[] opened = new EditorTab[1];
        SwingUtilities.invokeAndWait(() -> opened[0] = frame.newTab());
        Thread.sleep(300);
        report(opened[0].editorFont().getSize() == 17,
                "new tabs inherit the font", describe(opened[0].editorFont()));

        // The gutter must match, or the line numbers drift out of alignment.
        int gutterWidth = opened[0].gutterWidth();
        report(gutterWidth > 0, "gutter still lays out after the change",
                "width=" + gutterWidth);

        System.out.println(failures == 0 ? "RESULT: PASS" : "RESULT: FAIL (" + failures + ")");
        System.exit(failures == 0 ? 0 : 1);
    }

    /** Picks a font family that is present, preferring a monospaced one. */
    private static String pickMonospacedFamily() {
        String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames();
        for (String preferred : new String[]{"Consolas", "Cascadia Mono", "Courier New",
                Font.MONOSPACED}) {
            for (String family : families) {
                if (family.equalsIgnoreCase(preferred)) {
                    return family;
                }
            }
        }
        return families.length > 0 ? families[0] : Font.MONOSPACED;
    }

    private static String describe(Font font) {
        return font.getFamily() + " " + font.getSize() + "pt";
    }

    private static void report(boolean ok, String label, String extra) {
        System.out.printf("  %-7s %-38s %s%n", ok ? "ok" : "FAIL", label, extra);
        if (!ok) {
            failures++;
        }
    }
}
