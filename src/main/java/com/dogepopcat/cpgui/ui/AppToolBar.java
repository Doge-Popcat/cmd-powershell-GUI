package com.dogepopcat.cpgui.ui;

import com.dogepopcat.cpgui.i18n.Messages;
import com.dogepopcat.cpgui.i18n.Msg;
import com.dogepopcat.cpgui.shell.ShellKind;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JToggleButton;
import javax.swing.JLabel;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Insets;

/**
 * The main tool bar.
 *
 * <p>Every button is the same size and carries a visible edge, so the bar reads
 * as a row of equal controls instead of a ragged line of words. The width is
 * derived from the longest label plus its keyboard hint, which keeps the buttons
 * even without hard-coding a pixel value that a translation might overflow.</p>
 *
 * <p>Buttons delegate to {@link javax.swing.Action}s owned by
 * {@link MainFrame}, so the same command is reachable from the menu, the tool
 * bar and its shortcut, and the enabled state stays in sync everywhere.</p>
 */
public class AppToolBar extends JToolBar {

    private static final long serialVersionUID = 1L;

    /** Gap between neighbouring buttons, in pixels. */
    private static final int GAP = 4;
    private static final int BUTTON_HEIGHT = 28;
    private static final int MIN_BUTTON_WIDTH = 84;
    private static final int MAX_BUTTON_WIDTH = 150;

    private final JComboBox<ShellKind> interpreter = new JComboBox<>(ShellKind.values());
    private final JLabel interpreterLabel = new JLabel();
    /** Undo and redo read better as arrows than as words in a narrow button. */
    private static final String UNDO_GLYPH = "\u21B6";
    private static final String REDO_GLYPH = "\u21B7";

    private final int buttonWidth;
    private JButton adminButton;
    private JToggleButton singleLineToggle;
    private boolean syncing;
    private boolean elevated;

    public AppToolBar(MainFrame frame) {
        super("Main");
        setFloatable(false);
        setRollover(true);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.tint(Theme.GUTTER_BACKGROUND, 40)),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));

        buttonWidth = measureButtonWidth(frame);

        add(button(frame.newTabAction()));
        add(button(frame.openAction()));
        add(button(frame.saveAction()));
        add(button(frame.undoAction(), UNDO_GLYPH + " " + Msg.TOOLBAR_UNDO.text()));
        add(button(frame.redoAction(), REDO_GLYPH + " " + Msg.TOOLBAR_REDO.text()));
        add(strut());
        add(button(frame.runAction()));
        add(singleLineToggle(frame));
        add(button(frame.stopAction()));
        add(button(frame.restartSessionAction()));
        add(strut());
        add(button(frame.workspaceAction()));
        adminButton = button(frame.adminAction());
        add(adminButton);
        add(strut());

        interpreterLabel.setFont(Theme.mono(11));
        add(interpreterLabel);
        interpreter.setMaximumSize(new Dimension(130, BUTTON_HEIGHT));
        interpreter.setPreferredSize(new Dimension(112, BUTTON_HEIGHT));
        interpreter.addActionListener(e -> {
            if (!syncing) {
                frame.setActiveShellKind((ShellKind) interpreter.getSelectedItem());
            }
        });
        add(interpreter);

        // The console mode lives in the Run menu rather than here: the tool bar
        // already carries ten buttons and the interpreter selector, and the mode
        // selector was pushed off the right hand edge on a default sized window.
        add(Box.createHorizontalGlue());

        Messages.addListener(language -> refreshText());
        Theme.addListener(this::applyTheme);
        // The palette must be applied now, not only on a later change: the look
        // and feel defaults are light whatever the chosen theme is.
        applyTheme();
        refreshText();
    }

    /** Re-applies the palette to every button after a theme change. */
    void applyTheme() {
        setBackground(Theme.WINDOW_BACKGROUND);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.tint(Theme.GUTTER_BACKGROUND, 40)),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        interpreterLabel.setForeground(Theme.WINDOW_FOREGROUND);
        for (Component component : getComponents()) {
            if (component instanceof JButton button) {
                button.setBackground(Theme.BUTTON_BACKGROUND);
                button.setForeground(Theme.WINDOW_FOREGROUND);
                button.setBorder(Theme.raisedBorder());
            }
        }
        setElevated(elevated);
        if (singleLineToggle != null) {
            styleToggle(singleLineToggle);
        }
        repaint();
    }

    /** Re-applies the labels after a language change. */
    private void refreshText() {
        interpreterLabel.setText(Msg.TOOLBAR_INTERPRETER.text());
        interpreterLabel.setForeground(Theme.WINDOW_FOREGROUND);
        interpreter.setToolTipText(Msg.TOOLBAR_INTERPRETER_TIP.text());

        interpreter.setBackground(Theme.isDark() ? Theme.EDITOR_BACKGROUND : java.awt.Color.WHITE);
        interpreter.setForeground(Theme.WINDOW_FOREGROUND);
    }

    /**
     * Re-applies the button fonts. Restyling the component tree resets fonts to
     * the look and feel default, so they have to be put back.
     */
    public void restyleFonts() {
        for (Component component : getComponents()) {
            component.setFont(Theme.mono(11));
        }
        interpreterLabel.setFont(Theme.mono(11));
    }

    /**
     * Works out a width that fits every button label, clamped so one unusually
     * long label cannot stretch the whole bar.
     */
    private int measureButtonWidth(MainFrame frame) {
        FontMetrics metrics = getFontMetrics(Theme.mono(11));
        int widest = 0;
        for (Action action : new Action[]{
                frame.newTabAction(), frame.openAction(), frame.saveAction(),
                frame.runAction(), frame.stopAction(), frame.workspaceAction(),
                frame.adminAction()}) {
            Object name = action.getValue(Action.NAME);
            if (name != null) {
                widest = Math.max(widest, metrics.stringWidth(name.toString()));
            }
        }
        return Math.max(MIN_BUTTON_WIDTH,
                Math.min(MAX_BUTTON_WIDTH, widest + 26));
    }

    /**
     * Builds the single-line mode toggle. It is a toggle button so the current
     * state is visible in the bar rather than buried in a menu tick.
     */
    private JToggleButton singleLineToggle(MainFrame frame) {
        JToggleButton toggle = new JToggleButton(frame.singleLineAction());
        toggle.setFocusable(false);
        toggle.setFont(Theme.mono(11));
        toggle.setMargin(new Insets(0, 0, 0, 0));
        toggle.setPreferredSize(new Dimension(buttonWidth, BUTTON_HEIGHT));
        toggle.setMinimumSize(new Dimension(buttonWidth, BUTTON_HEIGHT));
        toggle.setMaximumSize(new Dimension(buttonWidth, BUTTON_HEIGHT));
        toggle.setBorder(Theme.raisedBorder());
        toggle.setBackground(Theme.BUTTON_BACKGROUND);
        toggle.setForeground(Theme.WINDOW_FOREGROUND);
        // A toggle reads as pressed when its edge is sunken rather than raised,
        // which shows the state without relying on colour alone.
        toggle.getModel().addChangeListener(e -> styleToggle(toggle));
        singleLineToggle = toggle;
        styleToggle(toggle);
        return toggle;
    }

    /** Gives the toggle its raised or sunken edge for the current state. */
    private void styleToggle(JToggleButton toggle) {
        boolean on = toggle.isSelected();
        toggle.setBorder(on
                ? javax.swing.BorderFactory.createCompoundBorder(
                        javax.swing.BorderFactory.createLoweredBevelBorder(),
                        javax.swing.BorderFactory.createEmptyBorder(3, 8, 3, 8))
                : Theme.raisedBorder());
        toggle.setBackground(on ? Theme.BUTTON_ACTIVE : Theme.BUTTON_BACKGROUND);
        toggle.setForeground(on ? Theme.BUTTON_ACTIVE_TEXT : Theme.WINDOW_FOREGROUND);
    }

    private Component strut() {
        return Box.createHorizontalStrut(GAP);
    }

    /** A raised edge with tighter padding than the default, for the tool bar. */
    private static javax.swing.border.Border compactEdge() {
        return javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createRaisedBevelBorder(),
                javax.swing.BorderFactory.createEmptyBorder(2, 5, 2, 5));
    }

    private JButton button(Action action, String label) {
        JButton button = button(action);
        button.setText(label);
        return button;
    }

    private JButton button(Action action) {
        JButton button = new JButton(action);
        button.setFocusable(false);
        button.setFont(Theme.mono(11));
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setPreferredSize(new Dimension(buttonWidth, BUTTON_HEIGHT));
        button.setMinimumSize(new Dimension(buttonWidth, BUTTON_HEIGHT));
        button.setMaximumSize(new Dimension(buttonWidth, BUTTON_HEIGHT));
        // A visible edge is what makes the buttons read as separate controls.
        button.setBorder(compactEdge());
        button.setBackground(Theme.BUTTON_BACKGROUND);
        button.setForeground(Theme.WINDOW_FOREGROUND);
        button.setOpaque(true);
        return button;
    }

    /**
     * Reflects the active tab's interpreter without firing a change event.
     *
     * @param kind the interpreter to select
     */
    public void setInterpreter(ShellKind kind) {
        syncing = true;
        try {
            interpreter.setSelectedItem(kind);
        } finally {
            syncing = false;
        }
    }

    /**
     * Colours the administrator button so the current privilege level is visible
     * at a glance rather than only in the status bar.
     *
     * @param elevated {@code true} when this window has administrator rights
     */
    public void setElevated(boolean elevated) {
        this.elevated = elevated;
        if (adminButton == null) {
            return;
        }
        adminButton.setBackground(elevated ? Theme.BUTTON_ELEVATED : Theme.BUTTON_ADMIN_AVAILABLE);
        adminButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(elevated
                        ? Theme.BUTTON_ELEVATED_BORDER : Theme.BUTTON_ADMIN_BORDER),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        adminButton.setToolTipText(elevated
                ? Msg.TIP_ADMIN_DONE.text()
                : Msg.TIP_ADMIN.text());
        adminButton.repaint();
    }

    /** @return the combo box listing the available interpreters. */
    public JComboBox<ShellKind> interpreterBox() {
        return interpreter;
    }

    /** @return the uniform width every button in this bar uses. */
    public int buttonWidth() {
        return buttonWidth;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        for (Component component : getComponents()) {
            component.setEnabled(enabled);
        }
    }
}
