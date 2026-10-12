package com.dogepopcat.cpgui.ui;

import com.dogepopcat.cpgui.analysis.DangerAnalyzer.Finding;
import com.dogepopcat.cpgui.i18n.Messages;
import com.dogepopcat.cpgui.i18n.Msg;
import com.dogepopcat.cpgui.analysis.DangerAnalyzer.Severity;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Lists the diagnostics of the active script.
 *
 * <p>Double clicking a row asks the frame to move the caret to the offending
 * line, which is the behaviour users expect from an IDE's problems view.</p>
 */
public class ProblemsPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final String[] COLUMN_KEYS = {"", "COLUMN_LINE", "COLUMN_PROBLEM",
            "COLUMN_SUGGESTION"};

    private final Model model = new Model();
    private final JTable table = new JTable(model);
    private final JScrollPane scroll;
    private final JLabel summary = new JLabel();
    private IntConsumer lineNavigator = line -> { };

    public ProblemsPanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder());

        table.setFont(Theme.mono(12));
        table.setRowHeight(20);
        table.setShowGrid(false);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        TableColumn severityColumn = table.getColumnModel().getColumn(0);
        severityColumn.setMaxWidth(28);
        severityColumn.setPreferredWidth(28);
        severityColumn.setCellRenderer(new SeverityRenderer());
        table.getColumnModel().getColumn(1).setMaxWidth(60);
        table.getColumnModel().getColumn(1).setPreferredWidth(60);
        table.getColumnModel().getColumn(2).setPreferredWidth(330);
        table.getColumnModel().getColumn(3).setPreferredWidth(330);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() >= 2) {
                    navigateToSelection();
                }
            }
        });

        summary.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));

        scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);
        add(summary, BorderLayout.SOUTH);

        Messages.addListener(language -> refreshText());
        Theme.addListener(this::applyTheme);
        applyTheme();
        refreshText();
    }

    /** Re-applies the column headers and summary after a language change. */
    private void refreshText() {
        for (int i = 0; i < COLUMN_KEYS.length; i++) {
            String key = COLUMN_KEYS[i];
            table.getColumnModel().getColumn(i).setHeaderValue(
                    key.isEmpty() ? "" : Msg.valueOf(key).text());
        }
        table.getTableHeader().repaint();
        setFindings(model.findings());
    }

    /** Registers the callback that moves the editor caret to a line. */
    public void setLineNavigator(IntConsumer navigator) {
        this.lineNavigator = navigator == null ? line -> { } : navigator;
    }

    private void navigateToSelection() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= model.getRowCount()) {
            return;
        }
        lineNavigator.accept(model.findingAt(table.convertRowIndexToModel(row)).lineIndex());
    }

    /** Applies the palette. Called on construction and whenever it changes. */
    void applyTheme() {
        setBackground(Theme.WINDOW_BACKGROUND);
        summary.setForeground(Theme.WINDOW_FOREGROUND);
        table.setBackground(Theme.isDark() ? Theme.EDITOR_BACKGROUND : java.awt.Color.WHITE);
        table.setForeground(Theme.WINDOW_FOREGROUND);
        table.setGridColor(Theme.isDark() ? Theme.tint(Theme.WINDOW_BACKGROUND, 24)
                : new java.awt.Color(0xD0, 0xD4, 0xDA));
        table.getTableHeader().setBackground(Theme.isDark()
                ? Theme.tint(Theme.WINDOW_BACKGROUND, 16) : new java.awt.Color(0xE8, 0xEA, 0xED));
        table.getTableHeader().setForeground(Theme.WINDOW_FOREGROUND);
        table.getTableHeader().setDefaultRenderer(new HeaderRenderer());
        scroll.getViewport().setBackground(table.getBackground());
        scroll.setBackground(Theme.WINDOW_BACKGROUND);
        repaint();
    }

    /**
     * Draws a header cell in the palette colours.
     *
     * <p>The Windows header renderer paints a themed light gradient of its own,
     * so the header is drawn here instead.</p>
     */
    private static final class HeaderRenderer extends javax.swing.table.DefaultTableCellRenderer {

        private static final long serialVersionUID = 1L;

        @Override
        public java.awt.Component getTableCellRendererComponent(JTable table, Object value,
                                                               boolean selected, boolean focused,
                                                               int row, int column) {
            javax.swing.JLabel label = (javax.swing.JLabel) super.getTableCellRendererComponent(
                    table, value, selected, focused, row, column);
            label.setOpaque(true);
            label.setBackground(table.getTableHeader().getBackground());
            label.setForeground(table.getTableHeader().getForeground());
            label.setFont(Theme.mono(12, java.awt.Font.BOLD));
            label.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                    javax.swing.BorderFactory.createMatteBorder(0, 0, 1, 1,
                            Theme.isDark() ? Theme.tint(Theme.WINDOW_BACKGROUND, 40)
                                    : new java.awt.Color(0xD0, 0xD4, 0xDA)),
                    javax.swing.BorderFactory.createEmptyBorder(2, 6, 2, 6)));
            return label;
        }
    }

    /** Replaces the displayed findings. */
    public void setFindings(List<Finding> findings) {
        model.setFindings(findings);
        summary.setText(summarise(findings));
    }

    /** @return the findings currently displayed. */
    public List<Finding> findings() {
        return model.findings();
    }

    private String summarise(List<Finding> findings) {
        if (findings.isEmpty()) {
            return Msg.NO_PROBLEMS.text();
        }
        int errors = 0;
        int warnings = 0;
        int infos = 0;
        for (Finding finding : findings) {
            switch (finding.severity()) {
                case DANGER -> errors++;
                case WARNING -> warnings++;
                default -> infos++;
            }
        }
        StringBuilder text = new StringBuilder(findings.size() == 1
                ? Msg.PROBLEMS_COUNT_ONE.format(errors, warnings)
                : Msg.PROBLEMS_COUNT.format(findings.size(), errors, warnings));
        if (infos > 0) {
            text.append(Msg.PROBLEMS_NOTED.format(infos));
        }
        return text.toString();
    }

    /** @return the table backing the view, exposed for tests. */
    public JTable table() {
        return table;
    }

    /** Severity indicator: a filled circle in the severity's colour. */
    private static final class SeverityRenderer extends DefaultTableCellRenderer {

        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean selected, boolean focused,
                                                       int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(
                    table, value, selected, focused, row, column);
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setFont(Theme.mono(12));
            String text = String.valueOf(value);
            label.setText(text);
            label.setForeground(colourFor(text));
            return label;
        }

        private static Color colourFor(String marker) {
            return switch (marker) {
                case "!!" -> Theme.ERROR;
                case "!" -> Theme.WARNING;
                default -> Theme.CONSOLE_INFO;
            };
        }
    }

    /** Table model holding the findings of the active tab. */
    private static final class Model extends AbstractTableModel {

        private static final long serialVersionUID = 1L;
        private final List<Finding> findings = new ArrayList<>();

        void setFindings(List<Finding> updated) {
            findings.clear();
            findings.addAll(updated);
            fireTableDataChanged();
        }

        Finding findingAt(int row) {
            return findings.get(row);
        }

        List<Finding> findings() {
            return List.copyOf(findings);
        }

        @Override
        public int getRowCount() {
            return findings.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMN_KEYS.length;
        }

        @Override
        public String getColumnName(int column) {
            String key = COLUMN_KEYS[column];
            return key.isEmpty() ? "" : Msg.valueOf(key).text();
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Object getValueAt(int row, int column) {
            Finding finding = findings.get(row);
            return switch (column) {
                case 0 -> marker(finding.severity());
                case 1 -> finding.lineIndex() + 1;
                case 2 -> finding.message();
                default -> finding.advice();
            };
        }

        private static String marker(Severity severity) {
            return switch (severity) {
                case DANGER -> "!!";
                case WARNING -> "!";
                default -> "i";
            };
        }
    }
}
