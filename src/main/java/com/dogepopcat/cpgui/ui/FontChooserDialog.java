package com.dogepopcat.cpgui.ui;

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
        EDITOR("Editor"),
        CONSOLE("Console");

        private final String label;

        Target(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** The user's choice. */
    public record Result(Target target, Font font) {
    }

    private static final String[] STYLES = {"Plain", "Bold", "Italic", "Bold Italic"};
    private static final String PREVIEW_TEXT =
            "dir /b /a  |  Get-ChildItem -Recurse  |  中文示例 0123456789";

    private final JComboBox<Target> target = new JComboBox<>(Target.values());
    private final JComboBox<String> family = new JComboBox<>();
    private final JComboBox<String> style = new JComboBox<>(STYLES);
    private final SpinnerNumberModel sizeModel = new SpinnerNumberModel(13, 8, 48, 1);
    private final JSpinner size = new JSpinner(sizeModel);
    private final JLabel preview = new JLabel(PREVIEW_TEXT);

    private Result result;

    private FontChooserDialog(Window owner, Target initialTarget, Font initial) {
        super(owner, "Fonts", ModalityType.APPLICATION_MODAL);
        buildUi();
        fillFamilies(initial);
        target.setSelectedItem(initialTarget);
        sizeModel.setValue(initial.getSize());
        style.setSelectedItem(styleName(initial));
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
        form.add(new JLabel("Apply to:"), constraints);
        constraints.gridx = 1;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        form.add(target, constraints);

        constraints.gridy = 1;
        constraints.gridx = 0;
        constraints.gridwidth = 1;
        constraints.fill = GridBagConstraints.NONE;
        form.add(new JLabel("Font:"), constraints);
        constraints.gridx = 1;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        family.setPreferredSize(new Dimension(280, 26));
        form.add(family, constraints);

        constraints.gridy = 2;
        constraints.gridx = 0;
        constraints.gridwidth = 1;
        constraints.fill = GridBagConstraints.NONE;
        form.add(new JLabel("Style:"), constraints);
        constraints.gridx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        form.add(style, constraints);

        constraints.gridy = 3;
        constraints.gridx = 0;
        constraints.fill = GridBagConstraints.NONE;
        form.add(new JLabel("Size:"), constraints);
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

        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
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
        List<String> monospaced = new ArrayList<>();
        List<String> others = new ArrayList<>();
        String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames();
        Arrays.sort(available, String.CASE_INSENSITIVE_ORDER);
        for (String name : available) {
            if (isMonospaced(name)) {
                monospaced.add(name);
            } else {
                others.add(name);
            }
        }
        List<String> ordered = new ArrayList<>(monospaced);
        ordered.addAll(others);
        for (String name : ordered) {
            family.addItem(name);
        }
        family.setSelectedItem(initial.getFamily());
        if (family.getSelectedItem() == null && family.getItemCount() > 0) {
            family.setSelectedIndex(0);
        }
    }

    /** @return {@code true} when the family renders every glyph at one width. */
    private static boolean isMonospaced(String family) {
        try {
            Font font = new Font(family, Font.PLAIN, 12);
            java.awt.FontMetrics metrics = new Canvas().getFontMetrics(font);
            return metrics.charWidth('i') == metrics.charWidth('W')
                    && metrics.charWidth('i') > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String styleName(Font font) {
        int style = font.getStyle();
        if (font.isBold() && font.isItalic()) {
            return STYLES[3];
        }
        if (font.isBold()) {
            return STYLES[1];
        }
        if (font.isItalic()) {
            return STYLES[2];
        }
        return STYLES[0];
    }

    private static int styleValue(String name) {
        if (STYLES[3].equals(name)) {
            return Font.BOLD | Font.ITALIC;
        }
        if (STYLES[1].equals(name)) {
            return Font.BOLD;
        }
        if (STYLES[2].equals(name)) {
            return Font.ITALIC;
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
