package com.dogepopcat.cpgui.ui;

import com.dogepopcat.cpgui.i18n.Msg;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import javax.swing.BoxLayout;
import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Lets the user pick the editor and console font.
 *
 * <p>Monospaced families are listed first because a proportional font makes
 * column aligned command output unreadable, but every installed family is
 * offered so the choice stays the user's. A live preview shows the selection
 * before it is applied.</p>
 */
public final class FontChooserDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** Which surface the chosen font applies to. */
    public enum Target {
        EDITOR,
        CONSOLE;

        /** @return the name of this surface in the current language. */
        public String label() {
            return this == EDITOR ? Msg.FONTS_EDITOR.text() : Msg.FONTS_CONSOLE.text();
        }

        @Override
        public String toString() {
            return label();
        }
    }

    /** The user's choice. */
    public record Result(Target target, Font font) {
    }

    private static final String[] STYLES = {"PLAIN", "BOLD", "ITALIC", "BOLD_ITALIC"};
    private static final String PREVIEW_TEXT =
            "dir /b /a  |  Get-ChildItem -Recurse  |  中文示例 0123456789";

    private final JComboBox<Target> target = new JComboBox<>(Target.values());
    private final JComboBox<String> family = new JComboBox<>();
    private final JComboBox<String> style = new JComboBox<>();
    private final SpinnerNumberModel sizeModel = new SpinnerNumberModel(13, 8, 48, 1);
    private final JSpinner size = new JSpinner(sizeModel);
    private final JLabel preview = new JLabel(PREVIEW_TEXT);

    private Result result;

    private FontChooserDialog(Window owner, Target initialTarget, Font initial) {
        super(owner, Msg.FONTS_TITLE.text(), ModalityType.APPLICATION_MODAL);
        buildUi();
        fillFamilies(initial);
        fillStyles();
        target.setSelectedItem(initialTarget);
        sizeModel.setValue(initial.getSize());
        style.setSelectedIndex(styleIndex(initial));
        updatePreview();
        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * Shows the dialog and waits for the user.
     *
     * @param owner         the window the dialog belongs to
     * @param initialTarget which surface to start on
     * @param initial       the font currently in use
     * @return the chosen font, or empty when the dialog was cancelled
     */
    public static Optional<Result> show(Window owner, Target initialTarget, Font initial) {
        FontChooserDialog dialog = new FontChooserDialog(owner, initialTarget, initial);
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.result);
    }

    private void buildUi() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 6, 12));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(3, 3, 3, 6);
        constraints.anchor = GridBagConstraints.WEST;

        constraints.gridx = 0;
        constraints.gridy = 0;
        form.add(new JLabel(Msg.FONTS_TARGET.text()), constraints);
        constraints.gridx = 1;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        form.add(target, constraints);

        constraints.gridy = 1;
        constraints.gridx = 0;
        constraints.gridwidth = 1;
        constraints.fill = GridBagConstraints.NONE;
        form.add(new JLabel(Msg.FONTS_FAMILY.text()), constraints);
        constraints.gridx = 1;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        family.setPreferredSize(new Dimension(280, 26));
        form.add(family, constraints);

        constraints.gridy = 2;
        constraints.gridx = 0;
        constraints.gridwidth = 1;
        constraints.fill = GridBagConstraints.NONE;
        form.add(new JLabel(Msg.FONTS_STYLE.text()), constraints);
        constraints.gridx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        form.add(style, constraints);

        constraints.gridy = 3;
        constraints.gridx = 0;
        constraints.fill = GridBagConstraints.NONE;
        form.add(new JLabel(Msg.FONTS_SIZE.text()), constraints);
        constraints.gridx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        form.add(size, constraints);

        preview.setOpaque(true);
        preview.setBackground(Theme.EDITOR_BACKGROUND);
        preview.setForeground(Theme.EDITOR_FOREGROUND);
        preview.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BUTTON_BORDER),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        preview.setPreferredSize(new Dimension(520, 46));

        JPanel previewRow = new JPanel(new BorderLayout());
        previewRow.setBorder(BorderFactory.createEmptyBorder(0, 12, 6, 12));
        previewRow.add(preview, BorderLayout.CENTER);

        JButton ok = new JButton(Msg.OK.text());
        JButton cancel = new JButton(Msg.CANCEL.text());
        ok.addActionListener(e -> {
            result = new Result((Target) target.getSelectedItem(), currentFont());
            dispose();
        });
        cancel.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.add(ok);
        buttons.add(cancel);

        // A spacer keeps the form from stretching to the dialog width.
        constraints.gridy = 4;
        constraints.gridx = 0;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.BOTH;
        form.add(Box.createVerticalStrut(0), constraints);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(form);
        content.add(previewRow);
        content.add(Box.createVerticalStrut(2));
        content.add(buttons);

        setContentPane(content);
        getRootPane().setDefaultButton(ok);

        family.addActionListener(e -> updatePreview());
        style.addActionListener(e -> updatePreview());
        size.addChangeListener(e -> updatePreview());
        target.addActionListener(e -> {
            // Switching surface keeps the same family but shows its own size.
            updatePreview();
        });
        javax.swing.KeyStroke escape = javax.swing.KeyStroke.getKeyStroke(
                java.awt.event.KeyEvent.VK_ESCAPE, 0);
        getRootPane().getInputMap(JPanel.WHEN_IN_FOCUSED_WINDOW).put(escape, "close");
        getRootPane().getActionMap().put("close", new javax.swing.AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                dispose();
            }
        });
    }

    /** Adds a spacer so the form does not stretch to the dialog width. */
    private void fillFamilies(Font initial) {
        // Only families that actually draw letters are offered; the symbol and
        // pi fonts Windows ships would turn every command into boxes.
        for (String name : FontSupport.usableFamilies()) {
            family.addItem(name);
        }
        family.setSelectedItem(initial.getFamily());
        if (family.getSelectedItem() == null && family.getItemCount() > 0) {
            family.setSelectedIndex(0);
        }
    }

    /** @return the translated name of a style constant. */
    private static String styleLabel(String key) {
        return Msg.valueOf("FONTS_STYLE_" + key).text();
    }

    /** Fills the style combo with translated names. */
    private void fillStyles() {
        for (String key : STYLES) {
            style.addItem(styleLabel(key));
        }
        style.setSelectedIndex(0);
    }

    /** @return the index into {@link #STYLES} matching a font, or 0. */
    private static int styleIndex(Font font) {
        int style = font.getStyle();
        if (font.isBold() && font.isItalic()) {
            return 3;
        }
        if (font.isBold()) {
            return 1;
        }
        if (font.isItalic()) {
            return 2;
        }
        return 0;
    }

    private static int styleValue(String label) {
        for (int i = 0; i < STYLES.length; i++) {
            if (styleLabel(STYLES[i]).equals(label)) {
                return switch (i) {
                    case 3 -> Font.BOLD | Font.ITALIC;
                    case 1 -> Font.BOLD;
                    case 2 -> Font.ITALIC;
                    default -> Font.PLAIN;
                };
            }
        }
        return Font.PLAIN;
    }

    private Font currentFont() {
        Object selected = family.getSelectedItem();
        String name = selected == null ? Font.MONOSPACED : selected.toString();
        return new Font(name, styleValue((String) style.getSelectedItem()),
                sizeModel.getNumber().intValue());
    }

    private void updatePreview() {
        Font font = currentFont();
        preview.setFont(font);
        preview.setText(PREVIEW_TEXT);
        preview.revalidate();
    }
}
