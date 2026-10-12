package com.dogepopcat.cpgui.ui;

import com.dogepopcat.cpgui.AppInfo;
import com.dogepopcat.cpgui.analysis.ScriptParser;
import com.dogepopcat.cpgui.shell.SessionRegistry;
import com.dogepopcat.cpgui.shell.ShellKind;
import com.dogepopcat.cpgui.shell.ShellSession;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * The application main window.
 *
 * <p>Layout: menu bar, tool bar, a vertically split work area (editor tabs on
 * top, console below) and a status bar.</p>
 */
public class MainFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private final EditorTabs editorTabs = new EditorTabs();
    private final ConsolePane console = new ConsolePane();
    private final StatusBar statusBar = new StatusBar();
    private final JTabbedPane bottomTabs = new JTabbedPane();
    private final ProblemsPanel problems = new ProblemsPanel();
    private final JSplitPane splitPane;
    private final SessionRegistry sessions = new SessionRegistry();

    private File workspace = new File(System.getProperty("user.dir"));
    private AppToolBar toolBar;

    // --- actions -----------------------------------------------------------

    private final Action newTabAction = action("New Tab", "Create a new script tab",
            KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK),
            e -> newTab());

    private final Action closeTabAction = action("Close Tab", "Close the active script tab",
            KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK),
            e -> closeTab(editorTabs.getSelectedIndex()));

    private final Action exitAction = action("Exit", "Quit the application", null,
            e -> dispatchClose());

    private final Action undoAction = action("Undo", "Undo the last edit",
            KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK),
            e -> withEditor(tab -> trigger(tab, "cpgui.undo")));

    private final Action redoAction = action("Redo", "Redo the last undone edit",
            KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK),
            e -> withEditor(tab -> trigger(tab, "cpgui.redo")));

    private final Action cutAction = action("Cut", "Cut the selection to the clipboard",
            KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK),
            e -> withEditor(tab -> tab.textPane().cut()));

    private final Action copyAction = action("Copy", "Copy the selection to the clipboard",
            KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK),
            e -> withEditor(tab -> tab.textPane().copy()));

    private final Action pasteAction = action("Paste", "Paste the clipboard into the editor",
            KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK),
            e -> withEditor(tab -> tab.textPane().paste()));

    private final Action selectAllAction = action("Select All", "Select the whole script",
            KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK),
            e -> withEditor(tab -> tab.textPane().selectAll()));

    private final Action clearOutputAction = action("Clear Output", "Remove all console output",
            null, e -> console.clear());

    private final Action largerFontAction = action("Larger Editor Font", null,
            KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK),
            e -> zoomEditor(1));

    private final Action smallerFontAction = action("Smaller Editor Font", null,
            KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK),
            e -> zoomEditor(-1));

    private final Action runAction = action("Run",
            "Run the selection, or the whole script when nothing is selected (Ctrl+Enter)",
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK),
            e -> runActiveScript());

    private final Action runAllAction = action("Run All",
            "Run every command in the active tab (Ctrl+Shift+Enter)",
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,
                    InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
            e -> runWholeScript());

    private final Action runLineAction = action("Run Current Line",
            "Run the command the caret is in (Ctrl+L)",
            KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK),
            e -> runCurrentLine());

    private final Action stopAction = action("Stop", "Stop the active tab's interpreter",
            KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0),
            e -> stopActiveSession());

    private final Action workspaceAction = action("Workspace\u2026",
            "Choose the working directory (the graphical cd command)",
            KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK),
            e -> chooseWorkspace());

    private final Action aboutAction = action("About", null, null, e -> showAbout());

    public MainFrame() {
        super(AppInfo.title());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(760, 500));

        editorTabs.setSelectionListener(index -> activeTabChanged());
        editorTabs.setTabCloseRequestHandler(this::closeTab);
        problems.setLineNavigator(this::goToLine);
        bottomTabs.addTab("Problems", problems);
        bottomTabs.addTab("Output", console);
        bottomTabs.setBorder(BorderFactory.createEmptyBorder());

        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorTabs, bottomTabs);
        splitPane.setResizeWeight(0.68);
        splitPane.setDividerSize(6);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setContinuousLayout(true);

        toolBar = new AppToolBar(this);

        setJMenuBar(createMenuBar());
        add(toolBar, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        statusBar.setWorkspace(workspace);
        statusBar.setShell(ShellKind.CMD);
        detectElevationAsync();

        setIconImages(java.util.List.of(
                Icons.appIcon(16), Icons.appIcon(32), Icons.appIcon(64), Icons.appIcon(256)));

        setSize(1120, 740);
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispatchClose();
            }
        });

        createTab(ShellKind.CMD);
        greet();
    }

    /** Writes the welcome banner into the console. */
    private void greet() {
        console.appendLine(AppInfo.title(), Theme.CONSOLE_SYSTEM);
        console.appendInfo("Write commands above, then press Ctrl+Enter or click Run."
                + System.lineSeparator());
        console.appendInfo("Each tab owns a persistent interpreter session, so cd and variables stick."
                + System.lineSeparator());
        console.appendInfo(System.lineSeparator());
    }

    // --- action plumbing ----------------------------------------------------

    /** Builds a named action with an optional keyboard shortcut. */
    private Action action(String name, String tooltip, KeyStroke accelerator,
                          Consumer<ActionEvent> body) {
        Action action = new AbstractAction(name) {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                body.accept(e);
            }
        };
        if (tooltip != null) {
            action.putValue(Action.SHORT_DESCRIPTION, tooltip);
        }
        if (accelerator != null) {
            action.putValue(Action.ACCELERATOR_KEY, accelerator);
        }
        return action;
    }

    private void trigger(EditorTab tab, String actionKey) {
        Action action = tab.textPane().getActionMap().get(actionKey);
        if (action != null) {
            action.actionPerformed(new ActionEvent(tab.textPane(),
                    ActionEvent.ACTION_PERFORMED, actionKey));
        }
    }

    private void withEditor(Consumer<EditorTab> body) {
        EditorTab tab = editorTabs.current();
        if (tab != null) {
            body.accept(tab);
        }
    }

    private void showAbout() {
        JPanel panel = new JPanel(new BorderLayout(12, 0));
        panel.add(new JLabel(new javax.swing.ImageIcon(Icons.appIcon(64))), BorderLayout.WEST);
        String html = "<html><b>" + AppInfo.NAME + "</b> " + AppInfo.VERSION
                + "<br>A graphical front end for cmd.exe and powershell.exe."
                + "<br><br>Java " + System.getProperty("java.version")
                + " &middot; " + System.getProperty("os.name")
                + "<br>Console code page: " + com.dogepopcat.cpgui.shell.ConsoleCharset.get().name()
                + "<br>Licensed under the MIT license.</html>";
        panel.add(new JLabel(html), BorderLayout.CENTER);
        JOptionPane.showMessageDialog(this, panel, "About " + AppInfo.NAME,
                JOptionPane.INFORMATION_MESSAGE);
    }

    // --- menu ---------------------------------------------------------------

    private JMenuBar createMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        file.setMnemonic('F');
        file.add(item(newTabAction));
        file.add(item(closeTabAction));
        file.addSeparator();
        file.add(item(workspaceAction));
        file.addSeparator();
        file.add(item(exitAction));
        bar.add(file);

        JMenu edit = new JMenu("Edit");
        edit.setMnemonic('E');
        edit.add(item(undoAction));
        edit.add(item(redoAction));
        edit.addSeparator();
        edit.add(item(cutAction));
        edit.add(item(copyAction));
        edit.add(item(pasteAction));
        edit.addSeparator();
        edit.add(item(selectAllAction));
        bar.add(edit);

        JMenu run = new JMenu("Run");
        run.setMnemonic('R');
        run.add(item(runAction));
        run.add(item(runAllAction));
        run.add(item(runLineAction));
        run.addSeparator();
        run.add(item(stopAction));
        bar.add(run);

        JMenu view = new JMenu("View");
        view.setMnemonic('V');
        view.add(item(clearOutputAction));
        view.addSeparator();
        view.add(item(largerFontAction));
        view.add(item(smallerFontAction));
        bar.add(view);

        JMenu help = new JMenu("Help");
        help.setMnemonic('H');
        help.add(item(aboutAction));
        bar.add(help);

        return bar;
    }

    private JMenuItem item(Action action) {
        JMenuItem menuItem = new JMenuItem(action);
        Object accelerator = action.getValue(Action.ACCELERATOR_KEY);
        if (accelerator instanceof KeyStroke keyStroke) {
            menuItem.setAccelerator(keyStroke);
        }
        return menuItem;
    }

    // --- tabs ---------------------------------------------------------------

    /** Opens a new editor tab targeting the given interpreter. */
    public EditorTab newTab() {
        EditorTab tab = createTab(ShellKind.CMD);
        statusBar.setMessage("New tab created");
        return tab;
    }

    private EditorTab createTab(ShellKind shellKind) {
        EditorTab tab = editorTabs.newTab(shellKind);
        tab.textPane().addCaretListener(e -> {
            if (tab == editorTabs.current()) {
                int[] pos = tab.caretLineColumn();
                statusBar.setCaret(pos[0], pos[1]);
            }
        });
        tab.diagnostics().setListener(findings -> {
            if (tab == editorTabs.current()) {
                problems.setFindings(findings);
            }
        });
        SwingUtilities.invokeLater(tab.textPane()::requestFocusInWindow);
        return tab;
    }

    /** Moves the caret of the active tab to a zero based line. */
    public void goToLine(int line) {
        EditorTab tab = editorTabs.current();
        if (tab == null) {
            return;
        }
        tab.selectLine(line);
        tab.textPane().requestFocusInWindow();
    }

    /** Closes the tab at {@code index}, releasing its interpreter session. */
    public void closeTab(int index) {
        if (index < 0 || index >= editorTabs.tabCount()) {
            return;
        }
        EditorTab removed = editorTabs.closeTab(index);
        if (removed != null) {
            sessions.release(removed);
        }
        if (editorTabs.tabCount() == 0) {
            createTab(ShellKind.CMD);
        }
        statusBar.setMessage("Tab closed");
    }

    private void activeTabChanged() {
        EditorTab tab = editorTabs.current();
        if (tab == null) {
            return;
        }
        statusBar.setShell(tab.shellKind());
        toolBar.setInterpreter(tab.shellKind());
        problems.setFindings(tab.diagnostics().findings());
        int[] pos = tab.caretLineColumn();
        statusBar.setCaret(pos[0], pos[1]);
        SwingUtilities.invokeLater(tab.textPane()::requestFocusInWindow);
    }

    /** Points the active tab at a different interpreter. */
    public void setActiveShellKind(ShellKind kind) {
        EditorTab tab = editorTabs.current();
        if (tab == null || kind == null) {
            return;
        }
        if (tab.shellKind() == kind) {
            return;
        }
        tab.setShellKind(kind);
        // The running interpreter cannot change, so start a fresh session.
        sessions.release(tab);
        statusBar.setShell(kind);
        statusBar.setMessage("Active tab now targets " + kind.description());
    }

    private void zoomEditor(int delta) {
        withEditor(tab -> {
            java.awt.Font font = tab.textPane().getFont();
            int size = Math.max(9, Math.min(28, font.getSize() + delta));
            tab.textPane().setFont(font.deriveFont((float) size));
            statusBar.setMessage("Editor font " + size + "pt");
        });
    }

    // --- execution ----------------------------------------------------------

    /**
     * Runs the active tab: the selection when there is one, otherwise the whole
     * script. This is what the Run button and Ctrl+Enter do.
     */
    public void runActiveScript() {
        EditorTab tab = editorTabs.current();
        if (tab == null) {
            return;
        }
        String selected = tab.textPane().getSelectedText();
        if (selected != null && !selected.isBlank()) {
            execute(tab, selected);
        } else {
            runWholeScript();
        }
    }

    /** Runs every command in the active tab, ignoring any selection. */
    public void runWholeScript() {
        EditorTab tab = editorTabs.current();
        if (tab == null) {
            return;
        }
        execute(tab, tab.getText());
    }

    /**
     * Runs the single command the caret sits in.
     *
     * <p>Line continuations are taken into account, so pressing the shortcut
     * inside a multi-line block submits the whole block.</p>
     */
    public void runCurrentLine() {
        EditorTab tab = editorTabs.current();
        if (tab == null) {
            return;
        }
        int caretLine = tab.caretLine();
        for (ScriptParser.Command command : ScriptParser.parse(tab.shellKind(), tab.getText())) {
            int first = command.lineIndex();
            int last = first + countNewlines(command.text());
            if (caretLine >= first && caretLine <= last) {
                if (!command.executable()) {
                    statusBar.setMessage("This line is a comment or blank");
                    return;
                }
                execute(tab, command.text());
                return;
            }
        }
        statusBar.setMessage("Nothing to run on this line");
    }

    private static int countNewlines(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    /** Sends a script to the active tab's interpreter. */
    private void execute(EditorTab tab, String script) {
        if (script == null || script.isBlank()) {
            statusBar.setMessage("Nothing to run");
            return;
        }

        ShellSession session;
        try {
            session = sessions.acquire(tab, tab.shellKind(), workspace,
                    new ConsoleSessionListener(tab));
        } catch (IOException e) {
            console.appendError("Failed to start " + tab.shellKind().executable()
                    + ": " + e.getMessage() + System.lineSeparator());
            statusBar.setMessage("Could not start " + tab.shellKind().displayName());
            return;
        }

        if (session.isBusy()) {
            statusBar.setMessage("A command is already running in this tab");
            return;
        }

        int commandCount = ScriptParser.executableCount(tab.shellKind(), script);
        echoScript(tab, script);
        try {
            session.run(script);
            statusBar.setMessage("Running " + commandCount + " command"
                    + (commandCount == 1 ? "" : "s")
                    + " in " + tab.shellKind().displayName()
                    + " (pid " + session.pid() + ") \u2026");
        } catch (IllegalStateException e) {
            console.appendError(e.getMessage() + System.lineSeparator());
            statusBar.setMessage("Could not run the script");
        }
    }

    /** Stops the active tab's interpreter, discarding its state. */
    public void stopActiveSession() {
        EditorTab tab = editorTabs.current();
        if (tab == null) {
            return;
        }
        ShellSession session = sessions.peek(tab);
        if (session == null) {
            statusBar.setMessage("No running session in this tab");
            return;
        }
        sessions.release(tab);
        console.appendSystem("[session stopped]" + System.lineSeparator());
        statusBar.setMessage("Session stopped");
    }

    /**
     * Asks the user for a working directory and applies it everywhere: new
     * sessions start there and every live session is told to change into it.
     */
    public void chooseWorkspace() {
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser(workspace);
        chooser.setDialogTitle("Select workspace folder");
        chooser.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setApproveButtonText("Use this folder");
        if (chooser.showOpenDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
            statusBar.setMessage("Workspace unchanged");
            return;
        }
        File selected = chooser.getSelectedFile();
        if (selected == null || !selected.isDirectory()) {
            statusBar.setMessage("Workspace unchanged");
            return;
        }
        setWorkspace(selected);
    }

    /**
     * Changes the working directory and pushes the equivalent {@code cd} into
     * every idle session.
     *
     * @param directory the new working directory
     */
    public void setWorkspace(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return;
        }
        this.workspace = directory;
        statusBar.setWorkspace(directory);

        int updated = 0;
        for (EditorTab tab : editorTabs.tabs()) {
            ShellSession session = sessions.peek(tab);
            if (session == null || !session.isAlive() || session.isBusy()) {
                continue;
            }
            String command = changeDirectoryCommand(tab.shellKind(), directory);
            console.append(promptName(tab.shellKind()) + "> " + command + System.lineSeparator(),
                    Theme.CONSOLE_COMMAND);
            try {
                session.run(command);
                updated++;
            } catch (IllegalStateException e) {
                console.appendError(e.getMessage() + System.lineSeparator());
            }
        }
        statusBar.setMessage(updated == 0
                ? "Workspace set to " + directory.getAbsolutePath()
                : "Workspace set and applied to " + updated + " session(s)");
    }

    private String promptName(ShellKind kind) {
        return kind == ShellKind.CMD ? "CMD" : "PS";
    }

    /** Builds the interpreter specific command that changes directory. */
    private String changeDirectoryCommand(ShellKind kind, File directory) {
        if (kind == ShellKind.CMD) {
            return "cd /d \"" + directory.getAbsolutePath() + "\"";
        }
        // Single quotes keep PowerShell from expanding $ and [] in file names.
        return "Set-Location -LiteralPath '"
                + directory.getAbsolutePath().replace("'", "''") + "'";
    }

    /** Writes the script to the console prefixed like an interactive prompt. */
    private void echoScript(EditorTab tab, String script) {
        String prompt = promptName(tab.shellKind()) + ">";
        StringBuilder text = new StringBuilder();
        for (String line : script.split("\\R", -1)) {
            if (!line.isBlank()) {
                text.append(prompt).append(' ').append(line.strip()).append(System.lineSeparator());
            }
        }
        console.append(text.toString(), Theme.CONSOLE_COMMAND);
    }

    private void dispatchClose() {
        sessions.closeAll();
        dispose();
        System.exit(0);
    }

    /**
     * Determines whether this process is elevated and updates the status bar.
     *
     * <p>The mandatory integrity level is read from {@code whoami /groups}; the
     * high integrity SID is only present when the process runs as
     * administrator. The query runs off the event dispatch thread.</p>
     */
    private void detectElevationAsync() {
        Thread thread = new Thread(() -> {
            boolean elevated = false;
            Process process = null;
            try {
                process = new ProcessBuilder("whoami.exe", "/groups")
                        .redirectErrorStream(true).start();
                byte[] bytes;
                try (java.io.InputStream in = process.getInputStream()) {
                    bytes = in.readAllBytes();
                }
                process.waitFor();
                String text = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
                elevated = text.contains("S-1-16-12288") || text.contains("S-1-16-16384");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // Leave the flag false when the query is unavailable.
            } finally {
                if (process != null) {
                    process.destroyForcibly();
                }
            }
            boolean result = elevated;
            SwingUtilities.invokeLater(() -> statusBar.setElevated(result));
        }, "cpgui-elevation");
        thread.setDaemon(true);
        thread.start();
    }

    // --- session events -----------------------------------------------------
    /** Routes a session's output into the shared console. */
    private final class ConsoleSessionListener implements ShellSession.Listener {

        private final EditorTab tab;

        ConsoleSessionListener(EditorTab tab) {
            this.tab = tab;
        }

        @Override
        public void onOutput(String text, boolean error) {
            console.append(text, error ? Theme.CONSOLE_STDERR : Theme.CONSOLE_FOREGROUND);
        }

        @Override
        public void onFinished(int exitCode, boolean success) {
            String summary = success
                    ? String.format("[%s finished, exit code %d]",
                            tab.shellKind().displayName(), exitCode)
                    : String.format("[%s reported a failure, exit code %d]",
                            tab.shellKind().displayName(), exitCode);
            console.append(summary + System.lineSeparator(),
                    success ? Theme.CONSOLE_INFO : Theme.ERROR);
            SwingUtilities.invokeLater(() -> {
                statusBar.setMessage(success
                        ? "Finished, exit code " + exitCode
                        : "Finished with an error, exit code " + exitCode);
                statusBar.setMessageColor(success ? null : Theme.ERROR);
            });
        }

        @Override
        public void onClosed(int exitCode) {
            console.appendSystem("[" + tab.shellKind().displayName()
                    + " session ended, exit code " + exitCode + "]" + System.lineSeparator());
        }

        @Override
        public void onFailure(String message) {
            console.appendError(message + System.lineSeparator());
        }
    }

    // --- accessors ----------------------------------------------------------

    /** @return the console shared by every session. */
    public ConsolePane console() {
        return console;
    }

    /** @return the tab strip holding open scripts. */
    public EditorTabs editorTabs() {
        return editorTabs;
    }

    /** @return the status bar at the bottom of the window. */
    public StatusBar statusBar() {
        return statusBar;
    }

    /** Selects a tab in the bottom pane by its title. */
    public void selectBottomTab(String title) {
        for (int i = 0; i < bottomTabs.getTabCount(); i++) {
            if (title.equals(bottomTabs.getTitleAt(i))) {
                bottomTabs.setSelectedIndex(i);
                return;
            }
        }
    }

    /** @return the split pane separating editor and console. */
    public JSplitPane splitPane() {
        return splitPane;
    }

    /** @return the registry owning every interpreter session. */
    public SessionRegistry sessions() {
        return sessions;
    }

    /** @return the directory new interpreter sessions start in. */
    public File workspace() {
        return workspace;
    }

    public Action newTabAction() {
        return newTabAction;
    }

    public Action runAction() {
        return runAction;
    }

    public Action stopAction() {
        return stopAction;
    }

    public Action workspaceAction() {
        return workspaceAction;
    }
}
