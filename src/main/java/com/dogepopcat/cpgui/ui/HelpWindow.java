package com.dogepopcat.cpgui.ui;

import com.dogepopcat.cpgui.completion.CommandCatalog;
import com.dogepopcat.cpgui.completion.CompletionItem;
import com.dogepopcat.cpgui.i18n.Msg;
import com.dogepopcat.cpgui.shell.ShellKind;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * An offline syntax reference.
 *
 * <p>It lists every command the interpreter knows, with the description and the
 * switches the completion catalogue holds, and offers two ways to go further:
 * run the interpreter's own {@code /?} or {@code Get-Help} for the selected
 * command, or open the official Microsoft documentation in a browser. The links
 * are kept for both English and Chinese so the reference points at whichever the
 * reader prefers.</p>
 */
public final class HelpWindow extends JDialog {

    private static final long serialVersionUID = 1L;

    /** An official documentation page, with both language variants. */
    private record DocLink(String title, String english, String chinese) {
    }

    private static final List<DocLink> DOC_LINKS = List.of(
            new DocLink("Windows Commands (cmd)",
                    "https://learn.microsoft.com/en-us/windows-server/administration/"
                            + "windows-commands/windows-commands",
                    "https://learn.microsoft.com/zh-cn/windows-server/administration/"
                            + "windows-commands/windows-commands"),
            new DocLink("PowerShell Command Reference",
                    "https://learn.microsoft.com/en-us/powershell/module/",
                    "https://learn.microsoft.com/zh-cn/powershell/module/"),
            new DocLink("PowerShell Language Specification",
                    "https://learn.microsoft.com/en-us/powershell/scripting/lang-spec/"
                            + "chapter-01-overview",
                    "https://learn.microsoft.com/zh-cn/powershell/scripting/lang-spec/"
                            + "chapter-01-overview"),
            new DocLink("about_Operators",
                    "https://learn.microsoft.com/en-us/powershell/module/"
                            + "microsoft.powershell.core/about/about_operators",
                    "https://learn.microsoft.com/zh-cn/powershell/module/"
                            + "microsoft.powershell.core/about/about_operators"),
            new DocLink("about_Automatic_Variables",
                    "https://learn.microsoft.com/en-us/powershell/module/"
                            + "microsoft.powershell.core/about/about_automatic_variables",
                    "https://learn.microsoft.com/zh-cn/powershell/module/"
                            + "microsoft.powershell.core/about/about_automatic_variables"),
            new DocLink("Batch Files Reference",
                    "https://learn.microsoft.com/en-us/windows-server/administration/"
                            + "windows-commands/windows-commands",
                    "https://learn.microsoft.com/zh-cn/windows-server/administration/"
                            + "windows-commands/windows-commands"));

    private final DefaultListModel<CompletionItem> model = new DefaultListModel<>();
    private final JList<CompletionItem> list = new JList<>(model);
    private final JTextField filter = new JTextField();
    private final JEditorPane detail = new JEditorPane();
    private final JTabbedPane tabs = new JTabbedPane();

    private Consumer<String> helpInConsole = command -> { };

    private HelpWindow(Window owner) {
        super(owner, Msg.HELP_TITLE.text(), ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildUi();
        setSize(860, 600);
        setLocationRelativeTo(owner);
    }

    /**
     * Opens the reference window.
     *
     * @param owner  the window it belongs to
     * @param kind   which interpreter to show first
     * @param runner called with a command when the user asks to see the
     *               interpreter's own help
     * @return the window, so the caller can bring it to the front again
     */
    public static HelpWindow open(Window owner, ShellKind kind, Consumer<String> runner) {
        HelpWindow window = new HelpWindow(owner);
        window.helpInConsole = runner == null ? command -> { } : runner;
        window.selectShell(kind);
        window.setVisible(true);
        return window;
    }

    private void buildUi() {
        filter.setFont(Theme.mono(12));
        filter.putClientProperty("JTextField.placeholderText", Msg.HELP_FILTER.text());
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refill();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refill();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refill();
            }
        });

        list.setFont(Theme.mono(12));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new CommandRenderer());
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showDetail();
            }
        });

        detail.setEditable(false);
        detail.setContentType("text/html");
        detail.setFont(Theme.mono(12));
        detail.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JPanel left = new JPanel(new BorderLayout(0, 4));
        left.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 3));
        left.add(filter, BorderLayout.NORTH);
        left.add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout(0, 4));
        right.setBorder(BorderFactory.createEmptyBorder(6, 3, 6, 6));
        right.add(new JScrollPane(detail), BorderLayout.CENTER);

        JButton showHelp = new JButton(Msg.HELP_SHOW_IN_CONSOLE.text());
        showHelp.setFocusable(false);
        showHelp.addActionListener(e -> {
            CompletionItem selected = list.getSelectedValue();
            if (selected != null) {
                helpInConsole.accept(selected.label());
            }
        });
        JButton openDocs = new JButton(Msg.HELP_OPEN_DOCS.text());
        openDocs.setFocusable(false);
        openDocs.addActionListener(e -> openDocumentation());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.add(showHelp);
        buttons.add(openDocs);
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        right.add(buttons, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.32);
        split.setDividerSize(6);
        split.setBorder(BorderFactory.createEmptyBorder());

        tabs.addTab(Msg.HELP_COMMANDS.text(), split);
        tabs.addTab(Msg.HELP_DOCS.text(), createDocsPanel());

        setContentPane(tabs);
        refill();
    }

    /** The tab listing the official documentation pages. */
    private JPanel createDocsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JLabel intro = new JLabel("<html>" + Msg.HELP_DOCS_INTRO.text() + "</html>");
        intro.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        panel.add(intro);

        for (DocLink link : DOC_LINKS) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
            row.add(new JLabel(link.title()));
            JButton english = new JButton(Msg.HELP_DOC_ENGLISH.text());
            english.setFocusable(false);
            english.addActionListener(e -> browse(link.english()));
            JButton chinese = new JButton(Msg.HELP_DOC_CHINESE.text());
            chinese.setFocusable(false);
            chinese.addActionListener(e -> browse(link.chinese()));
            row.add(english);
            row.add(chinese);
            panel.add(row);
        }
        panel.add(javax.swing.Box.createVerticalGlue());
        return panel;
    }

    private void selectShell(ShellKind kind) {
        this.kind = kind == null ? ShellKind.CMD : kind;
        refill();
    }

    private ShellKind kind = ShellKind.CMD;

    /** Rebuilds the list for the current filter. */
    private void refill() {
        String needle = filter.getText() == null ? ""
                : filter.getText().strip().toLowerCase(Locale.ROOT);
        model.clear();
        List<CompletionItem> commands = new ArrayList<>(CommandCatalog.commands(kind));
        commands.sort((a, b) -> a.label().compareToIgnoreCase(b.label()));
        for (CompletionItem item : commands) {
            if (needle.isEmpty() || item.label().toLowerCase(Locale.ROOT).contains(needle)
                    || item.detail().toLowerCase(Locale.ROOT).contains(needle)) {
                model.addElement(item);
            }
        }
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        } else {
            showDetail();
        }
    }

    private void showDetail() {
        CompletionItem item = list.getSelectedValue();
        if (item == null) {
            detail.setText("<html><body style='font-family:monospace'>"
                    + Msg.HELP_NOTHING_SELECTED.text() + "</body></html>");
            return;
        }
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:monospace;font-size:11px'>");
        html.append("<h2 style='margin:0 0 6px 0'>").append(escape(item.label())).append("</h2>");
        html.append("<p>").append(escape(item.detail())).append("</p>");

        List<CompletionItem> parameters = new ArrayList<>(
                CommandCatalog.switchesFor(kind, item.label()));
        parameters.addAll(CommandCatalog.commonParameters(kind));
        if (!parameters.isEmpty()) {
            html.append("<h3>").append(escape(Msg.HELP_PARAMETERS.text())).append("</h3><ul>");
            for (CompletionItem parameter : parameters) {
                html.append("<li><b>").append(escape(parameter.label())).append("</b> &mdash; ")
                        .append(escape(parameter.detail())).append("</li>");
            }
            html.append("</ul>");
        }
        html.append("<p style='color:#888'>").append(escape(Msg.HELP_USAGE_HINT.text()))
                .append("</p>");
        html.append("</body></html>");
        detail.setText(html.toString());
        SwingUtilities.invokeLater(() -> detail.setCaretPosition(0));
    }

    /** Runs the interpreter's own help for the selected command. */
    private void openDocumentation() {
        browse(kind == ShellKind.CMD ? DOC_LINKS.get(0).english() : DOC_LINKS.get(1).english());
    }

    private void browse(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(
                            java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
                return;
            }
        } catch (Exception ignored) {
            // Fall through to the message below.
        }
        detail.setText("<html><body style='font-family:monospace'>" + escape(url)
                + "</body></html>");
    }

    /** Draws a command as its name, with the description greyed beside it. */
    private static final class CommandRenderer extends javax.swing.DefaultListCellRenderer {

        private static final long serialVersionUID = 1L;

        @Override
        public java.awt.Component getListCellRendererComponent(JList<?> list, Object value,
                                                              int index, boolean selected,
                                                              boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, focused);
            setFont(Theme.mono(12));
            setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
            if (value instanceof CompletionItem item) {
                String description = item.detail() == null ? "" : item.detail();
                if (description.length() > 46) {
                    description = description.substring(0, 45) + "\u2026";
                }
                setText(item.label() + "   " + description);
                setToolTipText(item.detail());
            }
            setBackground(selected ? Theme.EDITOR_SELECTION
                    : (Theme.isDark() ? Theme.EDITOR_BACKGROUND : java.awt.Color.WHITE));
            setForeground(selected ? java.awt.Color.WHITE : Theme.EDITOR_FOREGROUND);
            return this;
        }
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** @return the command list, exposed for tests. */
    public JList<CompletionItem> commandList() {
        return list;
    }

    /** @return the filter field, exposed for tests. */
    public JTextField filterField() {
        return filter;
    }

    /** @return the detail pane, exposed for tests. */
    public JEditorPane detailPane() {
        return detail;
    }

    /** Applies a font to the reference, so it matches the editor. */
    @Override
    public void setFont(Font font) {
        super.setFont(font);
        if (list != null) {
            list.setFont(font);
            detail.setFont(font);
            filter.setFont(font);
        }
    }
}
