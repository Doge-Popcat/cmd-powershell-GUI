package dev;

import com.dogepopcat.cpgui.analysis.DangerAnalyzer;
import com.dogepopcat.cpgui.analysis.ScriptParser;
import com.dogepopcat.cpgui.shell.ShellKind;

import java.util.List;

/**
 * Developer check for the script parser and the danger rules.
 *
 * <pre>
 *   java -cp "build/classes;build/tools" dev.ParserProbe
 * </pre>
 */
public final class ParserProbe {

    private ParserProbe() {
    }

    public static void main(String[] args) {
        // Pin the language so assertions do not depend on the machine locale.
        com.dogepopcat.cpgui.i18n.Messages.setLanguage(com.dogepopcat.cpgui.i18n.Language.ENGLISH);
        String cmd = String.join("\r\n",
                "REM a comment",
                ":: another comment",
                "",
                "echo one",
                "dir ^",
                "  /b ^",
                "  /a",
                "echo two & echo three",
                "copy a.txt b.txt");
        String ps = String.join("\n",
                "# a comment",
                "",
                "Write-Output 'one'",
                "Get-ChildItem `",
                "  -Path . `",
                "  -Recurse",
                "$x = 1; $y = 2",
                "   # indented comment");
        String risky = String.join("\r\n",
                "echo safe",
                "rd /s /q \"D:\\scratch\\demo\"",
                "del /f /s /q \"D:\\scratch\\demo\\*\"",
                "format D:",
                "shutdown /a",
                "Write-Output 'also safe'");

        showParse("CMD", ShellKind.CMD, cmd);
        showParse("POWERSHELL", ShellKind.POWERSHELL, ps);
        showDanger("CMD danger rules", ShellKind.CMD, risky);
        showDanger("PowerShell danger rules", ShellKind.POWERSHELL, String.join("\n",
                "Remove-Item -Path 'D:\\scratch\\demo' -Recurse -Force",
                "Invoke-Expression $payload",
                "Set-ExecutionPolicy Unrestricted",
                "Get-ChildItem"));

        // The dump above is for reading; these are the assertions.
        System.out.println("======== assertions ========");
        List<ScriptParser.Command> parsed = ScriptParser.parse(ShellKind.CMD, cmd);
        // parse() returns every line, comments included, each with a comment flag;
        // executable() is what tells them apart.
        long cmdCommands = parsed.stream().filter(ScriptParser.Command::executable).count();
        report(cmdCommands == 4, "comments and blanks are not executable",
                "executable=" + cmdCommands + " of " + parsed.size() + " lines");
        report(parsed.stream().anyMatch(ScriptParser.Command::comment),
                "comment lines are reported as comments", "");
        report(parsed.stream().anyMatch(c -> c.text().startsWith("echo one")),
                "the first real command is kept", "");
        // A caret continues the line, so the three physical lines are one command.
        report(parsed.stream().anyMatch(c -> c.text().contains("dir") && c.text().contains("/a")),
                "a caret continuation joins into one command", "");
        report(parsed.stream().anyMatch(c -> c.text().contains("&")),
                "an ampersand stays inside its command", "");

        List<ScriptParser.Command> psParsed = ScriptParser.parse(ShellKind.POWERSHELL, ps);
        long psCommands = psParsed.stream().filter(ScriptParser.Command::executable).count();
        report(psCommands == 3, "PowerShell comments are not executable",
                "executable=" + psCommands + " of " + psParsed.size() + " lines");
        report(psParsed.stream().anyMatch(c -> c.text().contains("$x") && c.text().contains("$y")),
                "a semicolon line stays one command", "");

        // Offsets must point back at the original text exactly.
        // Offsets must point back at the original text. Line endings are compared
        // loosely because a continued line is reported joined with plain newlines.
        boolean offsetsOk = parsed.stream()
                .filter(ScriptParser.Command::executable)
                .allMatch(c -> flatten(cmd.substring(c.startOffset(), c.endOffset()))
                        .equals(flatten(c.text())));
        report(offsetsOk, "every command offset matches the source text", "");

        // dangers() is the subset the confirmation dialog shows, which is the
        // DANGER severity only; the shutdown line is a warning.
        report(DangerAnalyzer.dangers(ShellKind.CMD, risky).size() == 3,
                "the three dangerous cmd lines are flagged",
                "dangers=" + DangerAnalyzer.dangers(ShellKind.CMD, risky).size());
        report(DangerAnalyzer.analyze(ShellKind.CMD, risky).stream()
                        .anyMatch(f -> f.severity() == DangerAnalyzer.Severity.WARNING),
                "a merely risky line warns instead", "");
        report(DangerAnalyzer.dangers(ShellKind.POWERSHELL,
                        "Remove-Item -Path 'D:\\scratch\\demo' -Recurse -Force").size() == 1,
                "the risky PowerShell script is flagged", "");
        report(DangerAnalyzer.dangers(ShellKind.CMD, "echo safe").isEmpty(),
                "a harmless line is not flagged", "");

        System.out.println();
        System.out.println(failures == 0 ? "RESULT: PASS" : "RESULT: FAIL (" + failures + ")");
        System.exit(failures == 0 ? 0 : 1);
    }

    /** Folds the two line ending styles together for comparison. */
    private static String flatten(String text) {
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }

    private static int failures;

    private static void report(boolean ok, String label, String extra) {
        if (!ok) {
            failures++;
        }
        System.out.printf("  %-7s %-52s %s%n", ok ? "ok" : "FAIL", label, extra);
    }

    private static void showParse(String label, ShellKind kind, String script) {
        System.out.println("======== " + label + " parse ========");
        for (ScriptParser.Command command : ScriptParser.parse(kind, script)) {
            System.out.printf("  line=%-2d comment=%-5s exec=%-5s offset=%d..%d text=%s%n",
                    command.lineIndex(), command.comment(), command.executable(),
                    command.startOffset(), command.endOffset(),
                    command.text().replace("\n", "\\n").replace("\r", ""));
        }
        System.out.println();
    }

    private static void showDanger(String label, ShellKind kind, String script) {
        System.out.println("======== " + label + " ========");
        for (DangerAnalyzer.Finding finding : DangerAnalyzer.analyze(kind, script)) {
            System.out.printf("  %-8s line=%-2d %s%n     advice: %s%n",
                    finding.severity(), finding.lineIndex() + 1,
                    finding.message(), finding.advice());
        }
        System.out.println();
    }
}
