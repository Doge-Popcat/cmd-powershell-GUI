package dev;

import com.dogepopcat.cpgui.completion.CommandCatalog;
import com.dogepopcat.cpgui.completion.CompletionItem;
import com.dogepopcat.cpgui.completion.CompletionProvider;
import com.dogepopcat.cpgui.shell.ShellKind;

import java.util.List;

/**
 * Developer check for the completion provider.
 *
 * <pre>
 *   java -cp "build/classes;build/tools" dev.CompletionProbe
 * </pre>
 */
public final class CompletionProbe {

    private static int failures;

    private CompletionProbe() {
    }

    public static void main(String[] args) {
        // cmd: command names
        expect("cmd command", ShellKind.CMD, "di", 2, "dir");
        expect("cmd command mid-text", ShellKind.CMD, "echo hi\r\ndir /b", 12, "dir");
        // cmd: switches of the command being typed
        expect("cmd switch", ShellKind.CMD, "dir /", 5, "/s");
        expect("cmd xcopy switch", ShellKind.CMD, "xcopy /", 7, "/y");
        // cmd: variables
        expect("cmd variable", ShellKind.CMD, "echo %PA", 8, "%PATH%");
        expect("cmd variable meta", ShellKind.CMD, "echo %~", 7, "%~dp0");
        // cmd: unknown command still completes
        expect("cmd unknown", ShellKind.CMD, "roboc", 5, "robocopy");

        // PowerShell: cmdlets
        expect("ps cmdlet", ShellKind.POWERSHELL, "Get-Ch", 6, "Get-ChildItem");
        expect("ps cmdlet lower", ShellKind.POWERSHELL, "get-ch", 6, "Get-ChildItem");
        // PowerShell: keywords
        expect("ps keyword", ShellKind.POWERSHELL, "forea", 5, "foreach");
        // PowerShell: common parameters
        expect("ps parameter", ShellKind.POWERSHELL, "Remove-Item -W", 14, "-WhatIf");
        // PowerShell: variables
        expect("ps variable", ShellKind.POWERSHELL, "$env:PA", 7, "$env:PATH");
        expect("ps automatic", ShellKind.POWERSHELL, "$PWD", 4, "$PWD");
        // PowerShell: substring fallback
        expect("ps substring", ShellKind.POWERSHELL, "Item", 4, "Get-Item");

        System.out.println();
        pathChecks();
        hintChecks();

        System.out.println();
        System.out.println("catalog sizes: cmd=" + CommandCatalog.commands(ShellKind.CMD).size()
                + " powershell=" + CommandCatalog.commands(ShellKind.POWERSHELL).size()
                + " psParameters="
                + CommandCatalog.commonParameters(ShellKind.POWERSHELL).size());
        System.out.println(failures == 0 ? "RESULT: PASS" : "RESULT: FAIL (" + failures + ")");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void pathChecks() {
        java.io.File base = new java.io.File(System.getProperty("user.dir"));
        System.out.println("path completion against " + base);
        expectPath("dir into src", "src\\ma", base, "src\\main\\");
        expectPath("file in build", "build\\cpgui", base, "build\\cpgui.jar");
        expectPath("dot relative", ".\\sr", base, ".\\src\\");
    }

    private static void expectPath(String label, String word, java.io.File base, String wanted) {
        List<CompletionItem> items = com.dogepopcat.cpgui.completion.PathCompleter
                .complete(word, base, ShellKind.CMD);
        boolean found = items.stream().anyMatch(item -> item.label().equals(wanted));
        System.out.printf("  %-7s %-22s count=%-4d wanted=%s%n",
                found ? "ok" : "FAIL", label, items.size(), wanted);
        if (!found) {
            failures++;
            System.out.println("        got: " + items.stream()
                    .limit(6).map(CompletionItem::label).toList());
        }
    }

    private static void hintChecks() {
        System.out.println("parameter hints");
        expectHint("cmd dir", ShellKind.CMD, "dir /b", 6, "dir");
        expectHint("cmd xcopy", ShellKind.CMD, "xcopy /e", 8, "xcopy");
        expectHint("ps cmdlet", ShellKind.POWERSHELL, "Get-ChildItem -Pa", 16, "Get-ChildItem");
        expectHint("unknown command", ShellKind.CMD, "notacommand /x", 14, null);
        expectHint("still typing the name", ShellKind.CMD, "di", 2, null);
    }

    private static void expectHint(String label, ShellKind kind, String text, int caret,
                                   String wantedCommand) {
        com.dogepopcat.cpgui.completion.ParameterHint hint =
                com.dogepopcat.cpgui.completion.ParameterHint.forCaret(kind, text, caret);
        String actual = hint == null ? null : hint.normalisedCommand();
        boolean ok = wantedCommand == null ? actual == null
                : wantedCommand.toLowerCase(java.util.Locale.ROOT).equals(actual);
        System.out.printf("  %-7s %-22s hint=%-18s params=%s%n",
                ok ? "ok" : "FAIL", label, actual,
                hint == null ? "-" : hint.parameters().size());
        if (!ok) {
            failures++;
            System.out.println("        wanted: " + wantedCommand);
        }
    }

    private static void expect(String label, ShellKind kind, String text, int caret,
                               String wanted) {
        CompletionProvider.Result result = CompletionProvider.complete(
                new CompletionProvider.Context(kind, text, caret));
        boolean found = false;
        for (CompletionItem item : result.items()) {
            if (item.label().equalsIgnoreCase(wanted)) {
                found = true;
                break;
            }
        }
        String first = result.items().isEmpty() ? "(none)" : result.items().get(0).label();
        System.out.printf("  %-7s %-22s replace=%d..%d count=%-4d first=%-18s wanted=%s%n",
                found ? "ok" : "FAIL", label, result.replaceStart(), result.replaceEnd(),
                result.items().size(), first, wanted);
        if (!found) {
            failures++;
            List<CompletionItem> sample = result.items().size() > 5
                    ? result.items().subList(0, 5) : result.items();
            System.out.println("        sample: " + sample);
        }
    }
}
