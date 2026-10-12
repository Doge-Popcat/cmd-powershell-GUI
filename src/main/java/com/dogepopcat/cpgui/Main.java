package com.dogepopcat.cpgui;

import com.dogepopcat.cpgui.ui.MainFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Font;

/**
 * Application entry point.
 *
 * <p>The whole UI is plain Swing so the program has no third-party runtime
 * dependencies and runs on any desktop JDK out of the box.</p>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        // Crisper text rendering on Windows high-DPI displays.
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        SwingUtilities.invokeLater(() -> {
            applyLookAndFeel();
            new MainFrame().setVisible(true);
        });
    }

    private static void applyLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // The cross-platform look and feel is a perfectly fine fallback.
        }
        UIManager.put("Tree.paintLines", Boolean.TRUE);
        UIManager.put("Table.showGrid", Boolean.TRUE);
        UIManager.put("Table.intercellSpacing", new java.awt.Dimension(0, 0));
        installMonospacedUIFont();
    }

    /**
     * Editor widgets must use a monospaced font even when the platform look and
     * feel would pick a proportional one.
     */
    private static void installMonospacedUIFont() {
        Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 13);
        for (String key : new String[]{
                "TextArea.font", "TextPane.font", "EditorPane.font",
                "TextField.font", "List.font", "Table.font"}) {
            Object current = UIManager.get(key);
            if (current instanceof Font) {
                UIManager.put(key, mono);
            }
        }
    }
}
