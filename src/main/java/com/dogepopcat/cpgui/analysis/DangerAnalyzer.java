package com.dogepopcat.cpgui.analysis;

import com.dogepopcat.cpgui.i18n.Msg;
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
     * @param message  description of the danger, resolved per language on use
     * @param advice   suggested alternative, resolved per language on use
     */
    private record Rule(String id, ShellKind kind, Severity severity, Pattern pattern,
                        Msg message, Msg advice) {
    }

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private static Pattern re(String expression) {
        return Pattern.compile(expression, FLAGS);
    }

    private static final List<Rule> RULES = List.of(
            // --- irreversible destruction ------------------------------------
            new Rule("cmd.format", ShellKind.CMD, Severity.DANGER,
                    re("(^|[\\s&|])format\\s+[a-z]:"),
                    Msg.RULE_CMD_FORMAT_MESSAGE,
                    Msg.RULE_CMD_FORMAT_ADVICE),
            new Rule("cmd.diskpart", ShellKind.CMD, Severity.DANGER,
                    re("(^|[\\s&|])diskpart([\\s&|]|$)"),
                    Msg.RULE_CMD_DISKPART_MESSAGE,
                    Msg.RULE_CMD_DISKPART_ADVICE),
            new Rule("cmd.del.force", ShellKind.CMD, Severity.DANGER,
                    re("(^|[\\s&|])(del|erase)(\\s|$)[^\\r\\n]*/[a-z]*[fs]"),
                    Msg.RULE_CMD_DEL_FORCE_MESSAGE,
                    Msg.RULE_CMD_DEL_FORCE_ADVICE),
            new Rule("cmd.rd.tree", ShellKind.CMD, Severity.DANGER,
                    re("(^|[\\s&|])(rd|rmdir)(\\s|$)[^\\r\\n]*/s"),
                    Msg.RULE_CMD_RD_TREE_MESSAGE,
                    Msg.RULE_CMD_RD_TREE_ADVICE),
            new Rule("cmd.del.drive", ShellKind.CMD, Severity.DANGER,
                    re("(^|[\\s&|])(del|erase)(\\s|$)[^\\r\\n]*\\s[a-z]:\\\\?(\\s|$)"),
                    Msg.RULE_CMD_DEL_DRIVE_MESSAGE,
                    Msg.RULE_CMD_DEL_DRIVE_ADVICE),
            new Rule("cmd.bcdedit", ShellKind.CMD, Severity.DANGER,
                    re("(^|[\\s&|])bcdedit([\\s&|]|$)"),
                    Msg.RULE_CMD_BCDEDIT_MESSAGE,
                    Msg.RULE_CMD_BCDEDIT_ADVICE),
            new Rule("cmd.cipher.wipe", ShellKind.CMD, Severity.DANGER,
                    re("(^|[\\s&|])cipher(\\s|$)[^\\r\\n]*/w"),
                    Msg.RULE_CMD_CIPHER_MESSAGE,
                    Msg.RULE_CMD_CIPHER_ADVICE),
            new Rule("cmd.shadow.delete", ShellKind.CMD, Severity.DANGER,
                    re("(vssadmin|wbadmin)(\\s|$)[^\\r\\n]*delete"),
                    Msg.RULE_CMD_SHADOW_MESSAGE,
                    Msg.RULE_CMD_SHADOW_ADVICE),
            new Rule("cmd.forkbomb", ShellKind.CMD, Severity.DANGER,
                    re("%0\\s*\\|\\s*%0"),
                    Msg.RULE_CMD_FORKBOMB_MESSAGE,
                    Msg.RULE_CMD_FORKBOMB_ADVICE),
            new Rule("cmd.encoded", ShellKind.CMD, Severity.DANGER,
                    re("powershell[^\\r\\n]*-(enc|encodedcommand)\\b"),
                    Msg.RULE_CMD_ENCODED_MESSAGE,
                    Msg.RULE_CMD_ENCODED_ADVICE),
            new Rule("cmd.system.redirect", ShellKind.CMD, Severity.DANGER,
                    re(">\\s*(%systemroot%|[a-z]:\\\\windows)"),
                    Msg.RULE_CMD_REDIRECT_MESSAGE,
                    Msg.RULE_CMD_REDIRECT_ADVICE),

            // --- privilege and configuration ---------------------------------
            new Rule("cmd.net.admin", ShellKind.CMD, Severity.DANGER,
                    re("net\\s+localgroup\\s+administrators[^\\r\\n]*/add"),
                    Msg.RULE_CMD_NET_ADMIN_MESSAGE,
                    Msg.RULE_CMD_NET_ADMIN_ADVICE),
            new Rule("cmd.net.user.add", ShellKind.CMD, Severity.WARNING,
                    re("net\\s+user\\s+\\S+[^\\r\\n]*/add"),
                    Msg.RULE_CMD_NET_USER_MESSAGE,
                    Msg.RULE_CMD_NET_USER_ADVICE),
            new Rule("cmd.reg.delete", ShellKind.CMD, Severity.WARNING,
                    re("reg\\s+delete"),
                    Msg.RULE_CMD_REG_DELETE_MESSAGE,
                    Msg.RULE_CMD_REG_DELETE_ADVICE),
            new Rule("cmd.acl", ShellKind.CMD, Severity.WARNING,
                    re("(takeown|icacls)(\\s|$)"),
                    Msg.RULE_CMD_ACL_MESSAGE,
                    Msg.RULE_CMD_ACL_ADVICE),
            new Rule("cmd.sc.delete", ShellKind.CMD, Severity.WARNING,
                    re("sc(\\s|$)delete\\b"),
                    Msg.RULE_CMD_SC_DELETE_MESSAGE,
                    Msg.RULE_CMD_SC_DELETE_ADVICE),
            new Rule("cmd.wmic.delete", ShellKind.CMD, Severity.WARNING,
                    re("wmic[^\\r\\n]*delete"),
                    Msg.RULE_CMD_WMIC_DELETE_MESSAGE,
                    Msg.RULE_CMD_WMIC_DELETE_ADVICE),
            new Rule("cmd.shutdown", ShellKind.CMD, Severity.WARNING,
                    re("(^|[\\s&|])shutdown([\\s&|]|$)"),
                    Msg.RULE_CMD_SHUTDOWN_MESSAGE,
                    Msg.RULE_CMD_SHUTDOWN_ADVICE),
            new Rule("cmd.taskkill", ShellKind.CMD, Severity.WARNING,
                    re("taskkill(\\s|$)[^\\r\\n]*/f"),
                    Msg.RULE_CMD_TASKKILL_MESSAGE,
                    Msg.RULE_CMD_TASKKILL_ADVICE),
            new Rule("cmd.chkdsk", ShellKind.CMD, Severity.WARNING,
                    re("chkdsk(\\s|$)[^\\r\\n]*/[fr]"),
                    Msg.RULE_CMD_CHKDSK_MESSAGE,
                    Msg.RULE_CMD_CHKDSK_ADVICE),
            new Rule("cmd.runas", ShellKind.CMD, Severity.WARNING,
                    re("(^|[\\s&|])runas([\\s&|]|$)"),
                    Msg.RULE_CMD_RUNAS_MESSAGE,
                    Msg.RULE_CMD_RUNAS_ADVICE),
            new Rule("cmd.attrib", ShellKind.CMD, Severity.INFO,
                    re("attrib(\\s|$)[^\\r\\n]*-[rsh]"),
                    Msg.RULE_CMD_ATTRIB_MESSAGE,
                    Msg.RULE_CMD_ATTRIB_ADVICE),
            new Rule("cmd.sfc", ShellKind.CMD, Severity.INFO,
                    re("sfc(\\s|$)[^\\r\\n]*/scannow"),
                    Msg.RULE_CMD_SFC_MESSAGE,
                    Msg.RULE_CMD_SFC_ADVICE),

            // --- PowerShell ---------------------------------------------------
            new Rule("ps.remove.recurse.force", ShellKind.POWERSHELL, Severity.DANGER,
                    re("remove-item[^\\r\\n]*(?=[^\\r\\n]*(-recurse|-r\\b))(?=[^\\r\\n]*(-force|-fo\\b))"),
                    Msg.RULE_PS_REMOVE_RECURSE_MESSAGE,
                    Msg.RULE_PS_REMOVE_RECURSE_ADVICE),
            new Rule("ps.remove.drive", ShellKind.POWERSHELL, Severity.DANGER,
                    re("remove-item[^\\r\\n]*['\"]?[a-z]:\\\\?['\"]?[^\\r\\n]*(-recurse|-r\\b)"),
                    Msg.RULE_PS_REMOVE_DRIVE_MESSAGE,
                    Msg.RULE_PS_REMOVE_DRIVE_ADVICE),
            new Rule("ps.invoke.expression", ShellKind.POWERSHELL, Severity.DANGER,
                    re("(invoke-expression|\\biex\\b)"),
                    Msg.RULE_PS_IEX_MESSAGE,
                    Msg.RULE_PS_IEX_ADVICE),
            new Rule("ps.download.execute", ShellKind.POWERSHELL, Severity.DANGER,
                    re("(invoke-webrequest|invoke-restmethod|downloadstring)[^\\r\\n]*\\|[^\\r\\n]*(iex|invoke-expression)"),
                    Msg.RULE_PS_DOWNLOAD_EXEC_MESSAGE,
                    Msg.RULE_PS_DOWNLOAD_EXEC_ADVICE),
            new Rule("ps.format.volume", ShellKind.POWERSHELL, Severity.DANGER,
                    re("(format-volume|clear-disk|initialize-disk|new-partition)"),
                    Msg.RULE_PS_FORMAT_MESSAGE,
                    Msg.RULE_PS_FORMAT_ADVICE),
            new Rule("ps.remove.item", ShellKind.POWERSHELL, Severity.WARNING,
                    re("remove-item"),
                    Msg.RULE_PS_REMOVE_ITEM_MESSAGE,
                    Msg.RULE_PS_REMOVE_ITEM_ADVICE),
            new Rule("ps.stop.computer", ShellKind.POWERSHELL, Severity.WARNING,
                    re("(stop-computer|restart-computer)"),
                    Msg.RULE_PS_STOP_COMPUTER_MESSAGE,
                    Msg.RULE_PS_STOP_COMPUTER_ADVICE),
            new Rule("ps.execution.policy", ShellKind.POWERSHELL, Severity.WARNING,
                    re("set-executionpolicy"),
                    Msg.RULE_PS_POLICY_MESSAGE,
                    Msg.RULE_PS_POLICY_ADVICE),
            new Rule("ps.new.user", ShellKind.POWERSHELL, Severity.WARNING,
                    re("(new-localuser|add-localgroupmember)"),
                    Msg.RULE_PS_NEW_USER_MESSAGE,
                    Msg.RULE_PS_NEW_USER_ADVICE),
            new Rule("ps.registry.write", ShellKind.POWERSHELL, Severity.WARNING,
                    re("(set-itemproperty|remove-itemproperty|new-itemproperty)[^\\r\\n]*hklm:"),
                    Msg.RULE_PS_REGISTRY_MESSAGE,
                    Msg.RULE_PS_REGISTRY_ADVICE),
            new Rule("ps.plaintext.secret", ShellKind.POWERSHELL, Severity.WARNING,
                    re("convertto-securestring[^\\r\\n]*-asplaintext"),
                    Msg.RULE_PS_SECRET_MESSAGE,
                    Msg.RULE_PS_SECRET_ADVICE),
            new Rule("ps.disable.restore", ShellKind.POWERSHELL, Severity.WARNING,
                    re("(disable-computerrestore|remove-computer)"),
                    Msg.RULE_PS_RESTORE_MESSAGE,
                    Msg.RULE_PS_RESTORE_ADVICE),
            new Rule("ps.unblock", ShellKind.POWERSHELL, Severity.INFO,
                    re("unblock-file"),
                    Msg.RULE_PS_UNBLOCK_MESSAGE,
                    Msg.RULE_PS_UNBLOCK_ADVICE),
            new Rule("ps.download", ShellKind.POWERSHELL, Severity.INFO,
                    re("(invoke-webrequest|invoke-restmethod|start-bitstransfer)"),
                    Msg.RULE_PS_DOWNLOAD_MESSAGE,
                    Msg.RULE_PS_DOWNLOAD_ADVICE),
            new Rule("ps.runas", ShellKind.POWERSHELL, Severity.INFO,
                    re("start-process[^\\r\\n]*-verb\\s+runas"),
                    Msg.RULE_PS_RUNAS_MESSAGE,
                    Msg.RULE_PS_RUNAS_ADVICE),

            // --- applies to both ---------------------------------------------
            new Rule("any.rm.rf.root", null, Severity.DANGER,
                    re("rm\\s+-[a-z]*r[a-z]*f?\\s+/(\\s|$)"),
                    Msg.RULE_ANY_RM_MESSAGE,
                    Msg.RULE_ANY_RM_ADVICE));

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
            Finding finding = firstMatchingRule(kind, command);
            if (finding == null) {
                finding = unknownCommandFinding(kind, command);
            }
            if (finding != null) {
                findings.add(finding);
            }
        }
        return findings;
    }

    /** Applies the danger rules, stopping at the first match per command. */
    private static Finding firstMatchingRule(ShellKind kind, ScriptParser.Command command) {
        for (Rule rule : RULES) {
            if (rule.kind() != null && rule.kind() != kind) {
                continue;
            }
            Matcher matcher = rule.pattern().matcher(command.text());
            if (!matcher.find()) {
                continue;
            }
            return new Finding(rule.severity(), rule.id(), rule.message().text(),
                    rule.advice().text(),
                    command.startOffset(), command.endOffset(), command.lineIndex(),
                    snippet(command.text()));
        }
        return null;
    }

    /**
     * Reports a command whose first word names nothing this machine can run.
     *
     * <p>The rules take precedence: a destructive command is worth flagging even
     * if its name is also unfamiliar.</p>
     */
    private static Finding unknownCommandFinding(ShellKind kind, ScriptParser.Command command) {
        String token = firstWord(command.text());
        if (token == null) {
            return null;
        }
        if (CommandResolver.isKnown(kind, token)) {
            return null;
        }
        return new Finding(Severity.WARNING, "unknown.command",
                Msg.RULE_UNKNOWN_COMMAND_MESSAGE.format(token),
                Msg.RULE_UNKNOWN_COMMAND_ADVICE.text(),
                command.startOffset(), command.endOffset(), command.lineIndex(),
                snippet(command.text()));
    }

    /**
     * @param text one command
     * @return its first word, or {@code null} when it does not look like a plain
     *         command name
     */
    private static String firstWord(String text) {
        String stripped = text.stripLeading();
        int end = 0;
        while (end < stripped.length() && !Character.isWhitespace(stripped.charAt(end))) {
            end++;
        }
        String token = stripped.substring(0, end);
        if (token.contains("=") || token.startsWith("@")) {
            // "set X=1" style assignments and "@echo" prefixes.
            token = token.startsWith("@") ? token.substring(1) : token;
        }
        if (token.isBlank() || token.contains("=")) {
            return null;
        }
        return token;
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

