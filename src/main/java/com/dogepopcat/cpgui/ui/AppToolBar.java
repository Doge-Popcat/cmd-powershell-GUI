package com.dogepopcat.cpgui.ui;

import com.dogepopcat.cpgui.i18n.Messages;
import com.dogepopcat.cpgui.i18n.Msg;
import com.dogepopcat.cpgui.shell.ShellKind;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
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
    private final int buttonWidth;
    private JButton adminButton;
    private boolean syncing;

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
        add(strut());
        add(button(frame.runAction()));
        add(button(frame.stopAction()));
        add(strut());
        add(button(frame.workspaceAction()));
        adminButton = button(frame.adminAction());
        add(adminButton);
        add(strut());

        add(Box.createHorizontalGlue());
        interpreterLabel.setFont(Theme.mono(12));
        add(interpreterLabel);
        interpreter.setMaximumSize(new Dimension(160, BUTTON_HEIGHT));
        interpreter.setPreferredSize(new Dimension(150, BUTTON_HEIGHT));
        interpreter.addActionListener(e -> {
            if (!syncing) {
                frame.setActiveShellKind((ShellKind) interpreter.getSelectedItem());
            }
        });
        add(interpreter);
        Messages.addListener(language -> refreshText());
        refreshText();
    }

    /** Re-applies the labels after a language change. */
    private void refreshText() {
        interpreterLabel.setText(Msg.TOOLBAR_INTERPRETER.text());
        interpreter.setToolTipText(Msg.TOOLBAR_INTERPRETER_TIP.text());
    }

    /**
     * Works out a width that fits every button label, clamped so one unusually
     * long label cannot stretch the whole bar.
     */
    private int measureButtonWidth(MainFrame frame) {
        FontMetrics metrics = getFontMetrics(Theme.mono(12));
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

    private Component strut() {
        return Box.createHorizontalStrut(GAP);
    }

    private JButton button(Action action) {
        JButton button = new JButton(action);
        button.setFocusable(false);
        button.setFont(Theme.mono(12));
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setPreferredSize(new Dimension(buttonWidth, BUTTON_HEIGHT));
        button.setMinimumSize(new Dimension(buttonWidth, BUTTON_HEIGHT));
        button.setMaximumSize(new Dimension(buttonWidth, BUTTON_HEIGHT));
        // A visible edge is what makes the buttons read as separate controls.
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BUTTON_BORDER),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        button.setBackground(Theme.BUTTON_BACKGROUND);
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
        if (adminButton == null) {
            return;
        }
        adminButton.setBackground(elevated ? Theme.BUTTON_ELEVATED : Theme.BUTTON_ADMIN_AVAILABLE);
        adminButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(elevated
                        ? Theme.BUTTON_ELEVATED_BORDER : Theme.BUTTON_ADMIN_BORDER),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
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
