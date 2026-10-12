package dev;

import com.dogepopcat.cpgui.analysis.DangerAnalyzer;
import com.dogepopcat.cpgui.analysis.ScriptParser;
import com.dogepopcat.cpgui.shell.ShellKind;

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
