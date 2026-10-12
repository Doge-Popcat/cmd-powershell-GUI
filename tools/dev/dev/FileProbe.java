package dev;

import com.dogepopcat.cpgui.io.ScriptFile;
import com.dogepopcat.cpgui.shell.ConsoleCharset;
import com.dogepopcat.cpgui.shell.ShellKind;
import com.dogepopcat.cpgui.ui.EditorTab;
import com.dogepopcat.cpgui.ui.MainFrame;

import javax.swing.SwingUtilities;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Developer check for script reading, writing and the unsaved-changes flag.
 *
 * <p>Everything is written under {@code build/scratch}, so nothing outside the
 * project is touched.</p>
 *
 * <pre>
 *   java -cp "build/classes;build/tools" dev.FileProbe
 * </pre>
 */
public final class FileProbe {

    private static final String SAMPLE = String.join("\r\n",
            "echo === 中文测试 ===",
            "echo accented: café résumé",
            "echo done");
    private static final String PS_SAMPLE = String.join("\r\n",
            "# 中文注释",
            "Write-Output '中文测试 café naïve'");

    private static int failures;

    private FileProbe() {
    }

    public static void main(String[] args) throws Exception {
        File scratch = new File("build/scratch");
        Files.createDirectories(scratch.toPath());

        roundTrip(scratch, ShellKind.CMD, "sample.cmd", SAMPLE);
        roundTrip(scratch, ShellKind.POWERSHELL, "sample.ps1", PS_SAMPLE);

        bomCheck(scratch);
        extensionCheck();
        lossyCheck(scratch);
        runThroughCmd(scratch);
        uiCheck(scratch);

        System.out.println(failures == 0 ? "RESULT: PASS" : "RESULT: FAIL (" + failures + ")");
        System.exit(failures == 0 ? 0 : 1);
    }

    /** Characters outside the console code page must be reported, not dropped. */
    private static void lossyCheck(File dir) {
        File file = new File(dir, "lossy.cmd");
        String text = "echo naïve";
        try {
            ScriptFile.write(file, text, ShellKind.CMD);
            report(false, "unrepresentable text is rejected", "no exception thrown");
        } catch (ScriptFile.UnrepresentableCharactersException e) {
            report(e.characters().indexOf('\u00ef') >= 0,
                    "unrepresentable text is rejected",
                    "offending=" + e.characters());
        } catch (Exception e) {
            report(false, "unrepresentable text is rejected", "wrong exception " + e);
        }

        try {
            ScriptFile.writeLossy(file, text, ShellKind.CMD);
            String read = ScriptFile.read(file);
            // No trailing newline is added: the buffer had none.
            report("echo na?ve".equals(read), "lossy write replaces with '?'", read.strip());
        } catch (Exception e) {
            report(false, "lossy write replaces with '?'", "exception " + e);
        }
    }

    private static void roundTrip(File dir, ShellKind kind, String name, String content)
            throws Exception {
        File file = new File(dir, name);
        ScriptFile.write(file, content, kind);
        String read = ScriptFile.read(file);
        boolean ok = read.equals(content);
        report(ok, "round trip " + name,
                "charset=" + ScriptFile.writeCharset(kind).name() + " bytes=" + file.length()
                        + (ok ? "" : " got=" + read.replace("\r\n", " / ")));
    }

    private static void bomCheck(File dir) throws Exception {
        byte[] ps = Files.readAllBytes(new File(dir, "sample.ps1").toPath());
        boolean bom = ps.length >= 3 && (ps[0] & 0xFF) == 0xEF && (ps[1] & 0xFF) == 0xBB
                && (ps[2] & 0xFF) == 0xBF;
        report(bom, "ps1 written with UTF-8 BOM", "first bytes=" + hex(ps, 4));

        byte[] cmd = Files.readAllBytes(new File(dir, "sample.cmd").toPath());
        boolean noBom = !(cmd.length >= 3 && (cmd[0] & 0xFF) == 0xEF);
        report(noBom, "cmd written in console code page",
                "charset=" + ConsoleCharset.get().name() + " chars=" + cmd.length);
    }

    private static void extensionCheck() {
        report(ScriptFile.shellKindFor(new File("a.ps1")) == ShellKind.POWERSHELL,
                "ps1 maps to PowerShell", "");
        report(ScriptFile.shellKindFor(new File("a.cmd")) == ShellKind.CMD, "cmd maps to CMD", "");
        report(ScriptFile.shellKindFor(new File("a.bat")) == ShellKind.CMD, "bat maps to CMD", "");
        report(ScriptFile.shellKindFor(null) == ShellKind.CMD, "null maps to CMD", "");
    }

    /** Writes a batch file and lets cmd.exe run it, to prove the encoding works. */
    private static void runThroughCmd(File dir) throws Exception {
        File file = new File(dir, "runnable.cmd");
        ScriptFile.write(file, "@echo off\r\necho 中文输出测试\r\necho plain-ascii", ShellKind.CMD);

        ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", file.getAbsolutePath());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), ConsoleCharset.get());
        process.waitFor();

        boolean ok = output.contains("中文输出测试") && output.contains("plain-ascii");
        report(ok, "cmd.exe runs the written file", output.strip().replace("\r\n", " / "));
    }

    /** Drives open, edit, save and the dirty flag through the real window. */
    private static void uiCheck(File dir) throws Exception {
        File file = new File(dir, "ui-roundtrip.cmd");
        Files.write(file.toPath(), "echo first".getBytes(ConsoleCharset.get()));

        MainFrame[] holder = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new MainFrame();
            holder[0].setVisible(true);
        });
        MainFrame frame = holder[0];

        EditorTab[] tabHolder = new EditorTab[1];
        SwingUtilities.invokeAndWait(() -> tabHolder[0] = frame.openFile(file));
        EditorTab tab = tabHolder[0];
        report(tab != null && "echo first".equals(tab.getText()), "open loads the file",
                tab == null ? "null tab" : "title=" + tab.title());
        if (tab == null) {
            return;
        }
        report(!tab.isDirty(), "freshly opened tab is clean", "title=" + tab.title());
        report(tab.file() != null && tab.file().getName().equals("ui-roundtrip.cmd"),
                "file association set", String.valueOf(tab.file()));

        // A real document edit, not setText, must mark the tab dirty.
        SwingUtilities.invokeAndWait(() -> {
            try {
                tab.document().insertString(tab.document().getLength(), "\r\necho third", null);
            } catch (javax.swing.text.BadLocationException e) {
                throw new IllegalStateException(e);
            }
        });
        Thread.sleep(200);
        report(tab.isDirty(), "editing marks the tab dirty", "title=" + tab.title());

        boolean[] saved = new boolean[1];
        SwingUtilities.invokeAndWait(() -> saved[0] = frame.saveTab(tab, file));
        Thread.sleep(200);
        report(saved[0] && !tab.isDirty(), "save clears the dirty flag", "title=" + tab.title());

        String onDisk = new String(Files.readAllBytes(file.toPath()), ConsoleCharset.get());
        report("echo first\r\necho third".equals(onDisk),
                "saved file uses CRLF line endings",
                onDisk.replace("\r\n", "|").replace("\n", "!"));

        // A .ps1 opened later must be recognised as PowerShell.
        File psFile = new File(dir, "ui-roundtrip.ps1");
        Files.write(psFile.toPath(), "Write-Output 'hi'".getBytes(StandardCharsets.UTF_8));
        EditorTab[] psHolder = new EditorTab[1];
        SwingUtilities.invokeAndWait(() -> psHolder[0] = frame.openFile(psFile));
        report(psHolder[0] != null && psHolder[0].shellKind() == ShellKind.POWERSHELL,
                "ps1 opens as a PowerShell tab",
                psHolder[0] == null ? "null" : psHolder[0].shellKind().displayName());

        System.exit(0);
    }

    private static void report(boolean ok, String label, String extra) {
        System.out.printf("  %-7s %-34s %s%n", ok ? "ok" : "FAIL", label, extra);
        if (!ok) {
            failures++;
        }
    }

    private static String hex(byte[] bytes, int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < Math.min(count, bytes.length); i++) {
            text.append(String.format("%02X ", bytes[i]));
        }
        return text.toString().trim();
    }
}
