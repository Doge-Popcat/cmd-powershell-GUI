package com.dogepopcat.cpgui.ui;

import com.dogepopcat.cpgui.AppInfo;
import com.dogepopcat.cpgui.analysis.DangerAnalyzer;
import com.dogepopcat.cpgui.analysis.DangerAnalyzer.Finding;
import com.dogepopcat.cpgui.analysis.ScriptParser;
import com.dogepopcat.cpgui.io.ScriptFile;
import com.dogepopcat.cpgui.i18n.Language;
import com.dogepopcat.cpgui.i18n.Messages;
import com.dogepopcat.cpgui.i18n.Msg;
import com.dogepopcat.cpgui.io.WorkspacePolicy;
import com.dogepopcat.cpgui.shell.ConsolePalette;
import com.dogepopcat.cpgui.shell.SessionRegistry;
import com.dogepopcat.cpgui.shell.ShellKind;
import com.dogepopcat.cpgui.shell.ShellSession;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
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

    /** Width of the file tree when it is shown, in pixels. */
    private static final int EXPLORER_WIDTH = 280;

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
    private final FileExplorer explorer = new FileExplorer();
    private JSplitPane horizontalSplit;
    private HelpWindow helpWindow;
    private volatile boolean elevated;
    private boolean dividerPlaced;
    /** The font every editor tab uses; new tabs inherit it. */
    private java.awt.Font editorFont = Theme.mono(13);
    /** The font the console uses. */
    private java.awt.Font consoleFont = Theme.mono(13);
    /** Actions whose name and tooltip follow the selected language. */
    private final java.util.List<Object[]> localizedActions = new java.util.ArrayList<>();
    /** Menus whose title follows the selected language. */
    private final java.util.List<Object[]> localizedMenus = new java.util.ArrayList<>();

    // --- actions -----------------------------------------------------------

    private final Action newTabAction = action(Msg.ACTION_NEW_TAB, Msg.TIP_NEW_TAB,
            KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK),
            e -> newTab());

    private final Action closeTabAction = action(Msg.ACTION_CLOSE_TAB, Msg.TIP_CLOSE_TAB,
            KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK),
            e -> closeTab(editorTabs.getSelectedIndex()));

    private final Action openAction = action(Msg.ACTION_OPEN, Msg.TIP_OPEN,
            KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK),
            e -> openScript());

    private final Action saveAction = action(Msg.ACTION_SAVE, Msg.TIP_SAVE,
            KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK),
            e -> saveActiveScript());

    private final Action saveAsAction = action(Msg.ACTION_SAVE_AS, Msg.TIP_SAVE_AS,
            KeyStroke.getKeyStroke(KeyEvent.VK_S,
                    InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
            e -> saveActiveScriptAs());

    private final Action exitAction = action(Msg.ACTION_EXIT, Msg.TIP_EXIT, null,
            e -> dispatchClose());

    private final Action undoAction = action(Msg.ACTION_UNDO, Msg.ACTION_UNDO,
            KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK),
            e -> withEditor(tab -> trigger(tab, "cpgui.undo")));

    private final Action redoAction = action(Msg.ACTION_REDO, Msg.ACTION_REDO,
            KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK),
            e -> withEditor(tab -> trigger(tab, "cpgui.redo")));

    private final Action cutAction = action(Msg.ACTION_CUT, Msg.ACTION_CUT,
            KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK),
            e -> withEditor(tab -> tab.textPane().cut()));

    private final Action copyAction = action(Msg.ACTION_COPY, Msg.ACTION_COPY,
            KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK),
            e -> withEditor(tab -> tab.textPane().copy()));

    private final Action pasteAction = action(Msg.ACTION_PASTE, Msg.ACTION_PASTE,
            KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK),
            e -> withEditor(tab -> tab.textPane().paste()));

    private final Action selectAllAction = action(Msg.ACTION_SELECT_ALL, Msg.ACTION_SELECT_ALL,
            KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK),
            e -> withEditor(tab -> tab.textPane().selectAll()));

    private final Action clearOutputAction = action(Msg.ACTION_CLEAR_OUTPUT, Msg.TIP_CLEAR_OUTPUT,
            null, e -> console.clear());

    private final Action largerFontAction = action(Msg.ACTION_LARGER_FONT, Msg.TIP_LARGER_FONT,
            KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK),
            e -> zoomEditor(1));

    private final Action smallerFontAction = action(Msg.ACTION_SMALLER_FONT, Msg.TIP_SMALLER_FONT,
            KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK),
            e -> zoomEditor(-1));

    private final Action fontAction = action(Msg.ACTION_FONTS,
            Msg.TIP_FONTS,
            KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK
                    | InputEvent.SHIFT_DOWN_MASK),
            e -> chooseFonts());

    private final Action runAction = action(Msg.ACTION_RUN, Msg.TIP_RUN,
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK),
            e -> runActiveScript());

    private final Action runAllAction = action(Msg.ACTION_RUN_ALL, Msg.TIP_RUN_ALL,
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,
                    InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
            e -> runWholeScript());

    private final Action runLineAction = action(Msg.ACTION_RUN_LINE, Msg.TIP_RUN_LINE,
            KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK),
            e -> runCurrentLine());

    private final Action singleLineAction = buildSingleLineAction();

    private final Action stopAction = action(Msg.ACTION_STOP, Msg.TIP_STOP,
            KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0),
            e -> stopActiveSession());

    private final Action workspaceAction = action(Msg.ACTION_WORKSPACE, Msg.TIP_WORKSPACE,
            KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK),
            e -> chooseWorkspace());

    private final Action adminAction = action(Msg.ACTION_ADMIN, Msg.TIP_ADMIN,
            null, e -> requestAdministratorMode());

    private final Action explorerAction = action(Msg.ACTION_EXPLORER, Msg.TIP_EXPLORER,
            KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK
                    | InputEvent.SHIFT_DOWN_MASK),
            e -> toggleExplorer());

    private final Action helpAction = action(Msg.ACTION_HELP, Msg.TIP_HELP,
            KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0),
            e -> showHelp());

    private final Action aboutAction = action(Msg.ACTION_ABOUT, Msg.TIP_ABOUT, null, e -> showAbout());

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
        bottomTabs.addTab(Msg.PROBLEMS_TAB.text(), problems);
        bottomTabs.addTab(Msg.OUTPUT_TAB.text(), console);
        bottomTabs.setBorder(BorderFactory.createEmptyBorder());
        // A command typed at the console prompt runs in the active tab.
        console.setInputHandler(command -> {
            EditorTab target = editorTabs.current();
            if (target == null) {
                return;
            }
            execute(target, command);
            console.setPrompt(promptName(target.shellKind()) + ">");
        });

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

        // The file tree sits beside the editor and can be hidden from the View menu.
        explorer.setOpenHandler(this::openFile);
        explorer.setWorkspaceHandler(this::setWorkspace);
        explorer.setRootDirectory(workspace);
        horizontalSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, explorer, splitPane);
        horizontalSplit.setResizeWeight(0);
        horizontalSplit.setDividerSize(6);
        horizontalSplit.setBorder(BorderFactory.createEmptyBorder());
        horizontalSplit.setContinuousLayout(true);

        setJMenuBar(createMenuBar());
        add(north, BorderLayout.NORTH);
        add(horizontalSplit, BorderLayout.CENTER);
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
        Theme.addListener(this::themeChanged);
        // Style the window now: the look and feel defaults are light whatever
        // palette is selected, so waiting for a change would leave it half white.
        applyTheme();
        // Style the window now: the look and feel defaults are light whatever
        // palette is chosen, so waiting for a change would leave it half white.
        applyTheme();
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
        console.appendInfo(Msg.GREETING_RUN_HINT.text() + System.lineSeparator());
        console.appendInfo(Msg.GREETING_SESSION_HINT.text() + System.lineSeparator());
        console.appendInfo(Msg.GREETING_CONSOLE_HINT.text() + System.lineSeparator());
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

    /**
     * Builds an action whose name and tooltip follow the selected language.
     *
     * <p>The pair is remembered so {@link #applyLanguage()} can refresh every
     * action at once; Swing then repaints the menus and tool bar for free.</p>
     */
    private Action action(Msg name, Msg tooltip, KeyStroke accelerator,
                          Consumer<ActionEvent> body) {
        Action action = action(name.text(), tooltip.text(), accelerator, body);
        localizedActions.add(new Object[]{action, name, tooltip});
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
                + "<br>" + Msg.ABOUT_SUBTITLE.text()
                + "<br><br>" + Msg.ABOUT_DETAILS.format(System.getProperty("java.version"),
                        System.getProperty("os.name"))
                + "<br>" + Msg.ABOUT_CODEPAGE.format(
                        com.dogepopcat.cpgui.shell.ConsoleCharset.get().name())
                + "<br>" + Msg.ABOUT_LICENSE.text() + "</html>";
        panel.add(new JLabel(html), BorderLayout.CENTER);
        JOptionPane.showMessageDialog(this, panel, Msg.ABOUT_TITLE.format(AppInfo.NAME),
                JOptionPane.INFORMATION_MESSAGE);
    }

    // --- menu ---------------------------------------------------------------

    private JMenuBar createMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = menu(Msg.MENU_FILE, 'F');
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

        JMenu edit = menu(Msg.MENU_EDIT, 'E');
        edit.add(item(undoAction));
        edit.add(item(redoAction));
        edit.addSeparator();
        edit.add(item(cutAction));
        edit.add(item(copyAction));
        edit.add(item(pasteAction));
        edit.addSeparator();
        edit.add(item(selectAllAction));
        bar.add(edit);

        JMenu run = menu(Msg.MENU_RUN, 'R');
        run.add(item(runAction));
        run.add(item(runAllAction));
        run.add(item(runLineAction));
        run.add(item(singleLineAction));
        run.addSeparator();
        run.add(item(stopAction));
        bar.add(run);

        JMenu view = menu(Msg.MENU_VIEW, 'V');
        view.add(item(explorerAction));
        view.addSeparator();
        view.add(item(clearOutputAction));
        view.addSeparator();
        view.add(item(fontAction));
        view.add(item(largerFontAction));
        view.add(item(smallerFontAction));
        view.addSeparator();
        view.add(createLanguageMenu());
        view.add(createThemeMenu());
        bar.add(view);

        JMenu help = menu(Msg.MENU_HELP, 'H');
        help.add(item(helpAction));
        help.addSeparator();
        help.add(item(aboutAction));
        bar.add(help);

        return bar;
    }

    /** Builds a menu whose title follows the selected language. */
    private JMenu menu(Msg title, char mnemonic) {
        JMenu menu = new JMenu(title.text());
        menu.setMnemonic(mnemonic);
        localizedMenus.add(new Object[]{menu, title});
        return menu;
    }

    /** Builds the language submenu with one radio entry per language. */
    private JMenu createLanguageMenu() {
        JMenu language = menu(Msg.MENU_LANGUAGE, 'L');
        ButtonGroup group = new ButtonGroup();
        for (Language option : Language.values()) {
            JRadioButtonMenuItem entry = new JRadioButtonMenuItem(option.displayName());
            entry.setSelected(Messages.language() == option);
            entry.addActionListener(e -> {
                Messages.setLanguage(option);
                applyLanguage();
            });
            group.add(entry);
            language.add(entry);
        }
        language.setIcon(null);
        return language;
    }

    /** Builds the theme submenu with one radio entry per palette. */
    private JMenu createThemeMenu() {
        JMenu theme = menu(Msg.MENU_THEME, 'T');
        ButtonGroup group = new ButtonGroup();
        for (Theme.Mode option : Theme.Mode.values()) {
            JRadioButtonMenuItem entry = new JRadioButtonMenuItem(option.label());
            entry.setSelected(Theme.mode() == option);
            entry.addActionListener(e -> {
                Theme.apply(option);
                statusBar.setMessage(Msg.THEME_CHANGED.format(option.label()));
            });
            group.add(entry);
            theme.add(entry);
        }
        return theme;
    }

    /** Shows or hides the file tree beside the editor. */
    public void toggleExplorer() {
        boolean visible = !explorer.isVisible();
        explorer.setVisible(visible);
        horizontalSplit.setDividerLocation(visible ? EXPLORER_WIDTH : 0);
        horizontalSplit.setDividerSize(visible ? 6 : 0);
        horizontalSplit.revalidate();
        horizontalSplit.repaint();
        statusBar.setMessage(visible
                ? Msg.STATUS_EXPLORER_SHOWN.text()
                : Msg.STATUS_EXPLORER_HIDDEN.text());
    }

    /** @return the file tree panel. */
    public FileExplorer explorer() {
        return explorer;
    }

    /**
     * Opens the syntax reference, reusing the window when it is already up.
     */
    public void showHelp() {
        EditorTab tab = editorTabs.current();
        ShellKind kind = tab == null ? ShellKind.CMD : tab.shellKind();
        if (helpWindow != null && helpWindow.isDisplayable()) {
            helpWindow.setTitle(Msg.HELP_TITLE.text());
            helpWindow.toFront();
            return;
        }
        helpWindow = HelpWindow.open(this, kind, command -> {
            EditorTab target = editorTabs.current();
            if (target == null) {
                return;
            }
            String request = target.shellKind() == ShellKind.CMD
                    ? command + " /?"
                    : "Get-Help " + command + " -Full";
            selectBottomTab(BottomTab.OUTPUT);
            execute(target, request);
        });
    }

    /**
     * Restyles the window after the palette has actually changed.
     *
     * <p>Restyling the component tree is what makes the menu bar, the tab strips
     * and the scroll bars follow the palette. It also replaces every component's
     * input and action map, which drops keys such as the console's history
     * bindings, so it may only run on a real change; the surfaces put their keys
     * back in their own {@code applyTheme}.</p>
     */
    private void themeChanged() {
        SwingUtilities.updateComponentTreeUI(this);
        applyTheme();
    }

    /**
     * Re-applies the palette to every surface.
     *
     * <p>Called once the window is built, and again from {@link #themeChanged()}
     * after the look and feel has been refreshed. Each surface is asked to style
     * itself rather than relying on listener order, so the result is the same
     * however the components happened to be created.</p>
     */
    void applyTheme() {
        SwingUtilities.updateComponentTreeUI(this);

        getContentPane().setBackground(Theme.WINDOW_BACKGROUND);
        setBackground(Theme.WINDOW_BACKGROUND);
        splitPane.setBackground(Theme.WINDOW_BACKGROUND);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        if (horizontalSplit != null) {
            horizontalSplit.setBackground(Theme.WINDOW_BACKGROUND);
            horizontalSplit.setBorder(BorderFactory.createEmptyBorder());
        }
        editorTabs.setBackground(Theme.WINDOW_BACKGROUND);
        bottomTabs.setBackground(Theme.WINDOW_BACKGROUND);
        bottomTabs.setForeground(Theme.WINDOW_FOREGROUND);
        getJMenuBar().setBackground(Theme.WINDOW_BACKGROUND);
        getJMenuBar().setForeground(Theme.WINDOW_FOREGROUND);

        // Restyling the tree resets every colour and font to the look and feel
        // default, so each surface is asked to style itself again afterwards.
        statusBar.applyTheme();
        locationBar.applyTheme();
        toolBar.applyTheme();
        console.applyTheme();
        problems.applyTheme();
        explorer.applyTheme();
        for (EditorTab tab : editorTabs.tabs()) {
            tab.applyTheme();
        }
        toolBar.restyleFonts();
        applyFont(FontChooserDialog.Target.EDITOR, editorFont);
        applyFont(FontChooserDialog.Target.CONSOLE, consoleFont);
        problems.setFindings(problems.findings());
        // Restyling does not mark the previously painted areas dirty on its own,
        // and a stale dark corner left in a light theme is exactly the half
        // painted window this is meant to avoid, so the tree is invalidated.
        invalidate();
        validate();
        repaint();
        for (java.awt.Window window : java.awt.Window.getWindows()) {
            window.repaint();
        }
    }

    /**
     * Re-applies every localized string after the language changes.
     *
     * <p>Actions carry their own names, so updating the action values refreshes
     * the menus and the tool bar at the same time. Everything else refreshes
     * itself through its own {@link Messages} listener.</p>
     */
    public void applyLanguage() {
        for (Object[] entry : localizedActions) {
            Action action = (Action) entry[0];
            action.putValue(Action.NAME, ((Msg) entry[1]).text());
            action.putValue(Action.SHORT_DESCRIPTION, ((Msg) entry[2]).text());
        }
        for (Object[] entry : localizedMenus) {
            ((JMenu) entry[0]).setText(((Msg) entry[1]).text());
        }
        bottomTabs.setTitleAt(bottomTabs.indexOfComponent(problems), Msg.PROBLEMS_TAB.text());
        bottomTabs.setTitleAt(bottomTabs.indexOfComponent(console), Msg.OUTPUT_TAB.text());
        editorTabs.refreshTitles();
        console.refreshText();
        statusBar.setMessage(Msg.STATUS_READY.text());
        statusBar.setMessageColor(null);
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
        statusBar.setMessage(Msg.STATUS_TAB_CREATED.text());
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
        // A new tab inherits whatever font the user has chosen.
        tab.setEditorFont(editorFont);
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
        statusBar.setMessage(Msg.STATUS_TAB_CLOSED.text());
    }

    // --- file operations ----------------------------------------------------

    /** Asks the user for a script and opens it in a new tab. */
    public void openScript() {
        javax.swing.JFileChooser chooser = scriptChooser(Msg.DIALOG_OPEN_SCRIPT.text());
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
            console.appendError(Msg.ERROR_READ.format(file, e.getMessage())
                    + System.lineSeparator());
            statusBar.setMessage(Msg.STATUS_CANNOT_OPEN.format(file.getName()));
            return null;
        }
        EditorTab tab = createTab(ScriptFile.shellKindFor(file));
        tab.setFile(file);
        tab.setText(content);
        tab.clearDirty();
        editorTabs.refreshTitles();
        statusBar.setMessage(Msg.STATUS_OPENED.format(file.getName()));
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
                statusBar.setMessage(Msg.STATUS_SAVE_CANCELLED.text());
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
        console.appendError(Msg.ERROR_READ.format(target, e.getMessage())
                + System.lineSeparator());
        statusBar.setMessage(Msg.STATUS_CANNOT_SAVE.format(target.getName()));
        JOptionPane.showMessageDialog(this,
                Msg.ERROR_SAVE.format(e.getMessage()),
                Msg.DIALOG_SAVE_FAILED.text(), JOptionPane.ERROR_MESSAGE);
        return false;
    }

    /**
     * Warns that the console code page cannot hold part of the script.
     *
     * <p>Silently replacing characters with {@code ?} would corrupt the script
     * without the user noticing, so the choice is theirs.</p>
     */
    private boolean confirmLossySave(File target, ScriptFile.UnrepresentableCharactersException e) {
        int choice = JOptionPane.showConfirmDialog(this,
                Msg.DIALOG_LOSSY_MESSAGE.format(e.charset().name(), e.characters()),
                Msg.DIALOG_LOSSY_TITLE.text(), JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        return choice == JOptionPane.YES_OPTION;
    }

    /** Asks for a destination and saves the tab there. */
    public boolean saveTabAs(EditorTab tab) {
        javax.swing.JFileChooser chooser = scriptChooser(Msg.DIALOG_SAVE_SCRIPT.text());
        chooser.setSelectedFile(new File(workspace, defaultNameFor(tab)));
        if (chooser.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return false;
        }
        File target = ensureExtension(chooser.getSelectedFile(), tab.shellKind());
        if (target.exists() && JOptionPane.showConfirmDialog(this,
                Msg.DIALOG_REPLACE_MESSAGE.format(target.getName()),
                Msg.DIALOG_REPLACE.text(), JOptionPane.YES_NO_OPTION,
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
                Msg.FILE_BATCH_FILTER.text(), "cmd", "bat"));
        chooser.addChoosableFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                Msg.FILE_POWERSHELL_FILTER.text(), "ps1"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                Msg.FILE_ALL_SCRIPTS_FILTER.text(), "cmd", "bat", "ps1"));
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
                Msg.DIALOG_SAVE_CHANGES_MESSAGE.format(name),
                Msg.DIALOG_SAVE_CHANGES.text(), JOptionPane.YES_NO_CANCEL_OPTION,
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
        console.setPrompt(promptName(tab.shellKind()) + ">");
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
        // The prompt is the only visible sign of the switch, so update it at once
        // and say so in the console instead of waiting for the next command.
        console.setPrompt(promptName(kind) + ">");
        console.appendSystem(Msg.STATUS_INTERPRETER_SWITCHED.format(kind.displayName())
                + System.lineSeparator());
        statusBar.setMessage(Msg.STATUS_INTERPRETER_SWITCHED.format(kind.description()));
    }

    private void zoomEditor(int delta) {
        withEditor(tab -> {
            java.awt.Font font = tab.textPane().getFont();
            int size = Math.max(9, Math.min(28, font.getSize() + delta));
            tab.setEditorFont(font.deriveFont((float) size));
            statusBar.setMessage(Msg.STATUS_EDITOR_FONT.format(size));
        });
    }

    /** Opens the font chooser and applies whatever the user picks. */
    public void chooseFonts() {
        EditorTab tab = editorTabs.current();
        java.awt.Font current = tab != null ? tab.editorFont() : editorFont;
        java.util.Optional<FontChooserDialog.Result> choice = FontChooserDialog.show(
                this, FontChooserDialog.Target.EDITOR, current);
        if (choice.isEmpty()) {
            statusBar.setMessage(Msg.STATUS_FONTS_UNCHANGED.text());
            return;
        }
        FontChooserDialog.Result result = choice.get();
        applyFont(result.target(), result.font());
        statusBar.setMessage(Msg.STATUS_FONT_CHANGED.format(result.target(),
                result.font().getFamily(), result.font().getSize()));
    }

    /**
     * Applies a font to one of the two surfaces.
     *
     * @param target which surface to change
     * @param font   the font to use
     */
    public void applyFont(FontChooserDialog.Target target, java.awt.Font font) {
        if (target == FontChooserDialog.Target.CONSOLE) {
            this.consoleFont = font;
            console.setConsoleFont(font);
            return;
        }
        this.editorFont = font;
        for (EditorTab open : editorTabs.tabs()) {
            open.setEditorFont(font);
        }
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
        if (tab.isSingleLineMode()) {
            runSingleLine();
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
                    statusBar.setMessage(Msg.STATUS_COMMENT_LINE.text());
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

    // --- console commands handled by the application ------------------------

    /**
     * Handles the handful of commands that only make sense against a real
     * console window.
     *
     * <p>{@code cls} and {@code color} act on the console screen buffer. Output
     * here is captured through a pipe, so the interpreter has no buffer to act
     * on and both would fail silently; the application owns the surface the user
     * is looking at, so it performs them instead.</p>
     *
     * @param tab    the active tab
     * @param script the text about to be submitted
     * @return {@code true} when the command was handled and must not be sent on
     */
    private boolean runConsoleCommand(EditorTab tab, String script) {
        String trimmed = script.strip();
        if (trimmed.contains("\n") || tab.shellKind() != ShellKind.CMD) {
            return false;
        }
        if (trimmed.equalsIgnoreCase("cls")) {
            console.clear();
            console.appendSystem("[console cleared]" + System.lineSeparator());
            statusBar.setMessage(Msg.STATUS_CONSOLE_CLEARED.text());
            return true;
        }
        if (trimmed.toLowerCase(java.util.Locale.ROOT).startsWith("color")
                && (trimmed.length() == 5 || Character.isWhitespace(trimmed.charAt(5)))) {
            String argument = trimmed.length() <= 5 ? "" : trimmed.substring(5).strip();
            java.util.Optional<java.awt.Color[]> palette = ConsolePalette.parse(
                    argument, new java.awt.Color[]{console.paletteBackground(),
                            console.paletteForeground()});
            if (palette.isEmpty()) {
                console.appendError(Msg.STATUS_BAD_COLOUR.format(argument)
                        + System.lineSeparator());
                statusBar.setMessage(Msg.STATUS_BAD_COLOUR.format(argument));
                return true;
            }
            java.awt.Color[] colours = palette.get();
            console.setPalette(colours[0], colours[1]);
            statusBar.setMessage(Msg.STATUS_CONSOLE_COLOUR.format(
                    argument.isEmpty() ? Msg.STATUS_COLOUR_DEFAULT.text() : argument));
            return true;
        }
        return false;
    }

    /** Sends a script to the active tab's interpreter. */
    private void execute(EditorTab tab, String script) {
        execute(tab, script, null);
    }

    /**
     * Sends a script to the interpreter and reports the outcome.
     *
     * @param tab      the tab to run in
     * @param script   the script to submit
     * @param onResult called with the exit code and whether it succeeded, or
     *                 {@code null} when the caller does not care
     */
    private void execute(EditorTab tab, String script,
                         java.util.function.BiConsumer<Integer, Boolean> onResult) {
        if (script == null || script.isBlank()) {
            statusBar.setMessage(Msg.STATUS_NOTHING_TO_RUN.text());
            return;
        }
        if (runConsoleCommand(tab, script)) {
            return;
        }

        List<Finding> dangers = DangerAnalyzer.dangers(tab.shellKind(), script);
        if (!dangers.isEmpty() && !confirmation.confirm(dangers, script)) {
            console.appendSystem(Msg.STATUS_RUN_CANCELLED_DANGER.format(dangers.size(),
                    dangers.size() == 1 ? "" : "s") + System.lineSeparator());
            statusBar.setMessage(Msg.STATUS_RUN_CANCELLED.text());
            statusBar.setMessageColor(Theme.WARNING);
            return;
        }

        ShellSession session;
        try {
            session = sessions.acquire(tab, tab.shellKind(), workspace,
                    new ConsoleSessionListener(tab, onResult));
        } catch (IOException e) {
            console.appendError(tab.shellKind().executable() + ": "
                    + e.getMessage() + System.lineSeparator());
            statusBar.setMessage(Msg.STATUS_CANNOT_START.format(tab.shellKind().displayName()));
            return;
        }

        // The session queues the script, so a run that arrives while the previous
        // one, or the internal directory query, is still in flight is not lost.

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
            statusBar.setMessage(Msg.STATUS_CANNOT_RUN.text());
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
        text.append(dangers.size() == 1
                ? Msg.DANGER_HEADER_ONE.text()
                : Msg.DANGER_HEADER_MANY.format(dangers.size()));
        for (Finding danger : dangers) {
            text.append(Msg.DANGER_LINE.format(danger.lineIndex() + 1,
                    escapeHtml(danger.message())));
            text.append(Msg.DANGER_SNIPPET.format(escapeHtml(danger.snippet())));
            text.append(Msg.DANGER_ADVICE.format(escapeHtml(danger.advice())));
        }
        text.append(Msg.DANGER_FOOTER.text());

        JLabel message = new JLabel(text.toString());
        message.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        Object[] options = {Msg.RUN_ANYWAY.text(), Msg.CANCEL.text()};
        int choice = JOptionPane.showOptionDialog(this, message, Msg.DIALOG_DANGER_TITLE.text(),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null,
                options, options[1]);
        return choice == 0;
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Builds the single-line mode toggle.
     *
     * <p>It is a toggle button so the current state is visible in the tool bar
     * rather than only in a menu tick.</p>
     */
    private Action buildSingleLineAction() {
        Action action = new javax.swing.AbstractAction(Msg.ACTION_SINGLE_LINE.text(),
                javax.swing.Action.SMALL_ICON == null ? null : null) {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                setSingleLineMode(!isSingleLineMode());
            }
        };
        action.putValue(Action.SHORT_DESCRIPTION, Msg.TIP_SINGLE_LINE.text());
        localizedActions.add(new Object[]{action, Msg.ACTION_SINGLE_LINE, Msg.TIP_SINGLE_LINE});
        return action;
    }

    /** @return {@code true} when the active tab runs one line at a time. */
    public boolean isSingleLineMode() {
        EditorTab tab = editorTabs.current();
        return tab != null && tab.isSingleLineMode();
    }

    /**
     * Turns single line mode on or off for the active tab.
     *
     * @param enabled the new state
     */
    public void setSingleLineMode(boolean enabled) {
        EditorTab tab = editorTabs.current();
        if (tab == null) {
            return;
        }
        tab.setSingleLineMode(enabled);
        statusBar.setMessage(enabled
                ? Msg.STATUS_SINGLE_LINE_ON.text()
                : Msg.STATUS_SINGLE_LINE_OFF.text());
    }

    /**
     * Runs the caret's line, marks the result and moves to the next line.
     *
     * <p>This is what the Run button does while single line mode is on: the text
     * is still edited normally, but execution walks down the script one command
     * at a time and each line is washed green or red.</p>
     */
    public void runSingleLine() {
        EditorTab tab = editorTabs.current();
        if (tab == null) {
            return;
        }
        int line = tab.caretLine();
        String text = tab.lineText(line);
        String command = text.strip();
        boolean comment = ScriptParser.isCommentLine(tab.shellKind(), text);
        if (command.isEmpty() || comment) {
            tab.moveCaretToLine(line + 1);
            statusBar.setMessage(comment
                    ? Msg.STATUS_COMMENT_LINE.text() : Msg.STATUS_NOTHING_TO_RUN.text());
            return;
        }
        int runLine = line;
        execute(tab, command, (exitCode, success) -> {
            tab.markLineResult(runLine, success);
            tab.moveCaretToLine(runLine + 1);
        });
    }

    /** Stops the active tab's interpreter, discarding its state. */
    public void stopActiveSession() {
        EditorTab tab = editorTabs.current();        if (tab == null) {
            return;
        }
        ShellSession session = sessions.peek(tab);
        if (session == null) {
            statusBar.setMessage(Msg.STATUS_NO_SESSION.text());
            return;
        }
        sessions.release(tab);
        console.appendSystem(Msg.STATUS_SESSION_STOPPED.text() + System.lineSeparator());
        statusBar.setMessage(Msg.STATUS_SESSION_STOPPED_OK.text());
    }

    /**
     * Asks the user for a working directory and applies it everywhere: new
     * sessions start there and every live session is told to change into it.
     */
    public void chooseWorkspace() {
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser(workspace);
        chooser.setDialogTitle(Msg.DIALOG_CHOOSE_FOLDER.text());
        chooser.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setApproveButtonText(Msg.DIALOG_USE_FOLDER.text());
        if (chooser.showOpenDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
            statusBar.setMessage(Msg.STATUS_WORKSPACE_UNCHANGED.text());
            return;
        }
        File selected = chooser.getSelectedFile();
        if (selected == null || !selected.isDirectory()) {
            statusBar.setMessage(Msg.STATUS_WORKSPACE_UNCHANGED.text());
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
                    Msg.DIALOG_SYSTEM_FOLDER_MESSAGE.format(directory.getAbsolutePath()),
                    Msg.DIALOG_SYSTEM_FOLDER_TITLE.text(),
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                locationBar.showRejected("refused");
                statusBar.setMessage(Msg.STATUS_WORKSPACE_UNCHANGED.text());
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
            statusBar.setMessage(Msg.STATUS_NOT_A_DIRECTORY.format(directory));
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
        console.startBlock();
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
                    Msg.DIALOG_ADMIN_ALREADY.text(),
                    Msg.DIALOG_ADMIN_TITLE.text(), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        List<String> command = com.dogepopcat.cpgui.util.CommandLine.currentProcess();
        if (command.size() < 2) {
            console.appendError(Msg.DIALOG_ADMIN_NO_COMMAND.text() + System.lineSeparator());
            statusBar.setMessage(Msg.STATUS_ADMIN_UNAVAILABLE.text());
            return;
        }

        int choice = JOptionPane.showConfirmDialog(this,
                Msg.DIALOG_ADMIN_CONFIRM.format(workspace.getAbsolutePath()),
                Msg.DIALOG_ADMIN_TITLE.text(), JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            statusBar.setMessage(Msg.STATUS_ADMIN_CANCELLED.text());
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
                statusBar.setMessage(Msg.STATUS_ADMIN_REFUSED.text());
                JOptionPane.showMessageDialog(this,
                        Msg.DIALOG_ADMIN_REFUSED.text(),
                        Msg.DIALOG_ADMIN_TITLE.text(), JOptionPane.WARNING_MESSAGE);
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (IOException e) {
            console.appendError(Msg.DIALOG_ADMIN_TITLE.text() + ": " + e.getMessage()
                    + System.lineSeparator());
            statusBar.setMessage(Msg.STATUS_ADMIN_FAILED.text());
            return;
        }

        console.appendSystem(Msg.STATUS_ELEVATED_KEEPS_RUNNING.text()
                + System.lineSeparator());
        statusBar.setMessage(Msg.STATUS_ADMIN_STARTED.text());
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
        private final java.util.function.BiConsumer<Integer, Boolean> onResult;

        ConsoleSessionListener(EditorTab tab,
                               java.util.function.BiConsumer<Integer, Boolean> onResult) {
            this.tab = tab;
            this.onResult = onResult;
        }

        @Override
        public void onOutput(String text, boolean error) {
            console.append(text, error ? Theme.CONSOLE_STDERR : Theme.CONSOLE_FOREGROUND);
        }

        @Override
        public void onFinished(int exitCode, boolean success) {
            String summary = success
                    ? Msg.RUN_FINISHED.format(tab.shellKind().displayName(), exitCode)
                    : Msg.RUN_FAILED.format(tab.shellKind().displayName(), exitCode);
            console.append(summary + System.lineSeparator(),
                    success ? Theme.CONSOLE_INFO : Theme.ERROR);
            SwingUtilities.invokeLater(() -> {
                statusBar.setMessage(success
                        ? Msg.STATUS_FINISHED.format(exitCode)
                        : Msg.STATUS_FINISHED_ERROR.format(exitCode));
                statusBar.setMessageColor(success ? null : Theme.ERROR);
                refreshLiveDirectory(tab);
                if (onResult != null) {
                    onResult.accept(exitCode, success);
                }
            });
        }

        @Override
        public void onClosed(int exitCode) {
            console.appendSystem(Msg.STATUS_SESSION_CLOSED.format(
                    tab.shellKind().displayName(), exitCode) + System.lineSeparator());
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

    /**
     * Selects a tab in the bottom pane.
     *
     * @param which the pane to show
     */
    public void selectBottomTab(BottomTab which) {
        bottomTabs.setSelectedIndex(which == BottomTab.PROBLEMS
                ? bottomTabs.indexOfComponent(problems)
                : bottomTabs.indexOfComponent(console));
    }

    /** The two panes at the bottom of the window. */
    public enum BottomTab {
        PROBLEMS,
        OUTPUT
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

    /** @return the main tool bar, exposed for tests. */
    public AppToolBar toolBar() {
        return toolBar;
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

    /** @return the single-line mode toggle. */
    public Action singleLineAction() {
        return singleLineAction;
    }

    public Action adminAction() {
        return adminAction;
    }

    /** @return {@code true} when the process holds administrator rights. */
    public boolean isElevated() {
        return elevated;
    }
}
