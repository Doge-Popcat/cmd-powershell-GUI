package com.dogepopcat.cpgui.ui;

import com.dogepopcat.cpgui.AppInfo;
import com.dogepopcat.cpgui.shell.ShellKind;

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
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * The application main window.
 *
 * <p>Layout: a menu bar, a vertically split work area (editor tabs on top,
 * console below) and a status bar. Tool bar buttons and the execution engine
 * are layered on in subsequent commits.</p>
 */
public class MainFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private final EditorTabs editorTabs = new EditorTabs();
    private final ConsolePane console = new ConsolePane();
    private final StatusBar statusBar = new StatusBar();
    private final JTabbedPane bottomTabs = new JTabbedPane();
    private final JSplitPane splitPane;

    public MainFrame() {
        super(AppInfo.title());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(760, 500));

        editorTabs.setSelectionListener(index -> activeTabChanged());
        editorTabs.setTabCloseRequestHandler(this::closeTab);
        bottomTabs.addTab("Output", console);
        bottomTabs.setBorder(BorderFactory.createEmptyBorder());

        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorTabs, bottomTabs);
        splitPane.setResizeWeight(0.68);
        splitPane.setDividerSize(6);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setContinuousLayout(true);

        setJMenuBar(createMenuBar());
        add(splitPane, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        statusBar.setElevated(isElevated());
        statusBar.setWorkspace(new java.io.File(System.getProperty("user.dir")));
        statusBar.setShell(ShellKind.CMD);

        setIconImages(java.util.List.of(
                Icons.appIcon(16), Icons.appIcon(32), Icons.appIcon(64), Icons.appIcon(256)));

        setSize(1120, 740);
        setLocationRelativeTo(null);

        createTab(ShellKind.CMD);
        greet();
    }

    /**
     * Creates a tab and wires the listeners the frame needs on it.
     *
     * @param shellKind interpreter the new script targets
     * @return the new tab
     */
    private EditorTab createTab(ShellKind shellKind) {
        EditorTab tab = editorTabs.newTab(shellKind);
        tab.textPane().addCaretListener(e -> {
            if (tab == editorTabs.current()) {
                int[] pos = tab.caretLineColumn();
                statusBar.setCaret(pos[0], pos[1]);
            }
        });
        // Give the editor focus so the tab strip does not keep the focus ring.
        javax.swing.SwingUtilities.invokeLater(tab.textPane()::requestFocusInWindow);
        return tab;
    }

    /** Writes the welcome banner into the console. */
    private void greet() {
        console.appendLine(AppInfo.title(), Theme.CONSOLE_SYSTEM);
        console.appendInfo("Type commands in the editor above, then press Ctrl+Enter to run."
                + System.lineSeparator());
        console.appendInfo("Only this window's folder is used; nothing outside the project is touched."
                + System.lineSeparator());
        console.appendInfo(System.lineSeparator());
    }

    // --- menu ---------------------------------------------------------------

    private JMenuBar createMenuBar() {
        int menuMask = InputEvent.CTRL_DOWN_MASK;
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        file.setMnemonic('F');
        file.add(item("New Tab", KeyStroke.getKeyStroke(KeyEvent.VK_T, menuMask), e -> newTab()));
        file.add(item("Close Tab", KeyStroke.getKeyStroke(KeyEvent.VK_W, menuMask),
                e -> closeTab(editorTabs.getSelectedIndex())));
        file.addSeparator();
        file.add(item("Exit", null, e -> dispose()));
        bar.add(file);

        JMenu edit = new JMenu("Edit");
        edit.setMnemonic('E');
        edit.add(item("Undo", KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask),
                e -> withEditor("Undo", tab -> tab.textPane().getActionMap()
                        .get("cpgui.undo").actionPerformed(null))));
        edit.add(item("Redo", KeyStroke.getKeyStroke(KeyEvent.VK_Y, menuMask),
                e -> withEditor("Redo", tab -> tab.textPane().getActionMap()
                        .get("cpgui.redo").actionPerformed(null))));
        edit.addSeparator();
        edit.add(item("Cut", KeyStroke.getKeyStroke(KeyEvent.VK_X, menuMask),
                e -> withEditor("Cut", tab -> tab.textPane().cut())));
        edit.add(item("Copy", KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask),
                e -> withEditor("Copy", tab -> tab.textPane().copy())));
        edit.add(item("Paste", KeyStroke.getKeyStroke(KeyEvent.VK_V, menuMask),
                e -> withEditor("Paste", tab -> tab.textPane().paste())));
        edit.addSeparator();
        edit.add(item("Select All", KeyStroke.getKeyStroke(KeyEvent.VK_A, menuMask),
                e -> withEditor("Select All", tab -> tab.textPane().selectAll())));
        bar.add(edit);

        JMenu view = new JMenu("View");
        view.setMnemonic('V');
        view.add(item("Clear Output", null, e -> console.clear()));
        view.add(item("Focus Editor", KeyStroke.getKeyStroke(KeyEvent.VK_1, menuMask),
                e -> withEditor("Focus editor", tab -> tab.textPane().requestFocusInWindow())));
        view.addSeparator();
        view.add(item("Larger Editor Font", KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, menuMask),
                e -> zoomEditor(1)));
        view.add(item("Smaller Editor Font", KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, menuMask),
                e -> zoomEditor(-1)));
        bar.add(view);

        JMenu help = new JMenu("Help");
        help.setMnemonic('H');
        help.add(item("About", null, e -> showAbout()));
        bar.add(help);

        return bar;
    }

    private JMenuItem item(String text, KeyStroke accelerator,
                           java.awt.event.ActionListener listener) {
        JMenuItem menuItem = new JMenuItem(text);
        if (accelerator != null) {
            menuItem.setAccelerator(accelerator);
        }
        menuItem.addActionListener(listener);
        return menuItem;
    }

    private void showAbout() {
        JPanel panel = new JPanel(new BorderLayout(12, 0));
        panel.add(new JLabel(new javax.swing.ImageIcon(Icons.appIcon(64))), BorderLayout.WEST);
        String html = "<html><b>" + AppInfo.NAME + "</b> " + AppInfo.VERSION
                + "<br>A graphical front end for cmd.exe and powershell.exe."
                + "<br><br>Java " + System.getProperty("java.version")
                + " &middot; " + System.getProperty("os.name")
                + "<br>Licensed under the MIT license.</html>";
        panel.add(new JLabel(html), BorderLayout.CENTER);
        JOptionPane.showMessageDialog(this, panel, "About " + AppInfo.NAME,
                JOptionPane.INFORMATION_MESSAGE);
    }

    // --- actions ------------------------------------------------------------

    /** Opens a new editor tab targeting the given interpreter. */
    public EditorTab newTab() {
        EditorTab tab = createTab(ShellKind.CMD);
        statusBar.setMessage("New tab created");
        return tab;
    }

    /** Closes the tab at {@code index} without prompting (no files exist yet). */
    public void closeTab(int index) {
        if (index < 0 || index >= editorTabs.tabCount()) {
            return;
        }
        editorTabs.closeTab(index);
        if (editorTabs.tabCount() == 0) {
            createTab(ShellKind.CMD);
        }
        statusBar.setMessage("Tab closed");
    }

    private interface EditorAction {
        void run(EditorTab tab);
    }

    private void withEditor(String what, EditorAction action) {
        EditorTab tab = editorTabs.current();
        if (tab == null) {
            return;
        }
        action.run(tab);
        statusBar.setMessage(what);
    }

    private void zoomEditor(int delta) {
        EditorTab tab = editorTabs.current();
        if (tab == null) {
            return;
        }
        java.awt.Font font = tab.textPane().getFont();
        int size = Math.max(9, Math.min(28, font.getSize() + delta));
        tab.textPane().setFont(font.deriveFont((float) size));
        statusBar.setMessage("Editor font " + size + "pt");
    }

    private void activeTabChanged() {
        EditorTab tab = editorTabs.current();
        if (tab != null) {
            statusBar.setShell(tab.shellKind());
            int[] pos = tab.caretLineColumn();
            statusBar.setCaret(pos[0], pos[1]);
            javax.swing.SwingUtilities.invokeLater(tab.textPane()::requestFocusInWindow);
        }
    }

    // --- helpers ------------------------------------------------------------

    /**
     * @return {@code true} when the process is running with administrator
     *         privileges, determined without shelling out
     */
    private boolean isElevated() {
        try {
            Process p = new ProcessBuilder("cmd.exe", "/c", "net session >nul 2>&1")
                    .redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor() == 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

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

    /** @return the split pane separating editor and console. */
    public JSplitPane splitPane() {
        return splitPane;
    }
}
