package dev;

import com.dogepopcat.cpgui.ui.EditorTab;
import com.dogepopcat.cpgui.ui.MainFrame;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Developer check for the dangerous-command gate.
 *
 * <p>It substitutes the confirmation handler so both answers can be exercised
 * without a human. The scripts used here are deliberately harmless: the
 * destructive rule is triggered by a {@code rd /s /q} aimed at a folder that
 * does not exist, so accepting the prompt proves the command really was
 * submitted without deleting anything.</p>
 *
 * <pre>
 *   java -cp "build/classes;build/tools" dev.DangerProbe
 * </pre>
 */
public final class DangerProbe {

    private static final String SCRATCH = "D:\\cpgui-does-not-exist\\scratch";

    private DangerProbe() {
    }

    public static void main(String[] args) throws Exception {
        MainFrame[] holder = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new MainFrame();
            holder[0].setVisible(true);
        });
        MainFrame frame = holder[0];
        EditorTab tab = frame.editorTabs().current();

        String dangerous = String.join("\r\n",
                "echo before",
                "rd /s /q \"" + SCRATCH + "\"",
                "echo after");
        String safe = "echo safe line";

        AtomicInteger prompts = new AtomicInteger();
        AtomicInteger declined = new AtomicInteger();
        AtomicInteger accepted = new AtomicInteger();

        frame.setDangerConfirmation((List<com.dogepopcat.cpgui.analysis.DangerAnalyzer.Finding> d,
                                     String script) -> {
            prompts.incrementAndGet();
            declined.addAndGet(d.size());
            return false;
        });
        run(frame, tab, dangerous);
        Thread.sleep(2500);
        System.out.println("--- user cancels ---");
        System.out.println(tail(frame, 2));

        frame.setDangerConfirmation((List<com.dogepopcat.cpgui.analysis.DangerAnalyzer.Finding> d,
                                     String script) -> {
            prompts.incrementAndGet();
            accepted.addAndGet(d.size());
            return true;
        });
        run(frame, tab, dangerous);
        Thread.sleep(3000);
        System.out.println("--- user accepts ---");
        System.out.println(tail(frame, 8));

        run(frame, tab, safe);
        Thread.sleep(2500);
        System.out.println("--- safe script ---");
        System.out.println(tail(frame, 3));

        System.out.println("prompts=" + prompts.get()
                + " declinedFindings=" + declined.get()
                + " acceptedFindings=" + accepted.get());

        boolean ok = prompts.get() == 2 && declined.get() == 1 && accepted.get() == 1;
        System.out.println(ok ? "RESULT: PASS" : "RESULT: FAIL");
        System.exit(ok ? 0 : 1);
    }

    private static void run(MainFrame frame, EditorTab tab, String script) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            tab.setText(script);
            frame.runActiveScript();
        });
    }

    private static String tail(MainFrame frame, int lines) {
        String[] all = frame.console().getText().split("\\R");
        StringBuilder text = new StringBuilder();
        for (int i = Math.max(0, all.length - lines); i < all.length; i++) {
            text.append("    |").append(all[i]).append(System.lineSeparator());
        }
        return text.toString();
    }
}
