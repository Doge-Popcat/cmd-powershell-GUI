package dev;

import com.dogepopcat.cpgui.util.CommandLine;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Developer check for the administrator mode plumbing.
 *
 * <p>It deliberately does not trigger a UAC prompt; it verifies everything that
 * leads up to it: the command that started this program is recovered and
 * resolved to a file that exists, the packaged application image (a launcher
 * whose bundled runtime has no {@code java.exe}) is started again as itself, and
 * an argument string survives the trip through {@code Start-Process} intact.</p>
 *
 * <pre>
 *   java -cp "build/classes;build/tools" dev.AdminProbe
 * </pre>
 */
public final class AdminProbe {

    /** Main class of the program, used where a command line is rebuilt. */
    private static final String MAIN = "com.dogepopcat.cpgui.Main";

    /** Stands in for the launcher of a packaged application image. */
    private static final String LAUNCHER =
            canonical(System.getenv("SystemRoot") + "\\System32\\cmd.exe");

    private static final String JAVA = canonical(System.getProperty("java.home")
            + File.separator + "bin" + File.separator + "java.exe");

    private static final String CLASS_PATH = System.getProperty("java.class.path", "");

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

        relaunch();
        argumentPassing();
        startProcessPlumbing();

        System.out.println(failures == 0 ? "RESULT: PASS" : "RESULT: FAIL (" + failures + ")");
        System.exit(failures == 0 ? 0 : 1);
    }

    /**
     * Checks how the program works out the command that starts it again, which is
     * the step that decides whether administrator mode can work at all.
     */
    private static void relaunch() {
        String quotedClassPath = CLASS_PATH.indexOf(' ') >= 0 ? "\"" + CLASS_PATH + "\"" : CLASS_PATH;
        String jar = "D:\\cpgui probe\\cpgui.jar";

        // The packaged application image: a launcher started without arguments,
        // whose bundled runtime has no java.exe. It has to be started again as
        // itself, because there is no Java executable to use. This is the bug
        // that made administrator mode fail in the packaged build: the fallback
        // named <app>\runtime\bin\java.exe, which jpackage does not ship.
        expect("packaged image launcher", List.of(LAUNCHER, ""),
                CommandLine.relaunchCommand("\"" + LAUNCHER + "\"", LAUNCHER, null,
                        CLASS_PATH, MAIN));
        expect("packaged image launcher, java.exe known", List.of(LAUNCHER, ""),
                CommandLine.relaunchCommand("\"" + LAUNCHER + "\"", LAUNCHER, JAVA,
                        CLASS_PATH, MAIN));
        expect("launcher without arguments, no java.exe", List.of(LAUNCHER, ""),
                CommandLine.relaunchCommand(null, LAUNCHER, null, CLASS_PATH, MAIN));

        // A start that really goes through a JVM keeps using that JVM.
        expect("java -jar", List.of(JAVA, "-jar \"" + jar + "\""),
                CommandLine.relaunchCommand(null, JAVA, JAVA, jar, jar));
        expect("java -cp with a visible command line",
                List.of(JAVA, "-cp \"" + CLASS_PATH + "\" " + MAIN),
                CommandLine.relaunchCommand("\"" + JAVA + "\" -cp \"" + CLASS_PATH + "\" " + MAIN,
                        JAVA, JAVA, CLASS_PATH, MAIN));
        expect("java -cp rebuilt from the JVM",
                List.of(JAVA, "-cp " + quotedClassPath + " " + MAIN),
                CommandLine.relaunchCommand(null, JAVA, JAVA, CLASS_PATH, MAIN));
        expect("a bare java launcher is not started without arguments",
                List.of(JAVA, "-cp " + quotedClassPath + " " + MAIN),
                CommandLine.relaunchCommand("\"" + JAVA + "\"", JAVA, JAVA, CLASS_PATH, MAIN));

        expect("nothing to go on", List.of(),
                CommandLine.relaunchCommand(null, null, null, "", ""));
    }

    /**
     * Exercises the whole quoting chain the elevated restart uses: the argument
     * string travels through an environment variable into {@code Start-Process},
     * and the program that is started must see it intact. The report switch of
     * the program itself is the observable result, and the path deliberately
     * holds a space, because such a path is what quoting rules get wrong.
     */
    private static void argumentPassing() {
        Path directory = null;
        try {
            directory = Files.createTempDirectory("cpgui elevation ");
            Path reportFile = directory.resolve("report.txt");
            String arguments = "-cp \"" + CLASS_PATH + "\" " + MAIN
                    + " --elevation-report \"" + reportFile + "\"";

            ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile",
                    "-NonInteractive", "-Command",
                    "Start-Process -FilePath $env:CPGUI_ELEVATE_EXE"
                            + " -ArgumentList $env:CPGUI_ELEVATE_ARGS -Wait");
            builder.environment().put("CPGUI_ELEVATE_EXE", JAVA);
            builder.environment().put("CPGUI_ELEVATE_ARGS", arguments);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(),
                    com.dogepopcat.cpgui.shell.ConsoleCharset.get());
            int exit = process.waitFor();

            String text = Files.exists(reportFile) ? Files.readString(reportFile) : "";
            report(exit == 0 && text.contains("relaunch executable"),
                    "quoted arguments reach the program",
                    exit == 0
                            ? (text.isBlank() ? "(no report written)" : "report written")
                            : "exit=" + exit + " " + output.strip());
        } catch (Exception e) {
            report(false, "quoted arguments reach the program", "exception " + e);
        } finally {
            deleteQuietly(directory);
        }
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

    private static String canonical(String path) {
        try {
            return new File(path).getCanonicalPath();
        } catch (java.io.IOException e) {
            return path;
        }
    }

    private static void deleteQuietly(Path directory) {
        if (directory == null) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                    // A leftover probe file is not worth failing the check.
                }
            });
        } catch (Exception ignored) {
            // Same here.
        }
    }

    private static void expect(String label, List<String> wanted, List<String> actual) {
        report(wanted.equals(actual), label, actual.toString());
        if (!wanted.equals(actual)) {
            System.out.println("        wanted " + wanted);
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
