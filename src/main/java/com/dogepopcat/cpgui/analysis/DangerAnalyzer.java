package com.dogepopcat.cpgui.analysis;

import com.dogepopcat.cpgui.shell.ShellKind;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static checker that flags commands that can destroy data, change system
 * security or hide what they are doing.
 *
 * <p>The rules are deliberately pattern based rather than exhaustive: the goal
 * is to make a user pause before an irreversible action, not to be a security
 * product. Every finding carries an explanation and a suggestion so the warning
 * is actionable.</p>
 */
public final class DangerAnalyzer {

    /** How much attention a finding deserves. */
    public enum Severity {
        /** Worth knowing, nothing more. */
        INFO,
        /** Changes state in a way that is hard to undo. */
        WARNING,
        /** Can destroy data or break the machine. */
        DANGER
    }

    /**
     * One problem found in a script.
     *
     * @param severity  how serious the problem is
     * @param ruleId    stable identifier of the rule that fired
     * @param message   short description of what the command does
     * @param advice    what the user should consider instead
     * @param start     document offset where the offending command starts
     * @param end       document offset just past the command
     * @param lineIndex zero based line of the command
     * @param snippet   the offending source text, trimmed for display
     */
    public record Finding(Severity severity, String ruleId, String message, String advice,
                          int start, int end, int lineIndex, String snippet) {
    }

    /**
     * A single detection rule.
     *
     * @param id       stable identifier
     * @param kind     interpreter the rule applies to, or {@code null} for both
     * @param severity severity of a match
     * @param pattern  expression matched against one command
     * @param message  description of the danger
     * @param advice   suggested alternative
     */
    private record Rule(String id, ShellKind kind, Severity severity, Pattern pattern,
                        String message, String advice) {
    }

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private static Pattern re(String expression) {
        return Pattern.compile(expression, FLAGS);
    }

    private static final List<Rule> RULES = List.of(
            // --- irreversible destruction ------------------------------------
            new Rule("cmd.format", ShellKind.CMD, Severity.DANGER,
                    re("(^|[\\s&|])format\\s+[a-z]:"),
                    "format erases an entire drive",
                    "Double-check the drive letter; this cannot be undone."),
            new Rule("cmd.diskpart", ShellKind.CMD, Severity.DANGER,
                    re("(^|[\\s&|])diskpart([\\s&|]|$)"),
                    "diskpart can repartition or wipe disks",
                    "Run it manually only when you are certain which disk is targeted."),
            new Rule("cmd.del.force", ShellKind.CMD, Severity.DANGER,
                    re("(^|[\\s&|])(del|erase)(\\s|$)[^\\r\\n]*/[a-z]*[fs]"),
                    "del with /f or /s deletes files permanently",
                    "Recycle them with PowerShell instead, or remove the /f and /s switches."),
            new Rule("cmd.rd.tree", ShellKind.CMD, Severity.DANGER,
                    re("(^|[\\s&|])(rd|rmdir)(\\s|$)[^\\r\\n]*/s"),
                    "rd /s removes a whole directory tree",
                    "List the contents first to confirm what will be lost."),
            new Rule("cmd.del.drive", ShellKind.CMD, Severity.DANGER,
                    re("(^|[\\s&|])(del|erase)(\\s|$)[^\\r\\n]*\\s[a-z]:\\\\?(\\s|$)"),
                    "This deletes from the root of a drive",
                    "Never delete a drive root; narrow the path to a specific folder."),
            new Rule("cmd.bcdedit", ShellKind.CMD, Severity.DANGER,
                    re("(^|[\\s&|])bcdedit([\\s&|]|$)"),
                    "bcdedit changes the boot configuration",
                    "A bad edit can stop Windows from starting."),
            new Rule("cmd.cipher.wipe", ShellKind.CMD, Severity.DANGER,
                    re("(^|[\\s&|])cipher(\\s|$)[^\\r\\n]*/w"),
                    "cipher /w overwrites free space and makes recovery impossible",
                    "Only use it when you intend to destroy deleted data for good."),
            new Rule("cmd.shadow.delete", ShellKind.CMD, Severity.DANGER,
                    re("(vssadmin|wbadmin)(\\s|$)[^\\r\\n]*delete"),
                    "Deleting shadow copies destroys restore points and backups",
                    "This is a common ransomware technique; verify the source of the script."),
            new Rule("cmd.forkbomb", ShellKind.CMD, Severity.DANGER,
                    re("%0\\s*\\|\\s*%0"),
                    "This line recursively re-runs itself",
                    "It will exhaust the machine's resources."),
            new Rule("cmd.encoded", ShellKind.CMD, Severity.DANGER,
                    re("powershell[^\\r\\n]*-(enc|encodedcommand)\\b"),
                    "An encoded PowerShell payload hides what it does",
                    "Decode it and read it before running."),
            new Rule("cmd.system.redirect", ShellKind.CMD, Severity.DANGER,
                    re(">\\s*(%systemroot%|[a-z]:\\\\windows)"),
                    "Writing into the Windows directory can break the system",
                    "Redirect to a folder you own instead."),

            // --- privilege and configuration ---------------------------------
            new Rule("cmd.net.admin", ShellKind.CMD, Severity.DANGER,
                    re("net\\s+localgroup\\s+administrators[^\\r\\n]*/add"),
                    "This grants administrator rights to an account",
                    "Confirm the account name carefully."),
            new Rule("cmd.net.user.add", ShellKind.CMD, Severity.WARNING,
                    re("net\\s+user\\s+\\S+[^\\r\\n]*/add"),
                    "This creates a local user account",
                    "Remove the account again if it was only a test."),
            new Rule("cmd.reg.delete", ShellKind.CMD, Severity.WARNING,
                    re("reg\\s+delete"),
                    "Deleting registry keys can break applications",
                    "Export the key first with reg export."),
            new Rule("cmd.acl", ShellKind.CMD, Severity.WARNING,
                    re("(takeown|icacls)(\\s|$)"),
                    "This changes file ownership or permissions",
                    "Record the current permissions before changing them."),
            new Rule("cmd.sc.delete", ShellKind.CMD, Severity.WARNING,
                    re("sc(\\s|$)delete\\b"),
                    "This removes a Windows service",
                    "Note the service name and its start type first."),
            new Rule("cmd.wmic.delete", ShellKind.CMD, Severity.WARNING,
                    re("wmic[^\\r\\n]*delete"),
                    "This deletes objects through WMI",
                    "Check which objects match before running."),
            new Rule("cmd.shutdown", ShellKind.CMD, Severity.WARNING,
                    re("(^|[\\s&|])shutdown([\\s&|]|$)"),
                    "This shuts down or restarts the machine",
                    "Save your work first."),
            new Rule("cmd.taskkill", ShellKind.CMD, Severity.WARNING,
                    re("taskkill(\\s|$)[^\\r\\n]*/f"),
                    "This force-kills a process",
                    "Unsaved work in that process is lost."),
            new Rule("cmd.chkdsk", ShellKind.CMD, Severity.WARNING,
                    re("chkdsk(\\s|$)[^\\r\\n]*/[fr]"),
                    "chkdsk repairs the volume and may take a long time",
                    "Make sure nothing is writing to that volume."),
            new Rule("cmd.runas", ShellKind.CMD, Severity.WARNING,
                    re("(^|[\\s&|])runas([\\s&|]|$)"),
                    "This starts a process as another user",
                    "Only do this for an account you own."),
            new Rule("cmd.attrib", ShellKind.CMD, Severity.INFO,
                    re("attrib(\\s|$)[^\\r\\n]*-[rsh]"),
                    "This hides or clears file attributes",
                    "Remember to restore them afterwards."),
            new Rule("cmd.sfc", ShellKind.CMD, Severity.INFO,
                    re("sfc(\\s|$)[^\\r\\n]*/scannow"),
                    "System File Checker scans and repairs system files",
                    "It can take several minutes."),

            // --- PowerShell ---------------------------------------------------
            new Rule("ps.remove.recurse.force", ShellKind.POWERSHELL, Severity.DANGER,
                    re("remove-item[^\\r\\n]*(?=[^\\r\\n]*(-recurse|-r\\b))(?=[^\\r\\n]*(-force|-fo\\b))"),
                    "Remove-Item -Recurse -Force deletes without any confirmation",
                    "Drop -Force so the shell asks before deleting, or use the Recycle Bin."),
            new Rule("ps.remove.drive", ShellKind.POWERSHELL, Severity.DANGER,
                    re("remove-item[^\\r\\n]*['\"]?[a-z]:\\\\?['\"]?[^\\r\\n]*(-recurse|-r\\b)"),
                    "This deletes from the root of a drive",
                    "Narrow the path to a specific folder."),
            new Rule("ps.invoke.expression", ShellKind.POWERSHELL, Severity.DANGER,
                    re("(invoke-expression|\\biex\\b)"),
                    "Invoke-Expression runs a string as code",
                    "It is the usual way downloaded payloads are executed."),
            new Rule("ps.download.execute", ShellKind.POWERSHELL, Severity.DANGER,
                    re("(invoke-webrequest|invoke-restmethod|downloadstring)[^\\r\\n]*\\|[^\\r\\n]*(iex|invoke-expression)"),
                    "This downloads code and runs it immediately",
                    "Download the file, read it, then run it deliberately."),
            new Rule("ps.format.volume", ShellKind.POWERSHELL, Severity.DANGER,
                    re("(format-volume|clear-disk|initialize-disk|new-partition)"),
                    "This repartitions or formats a disk",
                    "Confirm the disk number with Get-Disk first."),
            new Rule("ps.remove.item", ShellKind.POWERSHELL, Severity.WARNING,
                    re("remove-item"),
                    "Remove-Item deletes files or registry keys",
                    "Add -WhatIf to preview exactly what would be removed."),
            new Rule("ps.stop.computer", ShellKind.POWERSHELL, Severity.WARNING,
                    re("(stop-computer|restart-computer)"),
                    "This shuts down or restarts the machine",
                    "Save your work first."),
            new Rule("ps.execution.policy", ShellKind.POWERSHELL, Severity.WARNING,
                    re("set-executionpolicy"),
                    "This changes which scripts the machine will run",
                    "Prefer -Scope Process so the change is not permanent."),
            new Rule("ps.new.user", ShellKind.POWERSHELL, Severity.WARNING,
                    re("(new-localuser|add-localgroupmember)"),
                    "This creates an account or grants it group membership",
                    "Confirm the account name and the group."),
            new Rule("ps.registry.write", ShellKind.POWERSHELL, Severity.WARNING,
                    re("(set-itemproperty|remove-itemproperty|new-itemproperty)[^\\r\\n]*hklm:"),
                    "This writes to the machine-wide registry hive",
                    "Back up the key first with reg export."),
            new Rule("ps.plaintext.secret", ShellKind.POWERSHELL, Severity.WARNING,
                    re("convertto-securestring[^\\r\\n]*-asplaintext"),
                    "A password is stored in the script as plain text",
                    "Read it from Get-Credential or a protected secret store."),
            new Rule("ps.disable.restore", ShellKind.POWERSHELL, Severity.WARNING,
                    re("(disable-computerrestore|remove-computer)"),
                    "This removes restore points or the machine account",
                    "Make sure you have another way back."),
            new Rule("ps.unblock", ShellKind.POWERSHELL, Severity.INFO,
                    re("unblock-file"),
                    "This removes the downloaded-file mark",
                    "Only unblock files you trust."),
            new Rule("ps.download", ShellKind.POWERSHELL, Severity.INFO,
                    re("(invoke-webrequest|invoke-restmethod|start-bitstransfer)"),
                    "This downloads content from the network",
                    "Check the URL before running it."),
            new Rule("ps.runas", ShellKind.POWERSHELL, Severity.INFO,
                    re("start-process[^\\r\\n]*-verb\\s+runas"),
                    "This starts an elevated process and will show a UAC prompt",
                    "The new process runs with administrator rights."),

            // --- applies to both ---------------------------------------------
            new Rule("any.rm.rf.root", null, Severity.DANGER,
                    re("rm\\s+-[a-z]*r[a-z]*f?\\s+/(\\s|$)"),
                    "rm -rf / deletes the entire file system",
                    "There is no undo; make sure this is really what you meant."));

    private DangerAnalyzer() {
    }

    /**
     * Analyses a script.
     *
     * @param kind   interpreter whose rules apply
     * @param script the whole editor contents
     * @return findings ordered by position, possibly empty
     */
    public static List<Finding> analyze(ShellKind kind, String script) {
        List<Finding> findings = new ArrayList<>();
        if (script == null || script.isBlank()) {
            return findings;
        }
        for (ScriptParser.Command command : ScriptParser.parse(kind, script)) {
            if (!command.executable()) {
                continue;
            }
            for (Rule rule : RULES) {
                if (rule.kind() != null && rule.kind() != kind) {
                    continue;
                }
                Matcher matcher = rule.pattern().matcher(command.text());
                if (!matcher.find()) {
                    continue;
                }
                findings.add(new Finding(rule.severity(), rule.id(), rule.message(), rule.advice(),
                        command.startOffset(), command.endOffset(), command.lineIndex(),
                        snippet(command.text())));
                break; // one finding per command keeps the list readable
            }
        }
        return findings;
    }

    /**
     * Analyses only the highest severity present.
     *
     * @return {@link Severity#DANGER} when any dangerous command was found, the
     *         next severity down when only milder ones were, otherwise empty
     */
    public static java.util.Optional<Severity> worstSeverity(ShellKind kind, String script) {
        Severity worst = null;
        for (Finding finding : analyze(kind, script)) {
            if (worst == null || finding.severity().ordinal() > worst.ordinal()) {
                worst = finding.severity();
            }
        }
        return java.util.Optional.ofNullable(worst);
    }

    /** @return only the findings that warrant a confirmation prompt. */
    public static List<Finding> dangers(ShellKind kind, String script) {
        List<Finding> result = new ArrayList<>();
        for (Finding finding : analyze(kind, script)) {
            if (finding.severity() == Severity.DANGER) {
                result.add(finding);
            }
        }
        return result;
    }

    private static String snippet(String text) {
        String oneLine = text.replace('\n', ' ').replace('\r', ' ').strip();
        return oneLine.length() <= 80 ? oneLine : oneLine.substring(0, 77) + "\u2026";
    }
}
