package dev;

import com.dogepopcat.cpgui.analysis.DangerAnalyzer;
import com.dogepopcat.cpgui.analysis.DangerAnalyzer.Finding;
import com.dogepopcat.cpgui.shell.ShellKind;

import java.util.List;

/**
 * Developer check for the unknown-command diagnostics.
 *
 * <p>The interesting half is the false positives: a linter that flags real
 * commands is worse than no linter at all, so every case below is a command
 * this machine can genuinely run.</p>
 *
 * <pre>
 *   java -cp "build/classes;build/tools" dev.LintProbe
 * </pre>
 */
public final class LintProbe {

    private static int failures;

    private LintProbe() {
    }

    public static void main(String[] args) {
        // Pin the language so assertions do not depend on the machine locale.
        com.dogepopcat.cpgui.i18n.Messages.setLanguage(com.dogepopcat.cpgui.i18n.Language.ENGLISH);
        System.out.println("typos are reported");
        expectUnknown(ShellKind.CMD, "exyzxo");
        expectUnknown(ShellKind.CMD, "exyzxo /b");
        expectUnknown(ShellKind.POWERSHELL, "Get-NonexistentThing");
        expectUnknown(ShellKind.POWERSHELL, "notacmd -Force");

        System.out.println("real commands are left alone");
        for (String script : new String[]{
                "dir /b", "cd /d C:\\", "echo hello", "mkdir newfolder", "del /q temp.txt",
                "copy a.txt b.txt", "xcopy /e a b", "robocopy a b /mir", "where cmd",
                "ipconfig /all", "ping -n 1 localhost", "timeout /t 1", "setx FOO bar",
                "cacls C:\\", "whoami /groups", "for %i in (a) do echo %i", "if exist a echo b",
                "call :label", "goto :eof", "start notepad", "tasklist", "ver", "pause"}) {
            expectKnown(ShellKind.CMD, script);
        }
        for (String script : new String[]{
                "Get-ChildItem", "Write-Output 'hi'", "Remove-Item -WhatIf x", "ls",
                "cat file.txt", "$x = 1", "foreach ($i in 1..3) { $i }", "if ($true) { 1 }",
                "& 'C:\\Windows\\System32\\cmd.exe'", ".\\script.ps1", "Get-Service | Stop-Service"}) {
            expectKnown(ShellKind.POWERSHELL, script);
        }

        System.out.println("paths, variables and wildcards are never guessed at");
        for (String script : new String[]{
                "C:\\Windows\\System32\\notepad.exe", "%windir%\\system32\\cmd.exe",
                "..\\build\\run.bat", "*.log", "@echo off"}) {
            expectKnown(ShellKind.CMD, script);
        }

        System.out.println("a destructive command is still reported as such");
        List<Finding> findings = DangerAnalyzer.analyze(ShellKind.CMD, "rd /s /q D:\\scratch\\x");
        boolean danger = findings.size() == 1
                && findings.get(0).severity() == DangerAnalyzer.Severity.DANGER;
        report(danger, "rd /s /q keeps its danger finding",
                findings.isEmpty() ? "(none)" : findings.get(0).ruleId());

        System.out.println(failures == 0 ? "RESULT: PASS" : "RESULT: FAIL (" + failures + ")");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void expectUnknown(ShellKind kind, String script) {
        List<Finding> findings = DangerAnalyzer.analyze(kind, script);
        boolean found = findings.stream().anyMatch(f -> f.ruleId().equals("unknown.command"));
        report(found, "reports " + script,
                findings.isEmpty() ? "(no findings)" : findings.get(0).message());
    }

    private static void expectKnown(ShellKind kind, String script) {
        List<Finding> findings = DangerAnalyzer.analyze(kind, script);
        boolean clean = findings.stream().noneMatch(f -> f.ruleId().equals("unknown.command"));
        report(clean, "accepts " + script,
                clean ? "" : findings.stream()
                        .filter(f -> f.ruleId().equals("unknown.command"))
                        .findFirst().map(Finding::message).orElse(""));
    }

    private static void report(boolean ok, String label, String extra) {
        System.out.printf("  %-7s %-40s %s%n", ok ? "ok" : "FAIL", label, extra);
        if (!ok) {
            failures++;
        }
    }
}
