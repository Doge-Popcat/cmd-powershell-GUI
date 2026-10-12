package com.dogepopcat.cpgui.shell;

/**
 * The command interpreter a session is backed by.
 */
public enum ShellKind {

    /** The classic Windows command interpreter, {@code cmd.exe}. */
    CMD("CMD", "cmd.exe", "Command Prompt", "cmd"),

    /** Windows PowerShell 5.1, {@code powershell.exe}. */
    POWERSHELL("PowerShell", "powershell.exe", "Windows PowerShell", "ps1");

    private final String displayName;
    private final String executable;
    private final String description;
    private final String fileExtension;

    ShellKind(String displayName, String executable, String description, String fileExtension) {
        this.displayName = displayName;
        this.executable = executable;
        this.description = description;
        this.fileExtension = fileExtension;
    }

    /** Short label used in tabs, the tool bar and the status bar. */
    public String displayName() {
        return displayName;
    }

    /** Name of the interpreter executable as found on {@code PATH}. */
    public String executable() {
        return executable;
    }

    /** Longer human readable name for tool tips and the about box. */
    public String description() {
        return description;
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
        return displayName;
    }
}
