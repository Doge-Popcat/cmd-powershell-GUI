package dev;

import com.dogepopcat.cpgui.util.CommandLine;

import java.io.File;
import java.util.List;

/**
 * Developer check for the administrator mode plumbing.
 *
 * <p>It deliberately does not trigger a UAC prompt; it verifies the parts that
 * can be checked without one: that the running command line is recovered
 * correctly and that the executable it names really exists.</p>
 *
 * <pre>
 *   java -cp "build/classes;build/tools" dev.AdminProbe
 * </pre>
 */
public final class AdminProbe {

    private static int failures;

    private AdminProbe() {
    }

    public static void main(String[] args) {
        System.out.println("running command line: "
                + ProcessHandle.current().info().commandLine().orElse("(hidden)"));

        split("quoted exe with spaces",
                "\"C:\\Program Files\\Java\\bin\\java.exe\" -jar \"D:\\a b\\app.jar\"",
                "C:\\Program Files\\Java\\bin\\java.exe",
                "-jar \"D:\\a b\\app.jar\"");
        split("unquoted exe", "C:\\java\\bin\\java.exe -cp a;b Main",
                "C:\\java\\bin\\java.exe", "-cp a;b Main");
        split("no arguments", "\"C:\\java\\bin\\java.exe\"",
                "C:\\java\\bin\\java.exe", null);
        split("blank", "   ", null, null);

        List<String> current = CommandLine.currentProcess();
        System.out.println("recovered: " + current);
        boolean recovered = current.size() >= 2 && new File(current.get(0)).isFile();
        report(recovered, "current process command is usable",
                current.isEmpty() ? "(empty)" : current.get(0));

        if (current.size() >= 2) {
            report(current.get(1).contains("AdminProbe") || current.get(1).contains("cpgui"),
                    "argument string points at this application", current.get(1));
        }

        startProcessPlumbing();

        System.out.println(failures == 0 ? "RESULT: PASS" : "RESULT: FAIL (" + failures + ")");
        System.exit(failures == 0 ? 0 : 1);
    }

    /**
     * Exercises the exact PowerShell invocation used for the elevated restart,
     * with the {@code runas} verb left out so no permission prompt appears.
     */
    private static void startProcessPlumbing() {
        ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile",
                "-NonInteractive", "-Command",
                "Start-Process -FilePath $env:CPGUI_ELEVATE_EXE"
                        + " -ArgumentList $env:CPGUI_ELEVATE_ARGS -Wait");
        builder.environment().put("CPGUI_ELEVATE_EXE",
                System.getenv("SystemRoot") + "\\System32\\cmd.exe");
        builder.environment().put("CPGUI_ELEVATE_ARGS", "/c exit 0");
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(),
                    com.dogepopcat.cpgui.shell.ConsoleCharset.get());
            int exit = process.waitFor();
            report(exit == 0, "Start-Process plumbing works",
                    "exit=" + exit + (output.isBlank() ? "" : " " + output.strip()));
        } catch (Exception e) {
            report(false, "Start-Process plumbing works", "exception " + e);
        }
    }

    private static void split(String label, String input, String wantedExe, String wantedArgs) {
        List<String> parsed = CommandLine.split(input);
        boolean ok = parsed.isEmpty()
                ? wantedExe == null
                : parsed.size() >= 1 && parsed.get(0).equals(wantedExe)
                        && (wantedArgs == null
                                ? parsed.size() == 1
                                : parsed.size() == 2 && parsed.get(1).equals(wantedArgs));
        System.out.printf("  %-7s %-28s -> %s%n", ok ? "ok" : "FAIL", label, parsed);
        if (!ok) {
            failures++;
            System.out.println("        wanted exe=" + wantedExe + " args=" + wantedArgs);
        }
    }

    private static void report(boolean ok, String label, String extra) {
        System.out.printf("  %-7s %-28s %s%n", ok ? "ok" : "FAIL", label, extra);
        if (!ok) {
            failures++;
        }
    }
}
