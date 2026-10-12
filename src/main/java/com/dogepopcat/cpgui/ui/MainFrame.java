package com.dogepopcat.cpgui.ui;

import com.dogepopcat.cpgui.AppInfo;
import com.dogepopcat.cpgui.analysis.DangerAnalyzer;
import com.dogepopcat.cpgui.analysis.DangerAnalyzer.Finding;
import com.dogepopcat.cpgui.analysis.ScriptParser;
import com.dogepopcat.cpgui.io.ScriptFile;
import com.dogepopcat.cpgui.io.WorkspacePolicy;
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
import java.util.List;
import java.util.function.Consumer;

/**
 * The application main window.
 *
 * <p>Layout: menu bar, tool bar, a vertically split work area (editor tabs on
 * top, console below) and a status bar.</p>
 */
public class MainFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    /** Share of the window height given to the editor on first layout. */
    private static final double EDITOR_SHARE = 0.68;

    private final EditorTabs editorTabs = new EditorTabs();
    private final ConsolePane console = new ConsolePane();
    private final StatusBar statusBar = new StatusBar();
    private final JTabbedPane bottomTabs = new JTabbedPane();
    private final ProblemsPanel problems = new ProblemsPanel();
    private final JSplitPane splitPane;
    private final SessionRegistry sessions = new SessionRegistry();

    private File workspace = WorkspacePolicy.defaultWorkspace();
    private AppToolBar toolBar;
    private final LocationBar locationBar = new LocationBar();
    private volatile boolean elevated;
    private boolean dividerPlaced;

    // --- actions -----------------------------------------------------------

    private final Action newTabAction = action("New Tab", "Create a new script tab",
            KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK),
            e -> newTab());

    private final Action closeTabAction = action("Close Tab", "Close the active script tab",
            KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK),
            e -> closeTab(editorTabs.getSelectedIndex()));

    private final Action openAction = action("Open\u2026", "Open a .cmd, .bat or .ps1 script",
            KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK),
            e -> openScript());

    private final Action saveAction = action("Save", "Save the active script (Ctrl+S)",
            KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK),
            e -> saveActiveScript());

    private final Action saveAsAction = action("Save As\u2026", "Save the active script under a new name",
            KeyStroke.getKeyStroke(KeyEvent.VK_S,
                    InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
            e -> saveActiveScriptAs());

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

    private final Action adminAction = action("Administrator",
            "Restart the application with administrator rights",
            null, e -> requestAdministratorMode());

    private final Action aboutAction = action("About", null, null, e -> showAbout());

    public MainFrame() {
        this(null);
    }

    /**
     * @param requestedWorkspace directory this window should start in, or
     *                           {@code null} to choose a safe default
     */
    public MainFrame(File requestedWorkspace) {
        super(AppInfo.title());
        if (requestedWorkspace != null && requestedWorkspace.isDirectory()) {
            workspace = requestedWorkspace;
        }
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(760, 500));

        editorTabs.setSelectionListener(index -> activeTabChanged());
        editorTabs.setTabCloseRequestHandler(this::closeTab);
        problems.setLineNavigator(this::goToLine);
        bottomTabs.addTab("Problems", problems);
        bottomTabs.addTab("Output", console);
        bottomTabs.setBorder(BorderFactory.createEmptyBorder());

        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorTabs, bottomTabs);
        splitPane.setResizeWeight(EDITOR_SHARE);
        splitPane.setDividerSize(6);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setContinuousLayout(true);
        // A split pane places its divider from the components' preferred sizes,
        // and resizeWeight only applies to later resizes. An empty editor has a
        // tiny preferred height, so the divider has to be placed explicitly once
        // the window has a real size.
        splitPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                placeInitialDivider();
            }
        });

        toolBar = new AppToolBar(this);

        locationBar.setDirectory(workspace, false);
        locationBar.setBrowseAction(this::chooseWorkspace);
        locationBar.setDirectoryConsumer(this::applyTypedDirectory);

        JPanel north = new JPanel(new BorderLayout());
        north.add(toolBar, BorderLayout.NORTH);
        north.add(locationBar, BorderLayout.SOUTH);

        setJMenuBar(createMenuBar());
        add(north, BorderLayout.NORTH);
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

            @Override
            public void windowDeactivated(WindowEvent e) {
                hideAllCompletions();
            }

            @Override
            public void windowIconified(WindowEvent e) {
                hideAllCompletions();
            }
        });
        addWindowStateListener(e -> {
            if ((e.getNewState() & java.awt.Frame.ICONIFIED) != 0) {
                hideAllCompletions();
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
        file.add(item(openAction));
        file.addSeparator();
        file.add(item(saveAction));
        file.add(item(saveAsAction));
        file.addSeparator();
        file.add(item(closeTabAction));
        file.addSeparator();
        file.add(item(workspaceAction));
        file.add(item(adminAction));
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
        tab.completion().setBaseDirectory(workspace);
        tab.completion().setHintConsumer(parameterHint -> {
            if (tab == editorTabs.current()) {
                statusBar.setHint(parameterHint == null ? null : describe(parameterHint));
            }
        });
        SwingUtilities.invokeLater(tab.textPane()::requestFocusInWindow);
        return tab;
    }

    /** Renders a signature hint as one line for the status bar. */
    private static String describe(com.dogepopcat.cpgui.completion.ParameterHint hint) {
        StringBuilder text = new StringBuilder(hint.command());
        if (hint.description() != null && !hint.description().isBlank()) {
            text.append(" \u2014 ").append(hint.description());
        }
        String parameters = hint.parameterLine();
        if (!parameters.isEmpty()) {
            text.append("   ").append(parameters);
        }
        return text.toString();
    }

    /**
     * Hides every suggestion list. They are always-on-top windows, so they must
     * not survive the frame being minimised or another application taking over.
     */
    private void hideAllCompletions() {
        for (EditorTab open : editorTabs.tabs()) {
            open.completion().close();
        }
        statusBar.setHint(null);
    }

    /**
     * Places the editor/console divider proportionally the first time the window
     * has a usable height, then never interferes again so the user's own
     * position is kept.
     */
    private void placeInitialDivider() {
        if (dividerPlaced || splitPane.getHeight() <= 0) {
            return;
        }
        dividerPlaced = true;
        splitPane.setDividerLocation(EDITOR_SHARE);
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

    /**
     * Closes the tab at {@code index}, releasing its interpreter session.
     *
     * <p>Unsaved changes are offered for saving first, and cancelling that
     * prompt leaves the tab open.</p>
     */
    public void closeTab(int index) {
        if (index < 0 || index >= editorTabs.tabCount()) {
            return;
        }
        EditorTab tab = editorTabs.tabs().get(index);
        if (!confirmDiscard(tab)) {
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

    // --- file operations ----------------------------------------------------

    /** Asks the user for a script and opens it in a new tab. */
    public void openScript() {
        javax.swing.JFileChooser chooser = scriptChooser("Open script");
        if (chooser.showOpenDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (file == null || !file.isFile()) {
            return;
        }
        openFile(file);
    }

    /**
     * Loads a file into a new tab.
     *
     * @param file the script to read
     * @return the new tab, or {@code null} when reading failed
     */
    public EditorTab openFile(File file) {
        String content;
        try {
            content = ScriptFile.read(file);
        } catch (IOException e) {
            console.appendError("Could not read " + file + ": " + e.getMessage()
                    + System.lineSeparator());
            statusBar.setMessage("Could not open " + file.getName());
            return null;
        }
        EditorTab tab = createTab(ScriptFile.shellKindFor(file));
        tab.setFile(file);
        tab.setText(content);
        tab.clearDirty();
        editorTabs.refreshTitles();
        statusBar.setMessage("Opened " + file.getName());
        return tab;
    }

    /** Saves the active tab, asking for a name when it has none yet. */
    public boolean saveActiveScript() {
        EditorTab tab = editorTabs.current();
        return tab != null && saveTab(tab, tab.file());
    }

    /** Saves the active tab under a new name. */
    public boolean saveActiveScriptAs() {
        EditorTab tab = editorTabs.current();
        return tab != null && saveTabAs(tab);
    }

    /**
     * Saves a tab to a specific file, or asks for one when {@code target} is
     * {@code null}.
     *
     * @return {@code true} when the file was written
     */
    public boolean saveTab(EditorTab tab, File target) {
        if (target == null) {
            return saveTabAs(tab);
        }
        try {
            ScriptFile.write(target, tab.getText(), tab.shellKind());
        } catch (ScriptFile.UnrepresentableCharactersException lossy) {
            if (!confirmLossySave(target, lossy)) {
                statusBar.setMessage("Save cancelled");
                return false;
            }
            try {
                ScriptFile.writeLossy(target, tab.getText(), tab.shellKind());
            } catch (IOException e) {
                return reportSaveFailure(target, e);
            }
        } catch (IOException e) {
            return reportSaveFailure(target, e);
        }
        tab.setFile(target);
        tab.clearDirty();
        editorTabs.refreshTitles();
        statusBar.setMessage("Saved " + target.getName()
                + " (" + ScriptFile.writeCharset(tab.shellKind()).name() + ")");
        return true;
    }

    private boolean reportSaveFailure(File target, IOException e) {
        console.appendError("Could not save " + target + ": " + e.getMessage()
                + System.lineSeparator());
        statusBar.setMessage("Could not save " + target.getName());
        JOptionPane.showMessageDialog(this,
                "Could not save the file:\n" + e.getMessage(),
                "Save failed", JOptionPane.ERROR_MESSAGE);
        return false;
    }

    /**
     * Warns that the console code page cannot hold part of the script.
     *
     * <p>Silently replacing characters with {@code ?} would corrupt the script
     * without the user noticing, so the choice is theirs.</p>
     */
    private boolean confirmLossySave(File target, ScriptFile.UnrepresentableCharactersException e) {
        String message = "These characters cannot be written in " + e.charset().name()
                + ", which is the code page cmd.exe reads batch files with:\n\n    "
                + e.characters()
                + "\n\nSaving will replace them with '?'. "
                + "Save the script as PowerShell (.ps1) to keep them.\n\nSave anyway?";
        int choice = JOptionPane.showConfirmDialog(this, message,
                "Some characters cannot be saved", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        return choice == JOptionPane.YES_OPTION;
    }

    /** Asks for a destination and saves the tab there. */
    public boolean saveTabAs(EditorTab tab) {
        javax.swing.JFileChooser chooser = scriptChooser("Save script as");
        chooser.setSelectedFile(new File(workspace, defaultNameFor(tab)));
        if (chooser.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return false;
        }
        File target = ensureExtension(chooser.getSelectedFile(), tab.shellKind());
        if (target.exists() && JOptionPane.showConfirmDialog(this,
                target.getName() + " already exists.\nReplace it?",
                "Confirm save as", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return false;
        }
        return saveTab(tab, target);
    }

    private String defaultNameFor(EditorTab tab) {
        return tab.file() != null ? tab.file().getName()
                : ScriptFile.defaultFileName(tab.shellKind());
    }

    /** Adds the interpreter's extension when the user did not type one. */
    private File ensureExtension(File file, ShellKind kind) {
        String name = file.getName();
        if (name.indexOf('.') >= 0) {
            return file;
        }
        return new File(file.getParentFile(), name + "." + kind.fileExtension());
    }

    private javax.swing.JFileChooser scriptChooser(String title) {
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser(workspace);
        chooser.setDialogTitle(title);
        chooser.setAcceptAllFileFilterUsed(true);
        chooser.addChoosableFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Batch scripts (*.cmd, *.bat)", "cmd", "bat"));
        chooser.addChoosableFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "PowerShell scripts (*.ps1)", "ps1"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "All scripts (*.cmd, *.bat, *.ps1)", "cmd", "bat", "ps1"));
        return chooser;
    }

    /**
     * Offers to save a tab that has unsaved changes.
     *
     * @return {@code true} when it is safe to discard the tab
     */
    private boolean confirmDiscard(EditorTab tab) {
        if (tab == null || !tab.isDirty()) {
            return true;
        }
        String name = tab.file() != null ? tab.file().getName() : tab.title();
        int choice = JOptionPane.showConfirmDialog(this,
                "Save changes to " + name + "?",
                "Unsaved changes", JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
            return false;
        }
        if (choice == JOptionPane.NO_OPTION) {
            return true;
        }
        return saveTab(tab, tab.file());
    }

    private void activeTabChanged() {
        EditorTab tab = editorTabs.current();
        if (tab == null) {
            return;
        }
        statusBar.setShell(tab.shellKind());
        toolBar.setInterpreter(tab.shellKind());
        problems.setFindings(tab.diagnostics().findings());
        refreshLiveDirectory(tab);
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

        List<Finding> dangers = DangerAnalyzer.dangers(tab.shellKind(), script);
        if (!dangers.isEmpty() && !confirmation.confirm(dangers, script)) {
            console.appendSystem("[run cancelled: "
                    + dangers.size()
                    + (dangers.size() == 1 ? " dangerous command was" : " dangerous commands were")
                    + " not confirmed]" + System.lineSeparator());
            statusBar.setMessage("Run cancelled");
            statusBar.setMessageColor(Theme.WARNING);
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

    // --- dangerous command confirmation -------------------------------------

    /**
     * Decides whether a script containing dangerous commands may run. Swapped
     * out in tests so the prompt can be answered without a human.
     */
    public interface DangerConfirmation {

        /**
         * @param dangers the dangerous findings found in the script
         * @param script  the script about to be submitted
         * @return {@code true} to run anyway, {@code false} to cancel
         */
        boolean confirm(List<Finding> dangers, String script);
    }

    private DangerConfirmation confirmation = this::askAboutDangerousCommands;

    /** Replaces the confirmation prompt, mainly so tests can answer it. */
    public void setDangerConfirmation(DangerConfirmation confirmation) {
        this.confirmation = confirmation == null
                ? (dangers, script) -> false
                : confirmation;
    }

    /**
     * Shows the warning dialog listing every dangerous command found.
     *
     * <p>The cancel button is the default, so an accidental Enter never runs a
     * destructive script.</p>
     */
    private boolean askAboutDangerousCommands(List<Finding> dangers, String script) {
        StringBuilder text = new StringBuilder();
        text.append("<html><b>This script contains ")
                .append(dangers.size())
                .append(dangers.size() == 1 ? " destructive command" : " destructive commands")
                .append(".</b><br><br>");
        for (Finding danger : dangers) {
            text.append("<b>Line ").append(danger.lineIndex() + 1).append(":</b> ")
                    .append(escapeHtml(danger.message())).append("<br>");
            text.append("<font color='#666666'>&nbsp;&nbsp;&nbsp;&nbsp;")
                    .append(escapeHtml(danger.snippet()))
                    .append("</font><br>");
            text.append("<font color='#666666'>&nbsp;&nbsp;&nbsp;&nbsp;\u2192 ")
                    .append(escapeHtml(danger.advice()))
                    .append("</font><br><br>");
        }
        text.append("Run it anyway?</html>");

        JLabel message = new JLabel(text.toString());
        message.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        Object[] options = {"Run anyway", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this, message, "Dangerous command",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null,
                options, options[1]);
        return choice == 0;
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Stops the active tab's interpreter, discarding its state. */
    public void stopActiveSession() {
        EditorTab tab = editorTabs.current();        if (tab == null) {
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
        if (WorkspacePolicy.isSystemDirectory(directory)) {
            // Running commands next to the operating system is how accidents
            // happen, so a system folder is refused rather than accepted quietly.
            int choice = JOptionPane.showConfirmDialog(this,
                    directory.getAbsolutePath() + "\n\n"
                            + "This is a Windows system folder. Commands run here could damage "
                            + "the operating system.\n\nUse it anyway?",
                    "System folder", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                locationBar.showRejected("refused");
                statusBar.setMessage("Workspace unchanged");
                return;
            }
        }
        this.workspace = directory;
        statusBar.setWorkspace(directory);
        locationBar.setDirectory(directory, false);
        for (EditorTab open : editorTabs.tabs()) {
            open.completion().setBaseDirectory(directory);
        }

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

    /** Applies a directory typed into the location bar. */
    private void applyTypedDirectory(File directory) {
        if (directory == null || !directory.isDirectory()) {
            locationBar.showRejected("not found");
            statusBar.setMessage("Not a directory: " + directory);
            return;
        }
        setWorkspace(directory);
    }

    /**
     * Asks the active tab's interpreter where it actually is and shows the
     * answer.
     *
     * <p>The configured workspace is only where sessions start; a {@code cd}
     * typed into the editor moves the interpreter somewhere else. Querying the
     * session keeps the location bar truthful.</p>
     */
    public void refreshLiveDirectory(EditorTab tab) {
        if (tab == null) {
            return;
        }
        ShellSession session = sessions.peek(tab);
        if (session == null || !session.isAlive() || session.isBusy()) {
            if (tab == editorTabs.current()) {
                locationBar.setDirectory(workspace, false);
            }
            return;
        }
        String probe = tab.shellKind() == ShellKind.CMD ? "cd" : "(Get-Location).Path";
        try {
            session.query(probe).thenAccept(result -> {
                String reported = result.replace("\r", "").strip();
                if (reported.isEmpty()) {
                    return;
                }
                SwingUtilities.invokeLater(() -> {
                    if (tab == editorTabs.current()) {
                        locationBar.setDirectory(new File(reported), true);
                    }
                });
            }).exceptionally(failure -> null);
        } catch (IllegalStateException busy) {
            // A command started in the meantime; the next finish will refresh.
        }
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
        for (EditorTab tab : editorTabs.tabs()) {
            if (!confirmDiscard(tab)) {
                return;
            }
        }
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
            boolean result = queryElevation();
            SwingUtilities.invokeLater(() -> {
                elevated = result;
                statusBar.setElevated(result);
                toolBar.setElevated(result);
                adminAction.putValue(Action.SHORT_DESCRIPTION, result
                        ? "This window already has administrator rights"
                        : "Open a second window with administrator rights");
            });
        }, "cpgui-elevation");
        thread.setDaemon(true);
        thread.start();
    }

    /** @return {@code true} when this process holds a high integrity token. */
    private boolean queryElevation() {
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
            return text.contains("S-1-16-12288") || text.contains("S-1-16-16384");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }
    }

    // --- administrator mode -------------------------------------------------

    /**
     * Opens a second, elevated window without disturbing this one.
     *
     * <p>Windows cannot raise the integrity level of a running process, so a new
     * one is started through the {@code runas} verb. The original window is left
     * open: closing it was needlessly destructive and made comparing the two
     * impossible. The new window inherits the current working directory.</p>
     */
    public void requestAdministratorMode() {
        if (elevated) {
            JOptionPane.showMessageDialog(this,
                    "This window is already running with administrator rights.\n\n"
                            + "The elevated window is the one with the highlighted "
                            + "Administrator button.",
                    "Administrator mode", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        List<String> command = com.dogepopcat.cpgui.util.CommandLine.currentProcess();
        if (command.size() < 2) {
            console.appendError("Could not work out how this program was started, so an "
                    + "elevated copy cannot be started." + System.lineSeparator());
            statusBar.setMessage("Administrator restart unavailable");
            return;
        }

        int choice = JOptionPane.showConfirmDialog(this,
                "Windows will ask for permission to open a second window with "
                        + "administrator rights.\n\n"
                        + "This window stays open. The elevated window starts in "
                        + workspace.getAbsolutePath() + " and its Administrator button "
                        + "is highlighted.\n\nContinue?",
                "Open an elevated window", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            statusBar.setMessage("Administrator window cancelled");
            return;
        }

        String executable = command.get(0);
        String arguments = String.join(" ", command.subList(1, command.size()))
                + " --workspace \"" + workspace.getAbsolutePath() + "\"";
        ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile",
                "-NonInteractive", "-Command",
                "Start-Process -FilePath $env:CPGUI_ELEVATE_EXE"
                        + " -ArgumentList $env:CPGUI_ELEVATE_ARGS -Verb RunAs");
        // Passing the values through the environment avoids every quoting rule.
        builder.environment().put("CPGUI_ELEVATE_EXE", executable);
        builder.environment().put("CPGUI_ELEVATE_ARGS", arguments);
        builder.redirectErrorStream(true);

        try {
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(),
                    com.dogepopcat.cpgui.shell.ConsoleCharset.get());
            int exit = process.waitFor();
            if (exit != 0) {
                console.appendError("The elevated window could not be started: "
                        + output.strip() + System.lineSeparator());
                statusBar.setMessage("Administrator window was refused");
                JOptionPane.showMessageDialog(this,
                        "The elevated window could not be started.\n\n"
                                + "This usually means the permission prompt was dismissed.",
                        "Administrator mode", JOptionPane.WARNING_MESSAGE);
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (IOException e) {
            console.appendError("The elevated window could not be started: " + e.getMessage()
                    + System.lineSeparator());
            statusBar.setMessage("Administrator window failed");
            return;
        }

        console.appendSystem("[an elevated window was started; this one keeps running]"
                + System.lineSeparator());
        statusBar.setMessage("Elevated window started");
    }

    /**
     * Splits a Windows command line, honouring double quotes around the
     * executable and keeping the remaining arguments as they were written.
     *
     * @param commandLine the raw command line
     * @return the executable followed by the argument string, or an empty list
     */
    static List<String> splitCommandLine(String commandLine) {
        return com.dogepopcat.cpgui.util.CommandLine.split(commandLine);
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
                refreshLiveDirectory(tab);
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

    /** @return the bar showing the current working directory. */
    public LocationBar locationBar() {
        return locationBar;
    }

    public Action newTabAction() {
        return newTabAction;
    }

    public Action openAction() {
        return openAction;
    }

    public Action saveAction() {
        return saveAction;
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

    public Action adminAction() {
        return adminAction;
    }

    /** @return {@code true} when the process holds administrator rights. */
    public boolean isElevated() {
        return elevated;
    }
}
