package com.dogepopcat.cpgui.completion;

import com.dogepopcat.cpgui.shell.ShellKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Built-in knowledge about what can be typed into each interpreter.
 *
 * <p>The catalogue is compiled into the application rather than read from
 * {@code Get-Command} or {@code help}, so completion works instantly, offline
 * and without spawning a process on every keystroke.</p>
 */
public final class CommandCatalog {

    private static final String[] CMD_COMMANDS = {
            "assoc|Displays or modifies file extension associations",
            "attrib|Displays or changes file attributes",
            "bcdedit|Edits the boot configuration data store",
            "call|Calls one batch program from another",
            "cd|Changes the current directory",
            "chcp|Displays or sets the active code page",
            "chdir|Changes the current directory",
            "chkdsk|Checks a disk and displays a status report",
            "chkntfs|Displays or modifies check-disk behaviour at boot",
            "cls|Clears the screen",
            "cmd|Starts a new instance of the command interpreter",
            "color|Sets the default console foreground and background colours",
            "compact|Displays or alters compression of files on NTFS",
            "convert|Converts FAT volumes to NTFS",
            "copy|Copies one or more files to another location",
            "date|Displays or sets the date",
            "del|Deletes one or more files",
            "dir|Displays a list of files and subdirectories",
            "diskpart|Manages disks, partitions and volumes",
            "doskey|Edits command lines and creates macros",
            "driverquery|Displays a list of installed device drivers",
            "echo|Displays messages, or turns command echoing on or off",
            "endlocal|Ends localisation of environment changes",
            "erase|Deletes one or more files",
            "exit|Quits the command interpreter",
            "fc|Compares two files and displays the differences",
            "find|Searches for a text string in a file",
            "findstr|Searches for strings in files using patterns",
            "for|Runs a command for each item in a set",
            "forfiles|Runs a command on each file in a folder",
            "format|Formats a disk for use with Windows",
            "fsutil|Performs file system tasks such as managing reparse points",
            "ftype|Displays or modifies file types used in file association",
            "getmac|Returns the MAC address of network adapters",
            "goto|Directs the command interpreter to a labelled line",
            "gpresult|Displays Group Policy settings for a target",
            "gpupdate|Refreshes Group Policy settings",
            "icacls|Displays or modifies discretionary access control lists",
            "if|Performs conditional processing in a batch file",
            "ipconfig|Displays IP configuration",
            "label|Creates, changes or deletes the volume label of a disk",
            "md|Creates a directory",
            "mkdir|Creates a directory",
            "mklink|Creates a symbolic link or hard link",
            "mode|Configures system devices",
            "more|Displays output one screen at a time",
            "move|Moves files and renames files and directories",
            "msiexec|Installs, modifies or removes Windows Installer packages",
            "net|Manages network resources, users, groups and services",
            "netsh|Displays or modifies network configuration",
            "openfiles|Displays or disconnects open files and folders",
            "path|Displays or sets a search path for executable files",
            "pause|Suspends processing of a batch file",
            "ping|Tests a network connection",
            "popd|Restores the previous directory saved by pushd",
            "powercfg|Controls power settings and sleep states",
            "print|Prints a text file",
            "prompt|Changes the command prompt",
            "pushd|Saves the current directory and changes to a new one",
            "rd|Removes a directory",
            "recover|Recovers readable information from a damaged disk",
            "reg|Manages registry keys and values",
            "regedit|Imports or exports registry settings",
            "ren|Renames a file or files",
            "rename|Renames a file or files",
            "replace|Replaces files in a destination directory",
            "rmdir|Removes a directory",
            "robocopy|Copies file trees with rich filtering and retry options",
            "route|Manipulates network routing tables",
            "runas|Runs a program as another user",
            "sc|Manages Windows services",
            "schtasks|Schedules commands to run at a chosen time",
            "set|Displays, sets or removes environment variables",
            "setlocal|Begins localisation of environment changes",
            "sfc|Scans and repairs protected system files",
            "shift|Shifts the position of replaceable parameters",
            "shutdown|Shuts down or restarts the computer",
            "sort|Sorts input",
            "start|Starts a separate window to run a program or command",
            "subst|Associates a path with a drive letter",
            "systeminfo|Displays machine and operating system configuration",
            "takeown|Restores ownership of a file to an administrator",
            "taskkill|Ends one or more tasks or processes",
            "tasklist|Displays the tasks running on the machine",
            "time|Displays or sets the system time",
            "title|Sets the window title for a session",
            "tracert|Traces the route to a destination",
            "tree|Graphically displays the directory structure",
            "type|Displays the contents of a text file",
            "ver|Displays the Windows version",
            "verify|Tells Windows whether to verify that files are written correctly",
            "vol|Displays a disk volume label and serial number",
            "where|Locates files matching a search pattern",
            "whoami|Displays the current user and group information",
            "wmic|Runs WMI queries from the command line",
            "xcopy|Copies files and directory trees"
    };

    /** Switches offered once a specific command is being typed: command|switch|help. */
    private static final String[] CMD_SWITCHES = {
            "dir|/a|Display files with the specified attributes",
            "dir|/b|Use bare format, no heading or summary",
            "dir|/o|List files in sorted order",
            "dir|/s|Recurse into subdirectories",
            "dir|/p|Pause after each screenful",
            "dir|/w|Wide list format",
            "dir|/t|Choose which timestamp to display",
            "copy|/y|Suppress the overwrite confirmation prompt",
            "copy|/v|Verify that new files are written correctly",
            "copy|/b|Treat the file as binary",
            "xcopy|/s|Copy directories and subdirectories",
            "xcopy|/e|Copy empty directories as well",
            "xcopy|/i|Assume the destination is a directory",
            "xcopy|/y|Suppress the overwrite confirmation prompt",
            "xcopy|/h|Copy hidden and system files",
            "robocopy|/mir|Mirror a directory tree",
            "robocopy|/e|Copy subdirectories including empty ones",
            "robocopy|/z|Restartable mode",
            "robocopy|/xo|Exclude older files",
            "del|/f|Force deletion of read-only files",
            "del|/s|Delete from all subdirectories",
            "del|/q|Quiet mode, no confirmation",
            "del|/p|Prompt before deleting each file",
            "rd|/s|Remove the directory and everything in it",
            "rd|/q|Quiet mode, no confirmation",
            "rmdir|/s|Remove the directory and everything in it",
            "rmdir|/q|Quiet mode, no confirmation",
            "findstr|/i|Case-insensitive search",
            "findstr|/s|Search the current directory and subdirectories",
            "findstr|/n|Print the line number of each match",
            "findstr|/v|Print lines that do not contain a match",
            "findstr|/r|Use search strings as regular expressions",
            "taskkill|/f|Forcefully terminate the process",
            "taskkill|/im|Match by image name",
            "taskkill|/pid|Match by process id",
            "taskkill|/t|Terminate the process and its children",
            "icacls|/grant|Grant permissions to a user",
            "icacls|/remove|Remove permissions from a user",
            "icacls|/t|Apply the change to all matching files",
            "takeown|/f|Target file or directory",
            "takeown|/r|Recurse into subdirectories",
            "sc|query|Query the status of a service",
            "sc|start|Start a service",
            "sc|stop|Stop a service",
            "sc|config|Change the configuration of a service",
            "sc|delete|Delete a service",
            "reg|query|Read keys and values",
            "reg|add|Add a key or value",
            "reg|delete|Delete a key or value",
            "reg|export|Export a registry subtree to a file",
            "reg|import|Import a registry file",
            "net|user|Manage user accounts",
            "net|localgroup|Manage local groups",
            "net|share|Manage shared folders",
            "net|start|Start a service",
            "net|stop|Stop a service",
            "net|use|Connect or disconnect a shared resource",
            "shutdown|/r|Restart the computer",
            "shutdown|/s|Shut down the computer",
            "shutdown|/t|Set the timeout before shutdown",
            "shutdown|/a|Abort a pending shutdown",
            "shutdown|/f|Force running applications to close",
            "chkdsk|/f|Fix errors on the disk",
            "chkdsk|/r|Locate bad sectors and recover readable information",
            "ping|/t|Ping until stopped",
            "ping|/n|Number of echo requests to send",
            "ping|/4|Force IPv4",
            "ping|/6|Force IPv6",
            "ipconfig|/all|Show full configuration for all adapters",
            "ipconfig|/flushdns|Flush the DNS resolver cache",
            "ipconfig|/release|Release the current DHCP lease",
            "ipconfig|/renew|Renew the DHCP lease",
            "mklink|/d|Create a directory symbolic link",
            "mklink|/h|Create a hard link",
            "mklink|/j|Create a directory junction",
            "schtasks|/create|Create a scheduled task",
            "schtasks|/query|List scheduled tasks",
            "schtasks|/delete|Delete a scheduled task",
            "schtasks|/run|Run a scheduled task immediately"
    };

    private static final String[] POWERSHELL_CMDLETS = {
            "Add-Content|Appends content to a file",
            "Add-LocalGroupMember|Adds a member to a local group",
            "Add-Member|Adds a member to a PowerShell object",
            "Add-Type|Adds a .NET type to a session",
            "Clear-Content|Removes the contents of a file without deleting it",
            "Clear-Disk|Wipes a disk and removes its partitioning information",
            "Clear-Host|Clears the console screen",
            "Clear-Variable|Removes the value of a variable",
            "Compare-Object|Compares two sets of objects",
            "Compress-Archive|Creates a compressed archive from files",
            "ConvertFrom-Csv|Converts CSV input into objects",
            "ConvertFrom-Json|Converts a JSON string into objects",
            "ConvertFrom-SecureString|Converts a secure string into an encrypted string",
            "Convert-Path|Converts a path into a provider path",
            "ConvertTo-Csv|Converts objects into CSV text",
            "ConvertTo-Html|Converts objects into HTML",
            "ConvertTo-Json|Converts objects into a JSON string",
            "ConvertTo-SecureString|Converts plain text into a secure string",
            "Copy-Item|Copies an item from one location to another",
            "Disable-ComputerRestore|Turns off System Restore on a drive",
            "Enable-PSRemoting|Configures the machine to receive remote commands",
            "Expand-Archive|Extracts files from a compressed archive",
            "Export-Clixml|Serialises objects into an XML file",
            "Export-Csv|Converts objects into CSV and writes them to a file",
            "ForEach-Object|Performs an operation on every object in the pipeline",
            "Format-List|Formats output as a list of properties",
            "Format-Table|Formats output as a table",
            "Format-Wide|Formats output as a wide table of a single property",
            "Get-Acl|Gets the security descriptor of a resource",
            "Get-Alias|Gets the aliases in the current session",
            "Get-ChildItem|Gets the items in a location",
            "Get-CimInstance|Gets CIM instances from a computer",
            "Get-Clipboard|Gets the contents of the clipboard",
            "Get-Command|Gets information about commands",
            "Get-ComputerInfo|Gets consolidated operating system information",
            "Get-Content|Gets the contents of a file",
            "Get-Credential|Prompts for a credential",
            "Get-Date|Gets the current date and time",
            "Get-Disk|Gets the disks visible to the machine",
            "Get-EventLog|Gets events from an event log",
            "Get-FileHash|Computes the hash of a file",
            "Get-Help|Displays help about a command",
            "Get-History|Gets the command history of the session",
            "Get-Item|Gets the item at a location",
            "Get-ItemProperty|Gets the properties of an item",
            "Get-Job|Gets the background jobs of the session",
            "Get-Location|Gets the current location",
            "Get-Member|Gets the members of an object",
            "Get-Module|Lists the modules in the session",
            "Get-Process|Gets the processes running on the machine",
            "Get-PSDrive|Gets the drives exposed by PowerShell providers",
            "Get-Random|Gets a random number or a random selection",
            "Get-Service|Gets the services on the machine",
            "Get-Variable|Gets the variables in the session",
            "Get-Volume|Gets the volumes on the machine",
            "Get-WmiObject|Gets WMI objects",
            "Group-Object|Groups objects by a shared property",
            "Import-Clixml|Imports a CLIXML file and creates objects",
            "Import-Csv|Converts CSV items into objects",
            "Import-Module|Adds a module to the session",
            "Invoke-CimMethod|Invokes a CIM method",
            "Invoke-Command|Runs a command locally or remotely",
            "Invoke-Expression|Runs a string as a command",
            "Invoke-Item|Performs the default action on an item",
            "Invoke-RestMethod|Sends an HTTP request and parses the response",
            "Invoke-WebRequest|Sends an HTTP request",
            "Join-Path|Combines a path and a child path",
            "Measure-Command|Measures how long a command takes",
            "Measure-Object|Measures the numeric properties of objects",
            "Move-Item|Moves an item",
            "New-Item|Creates a new item",
            "New-ItemProperty|Creates a property for an item",
            "New-LocalUser|Creates a local user account",
            "New-Object|Creates an instance of a .NET type",
            "New-PSDrive|Creates a temporary PowerShell drive",
            "New-Partition|Creates a partition on a disk",
            "New-Service|Creates a Windows service",
            "New-Variable|Creates a variable",
            "Out-File|Sends output to a file",
            "Out-GridView|Sends output to an interactive table",
            "Out-Host|Sends output to the console",
            "Out-Null|Discards output",
            "Out-String|Converts input into strings",
            "Pop-Location|Changes to the most recent saved location",
            "Push-Location|Saves the current location and changes to a new one",
            "Read-Host|Reads a line of input from the console",
            "Receive-Job|Gets the results of a background job",
            "Register-ObjectEvent|Subscribes to an event of a .NET object",
            "Remove-Item|Deletes an item",
            "Remove-ItemProperty|Deletes a property of an item",
            "Remove-Job|Deletes a background job",
            "Remove-Module|Removes a module from the session",
            "Remove-PSDrive|Deletes a temporary PowerShell drive",
            "Remove-Variable|Deletes a variable",
            "Rename-Item|Renames an item",
            "Resolve-Path|Resolves a wildcard path",
            "Restart-Computer|Restarts the operating system",
            "Restart-Service|Stops and then starts a service",
            "Select-Object|Selects properties or a number of objects",
            "Select-String|Finds text in strings and files",
            "Set-Acl|Applies a security descriptor to a resource",
            "Set-Alias|Creates or changes an alias",
            "Set-Clipboard|Sets the contents of the clipboard",
            "Set-Content|Writes content to a file",
            "Set-Date|Changes the system date and time",
            "Set-ExecutionPolicy|Changes the script execution policy",
            "Set-Item|Changes the value of an item",
            "Set-ItemProperty|Changes the value of a property",
            "Set-Location|Changes the current location",
            "Set-Service|Changes the properties of a service",
            "Set-Variable|Changes the value of a variable",
            "Sort-Object|Sorts objects by property values",
            "Split-Path|Returns part of a path",
            "Start-BitsTransfer|Transfers files in the background",
            "Start-Job|Starts a background job",
            "Start-Process|Starts a process on the local machine",
            "Start-Service|Starts a stopped service",
            "Start-Sleep|Suspends activity for a period",
            "Start-Transcript|Records a session to a file",
            "Stop-Computer|Shuts down the computer",
            "Stop-Job|Stops a background job",
            "Stop-Process|Stops a running process",
            "Stop-Service|Stops a running service",
            "Tee-Object|Saves output and passes it down the pipeline",
            "Test-Connection|Sends ICMP echo requests to a host",
            "Test-NetConnection|Diagnoses network connectivity",
            "Test-Path|Determines whether a path exists",
            "Unblock-File|Removes the downloaded-file mark",
            "Unregister-Event|Cancels an event subscription",
            "Wait-Job|Waits for a background job to finish",
            "Wait-Process|Waits for a process to stop",
            "Where-Object|Filters objects by a condition",
            "Write-Debug|Writes a message to the debug stream",
            "Write-Error|Writes a message to the error stream",
            "Write-Host|Writes output directly to the console",
            "Write-Output|Sends objects down the pipeline",
            "Write-Progress|Displays a progress bar",
            "Write-Verbose|Writes a message to the verbose stream",
            "Write-Warning|Writes a warning message"
    };

    private static final String[] POWERSHELL_KEYWORDS = {
            "if|Runs a block when a condition is true",
            "else|Runs a block when the previous condition was false",
            "elseif|Tests another condition when the previous one failed",
            "switch|Selects a block based on matching values",
            "foreach|Iterates over a collection",
            "for|Counts through a loop",
            "while|Loops while a condition is true",
            "do|Loops at least once",
            "until|Loops until a condition becomes true",
            "function|Declares a function",
            "filter|Declares a pipeline filter",
            "param|Declares the parameters of a script or function",
            "return|Returns from a function or script",
            "break|Exits the current loop or switch",
            "continue|Skips to the next iteration",
            "try|Begins a block whose errors can be caught",
            "catch|Handles an error raised in a try block",
            "finally|Runs after try and catch regardless of the outcome",
            "throw|Raises a terminating error",
            "trap|Declares an error handler for a scope",
            "class|Declares a PowerShell class",
            "enum|Declares an enumeration",
            "using|Imports a namespace or module",
            "in|Used by foreach to name the iteration variable"
    };

    private static final String[] POWERSHELL_COMMON_PARAMETERS = {
            "-Confirm|Prompts for confirmation before the command runs",
            "-Debug|Shows programmer level debugging messages",
            "-ErrorAction|Chooses what happens when a non-terminating error occurs",
            "-ErrorVariable|Stores error records in a variable",
            "-InformationAction|Chooses how information messages are handled",
            "-OutBuffer|Number of objects to collect before passing them on",
            "-OutVariable|Stores output objects in a variable",
            "-PipelineVariable|Stores the current pipeline object in a variable",
            "-Verbose|Shows detailed activity messages",
            "-WarningAction|Chooses how warning messages are handled",
            "-WarningVariable|Stores warning messages in a variable",
            "-WhatIf|Shows what would happen without doing it"
    };

    private static final List<CompletionItem> CMD_ITEMS = build(CMD_COMMANDS, true);
    private static final List<CompletionItem> POWERSHELL_ITEMS =
            build(POWERSHELL_CMDLETS, true);
    private static final List<CompletionItem> POWERSHELL_KEYWORD_ITEMS =
            build(POWERSHELL_KEYWORDS, false);
    private static final List<CompletionItem> POWERSHELL_PARAMETER_ITEMS =
            parameters(POWERSHELL_COMMON_PARAMETERS);

    /** Switches keyed by the lower case command they belong to. */
    private static final Map<String, List<CompletionItem>> CMD_SWITCH_ITEMS = switches();

    private CommandCatalog() {
    }

    private static List<CompletionItem> build(String[] entries, boolean command) {
        List<CompletionItem> items = new ArrayList<>(entries.length);
        for (String entry : entries) {
            int bar = entry.indexOf('|');
            String name = entry.substring(0, bar);
            String description = entry.substring(bar + 1);
            items.add(command
                    ? CompletionItem.command(name, description)
                    : CompletionItem.keyword(name, description));
        }
        return List.copyOf(items);
    }

    private static List<CompletionItem> parameters(String[] entries) {
        List<CompletionItem> items = new ArrayList<>(entries.length);
        for (String entry : entries) {
            int bar = entry.indexOf('|');
            items.add(CompletionItem.parameter(entry.substring(0, bar),
                    entry.substring(bar + 1)));
        }
        return List.copyOf(items);
    }

    private static Map<String, List<CompletionItem>> switches() {
        Map<String, List<CompletionItem>> map = new HashMap<>();
        for (String entry : CMD_SWITCHES) {
            String[] parts = entry.split("\\|", 3);
            map.computeIfAbsent(parts[0].toLowerCase(Locale.ROOT), key -> new ArrayList<>())
                    .add(CompletionItem.parameter(parts[1], parts[2]));
        }
        return map;
    }

    /** @return every command style completion available for an interpreter. */
    public static List<CompletionItem> commands(ShellKind kind) {
        if (kind == ShellKind.CMD) {
            return CMD_ITEMS;
        }
        List<CompletionItem> combined = new ArrayList<>(
                POWERSHELL_ITEMS.size() + POWERSHELL_KEYWORD_ITEMS.size());
        combined.addAll(POWERSHELL_ITEMS);
        combined.addAll(POWERSHELL_KEYWORD_ITEMS);
        return combined;
    }

    /** @return the parameters offered after a cmdlet name. */
    public static List<CompletionItem> commonParameters(ShellKind kind) {
        return kind == ShellKind.POWERSHELL ? POWERSHELL_PARAMETER_ITEMS : List.of();
    }

    /**
     * @param command the command currently being typed
     * @return the switches that command understands, possibly empty
     */
    public static List<CompletionItem> switchesFor(ShellKind kind, String command) {
        if (kind != ShellKind.CMD || command == null) {
            return List.of();
        }
        List<CompletionItem> items = CMD_SWITCH_ITEMS.get(command.toLowerCase(Locale.ROOT));
        return items == null ? List.of() : items;
    }
}
