package com.dogepopcat.cpgui.shell;

import com.dogepopcat.cpgui.i18n.Msg;

/**
 * The command interpreter a session is backed by.
 */
public enum ShellKind {

    /** The classic Windows command interpreter, {@code cmd.exe}. */
    CMD(Msg.SHELL_CMD, "cmd.exe", Msg.SHELL_CMD_DESCRIPTION, "cmd"),

    /** Windows PowerShell 5.1, {@code powershell.exe}. */
    POWERSHELL(Msg.SHELL_POWERSHELL, "powershell.exe", Msg.SHELL_POWERSHELL_DESCRIPTION, "ps1");

    private final Msg displayName;
    private final String executable;
    private final Msg description;
    private final String fileExtension;

    ShellKind(Msg displayName, String executable, Msg description, String fileExtension) {
        this.displayName = displayName;
        this.executable = executable;
        this.description = description;
        this.fileExtension = fileExtension;
    }

    /** Short label used in tabs, the tool bar and the status bar. */
    public String displayName() {
        return displayName.text();
    }

    /** Name of the interpreter executable as found on {@code PATH}. */
    public String executable() {
        return executable;
    }

    /** Longer human readable name for tool tips and the about box. */
    public String description() {
        return description.text();
    }

    /** Default file extension, without the dot, for scripts of this kind. */
    public String fileExtension() {
        return fileExtension;
    }

    /** @return the single line comment prefix understood by this shell. */
    public String lineComment() {
        return this == CMD ? "REM" : "#";
    }

    @Override
    public String toString() {
        return displayName();
    }
}
