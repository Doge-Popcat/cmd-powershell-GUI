package com.dogepopcat.cpgui.ui;

import com.dogepopcat.cpgui.i18n.Messages;
import com.dogepopcat.cpgui.i18n.Msg;
import com.dogepopcat.cpgui.shell.ShellKind;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;

/**
 * Bottom status bar showing the current message, workspace, shell, elevation
 * state and caret position.
 */
public class StatusBar extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel message = new JLabel();
    private final JLabel hint = new JLabel();
    private final JLabel workspace = new JLabel();
    private final JLabel shell = new JLabel();
    private final JLabel elevation = new JLabel();
    private final JLabel caret = new JLabel();

    private String workspacePath;
    private com.dogepopcat.cpgui.shell.ShellKind shellKind;
    private boolean elevated;
    private int caretLine = 1;
    private int caretColumn = 1;

    public StatusBar() {
        super(new BorderLayout());
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.tint(Theme.GUTTER_BACKGROUND, 40)),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        left.add(message);
        add(left, BorderLayout.WEST);

        // The command signature lives here rather than in a floating window so
        // it can never cover the input method candidate list.
        hint.setFont(Theme.mono(12));
        hint.setForeground(Theme.CONSOLE_INFO);
        hint.setHorizontalAlignment(SwingConstants.CENTER);
        add(hint, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        right.add(workspace);
        right.add(separator());
        right.add(shell);
        right.add(separator());
        right.add(elevation);
        right.add(separator());
        right.add(caret);
        add(right, BorderLayout.EAST);

        Messages.addListener(language -> refreshText());
        Theme.addListener(this::applyTheme);
        refreshText();
    }

    /** Re-applies the palette after a theme change. */
    private void applyTheme() {
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.tint(Theme.GUTTER_BACKGROUND, 40)),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        setBackground(Theme.WINDOW_BACKGROUND);
        message.setForeground(Theme.WINDOW_FOREGROUND);
        hint.setForeground(Theme.CONSOLE_INFO);
        workspace.setForeground(Theme.WINDOW_FOREGROUND);
        shell.setForeground(Theme.WINDOW_FOREGROUND);
        caret.setForeground(Theme.WINDOW_FOREGROUND);
        setElevated(elevated);
        refreshText();
        repaint();
    }

    /** Re-applies every label after a language change. */
    private void refreshText() {
        if (message.getText().isEmpty() || message.getText().equals(
                Msg.STATUS_READY.text())) {
            message.setText(Msg.STATUS_READY.text());
        }
        setWorkspacePath(workspacePath);
        setShell(shellKind);
        setElevated(elevated);
        setCaret(caretLine, caretColumn);
    }

    /**
     * Shows the signature of the command the caret is inside.
     *
     * @param text the text to show, or {@code null} to clear the hint
     */
    public void setHint(String text) {
        hint.setText(text == null ? "" : text);
        hint.setToolTipText(text == null || text.isEmpty() ? null : text);
    }

    private JSeparator separator() {
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 14));
        return sep;
    }

    /** Sets the transient message on the left hand side. */
    public void setMessage(String text) {
        message.setText(text == null || text.isEmpty() ? Msg.STATUS_READY.text() : text);
    }

    /** Shows the directory new shells start in. */
    public void setWorkspace(File dir) {
        setWorkspacePath(dir == null ? null : dir.getAbsolutePath());
    }

    private void setWorkspacePath(String path) {
        this.workspacePath = path;
        workspace.setText("\u25b8 " + (path == null ? Msg.STATUS_SHELL_NONE.text() : path));
        workspace.setToolTipText(path == null ? null : Msg.STATUS_LOCATION_TIP.format(path));
    }

    /** Shows which interpreter the active tab targets. */
    public void setShell(ShellKind shellKind) {
        this.shellKind = shellKind;
        shell.setText(shellKind == null ? Msg.STATUS_SHELL_NONE.text() : shellKind.displayName());
        shell.setToolTipText(shellKind == null ? null
                : Msg.STATUS_INTERPRETER_TIP.format(shellKind.description()));
    }

    /** Shows whether the process is elevated. */
    public void setElevated(boolean elevated) {
        this.elevated = elevated;
        elevation.setText(elevated ? Msg.STATUS_ADMIN_USER.text() : Msg.STATUS_STANDARD_USER.text());
        elevation.setForeground(elevated ? Theme.ERROR : null);
        elevation.setToolTipText(elevated
                ? Msg.STATUS_ELEVATED_TIP.text()
                : Msg.STATUS_STANDARD_TIP.text());
    }

    /** Shows the caret's line and column, one based. */
    public void setCaret(int line, int column) {
        this.caretLine = line + 1;
        this.caretColumn = column + 1;
        caret.setText(Msg.STATUS_CARET.format(caretLine, caretColumn));
    }

    /** Sets the colour of the message label, for warnings and errors. */
    public void setMessageColor(Color color) {
        message.setForeground(color);
    }
}
