package dev;

import com.dogepopcat.cpgui.ui.MainFrame;

import javax.swing.Action;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.event.KeyEvent;

/**
 * Developer check for the single line console prompt.
 *
 * <pre>
 *   java -cp "build/classes;build/tools" dev.ConsoleProbe
 * </pre>
 */
public final class ConsoleProbe {

    private static int failures;

    private ConsoleProbe() {
    }

    public static void main(String[] args) {
        // Pin the language so assertions do not depend on the machine locale.
        com.dogepopcat.cpgui.i18n.Messages.setLanguage(com.dogepopcat.cpgui.i18n.Language.ENGLISH);
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

        // Type a command at the console prompt and submit it with Enter.
        SwingUtilities.invokeAndWait(() -> {
            frame.console().inputField().setText("echo CONSOLE-INPUT-OK");
            frame.console().inputField().postActionEvent();
        });
        Thread.sleep(3000);
        String console = frame.console().getText();
        report(console.contains("CONSOLE-INPUT-OK"),
                "typed command produced output", lastLines(console, 4));
        report(console.contains("CMD> echo CONSOLE-INPUT-OK"),
                "command was echoed with a prompt", "");
        report(frame.console().inputField().getText().isEmpty(),
                "input field cleared after Enter", "");

        // A second command must be separated from the first block.
        SwingUtilities.invokeAndWait(() -> {
            frame.console().inputField().setText("ver");
            frame.console().inputField().postActionEvent();
        });
        Thread.sleep(2500);
        console = frame.console().getText();
        report(console.contains("\u2500"), "blocks are separated by a rule", "");
        report(console.contains("Microsoft Windows") || console.toLowerCase().contains("version"),
                "second command ran", lastLines(console, 3));

        // Up recalls the previous command, through the field's own key binding.
        SwingUtilities.invokeAndWait(() -> {
            Object key = frame.console().inputField().getInputMap()
                    .get(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0));
            Action action = frame.console().inputField().getActionMap().get(key);
            action.actionPerformed(new java.awt.event.ActionEvent(
                    frame.console().inputField(), java.awt.event.ActionEvent.ACTION_PERFORMED, "up"));
        });
        String recalled = frame.console().inputField().getText();
        report("ver".equals(recalled) || "echo CONSOLE-INPUT-OK".equals(recalled),
                "Up recalls an earlier command", recalled);
        SwingUtilities.invokeAndWait(() -> frame.console().inputField().setText(""));

        System.out.println(failures == 0 ? "RESULT: PASS" : "RESULT: FAIL (" + failures + ")");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static String lastLines(String text, int count) {
        String[] lines = text.split("\\R");
        StringBuilder result = new StringBuilder();
        for (int i = Math.max(0, lines.length - count); i < lines.length; i++) {
            result.append('|').append(lines[i]).append(' ');
        }
        return result.toString().strip();
    }

    private static void report(boolean ok, String label, String extra) {
        System.out.printf("  %-7s %-36s %s%n", ok ? "ok" : "FAIL", label, extra);
        if (!ok) {
            failures++;
        }
    }
}
