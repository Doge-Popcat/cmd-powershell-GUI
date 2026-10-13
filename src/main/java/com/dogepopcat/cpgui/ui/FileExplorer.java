package com.dogepopcat.cpgui.ui;

import com.dogepopcat.cpgui.i18n.Messages;
import com.dogepopcat.cpgui.i18n.Msg;

import javax.swing.BorderFactory;
import javax.swing.JButton;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTree;

import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * A file tree beside the editor, so the folder the commands run in can be seen
 * and changed without leaving the window.
 *
 * <p>Children are read the first time a folder is expanded, which keeps opening
 * the window cheap even when the root holds a deep tree. Double clicking a file
 * opens it in a tab and double clicking a folder makes it the working
 * directory.</p>
 */
public class FileExplorer extends JPanel {

    private static final long serialVersionUID = 1L;

    /** One entry in the tree. */
    private static final class Node extends DefaultMutableTreeNode {

        private static final long serialVersionUID = 1L;
        private boolean loaded;

        Node(File file, boolean directory) {
            super(file, directory);
        }

        File file() {
            return (File) getUserObject();
        }

        boolean isDirectory() {
            return allowsChildren;
        }
    }

    private final DefaultTreeModel model = new DefaultTreeModel(new Node(null, true));
    private final JTree tree = new JTree(model);
    private final JLabel caption = new JLabel();
    private final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));

    private File rootDirectory;
    private Consumer<File> openHandler = file -> { };
    private Consumer<File> workspaceHandler = file -> { };

    public FileExplorer() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder());

        caption.setFont(Theme.mono(12, java.awt.Font.BOLD));
        caption.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        JButton newFile = toolButton(Msg.EXPLORER_NEW_FILE.text(), this::createFile);
        JButton newFolder = toolButton(Msg.EXPLORER_NEW_FOLDER.text(), this::createFolder);
        JButton refresh = toolButton(Msg.EXPLORER_REFRESH.text(), this::refresh);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        buttons.setOpaque(false);
        buttons.add(newFile);
        buttons.add(newFolder);
        buttons.add(refresh);
        header.add(caption, BorderLayout.NORTH);
        header.add(buttons, BorderLayout.CENTER);

        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new Renderer());
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) {
                loadChildren((Node) event.getPath().getLastPathComponent());
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {
                // Nothing to do; the children stay cached.
            }
        });
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() >= 2) {
                    activateSelection();
                }
            }
        });
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowMenu(e);
            }
        });

        JScrollPane scroll = new JScrollPane(tree);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        setPreferredSize(new Dimension(280, 400));

        Messages.addListener(language -> refreshText());
        Theme.addListener(this::applyTheme);
        // The palette must be applied now, not only on a later change: the look
        // and feel defaults are light whatever the chosen theme is.
        applyTheme();
        refreshText();
    }

    private JButton toolButton(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setFont(Theme.mono(11));
        button.setFocusable(false);
        button.setMargin(new java.awt.Insets(2, 6, 2, 6));
        button.setBorder(Theme.raisedBorder());
        button.setBackground(Theme.BUTTON_BACKGROUND);
        button.setForeground(Theme.WINDOW_FOREGROUND);
        button.addActionListener(e -> action.run());
        return button;
    }

    private void refreshText() {
        caption.setText(Msg.EXPLORER_TITLE.text());
        restyleButtons();
        repaint();
    }

    /** Recolours the header buttons for the palette in use. */
    private void restyleButtons() {
        buttons.setBackground(Theme.WINDOW_BACKGROUND);
        if (buttons.getParent() != null) {
            buttons.getParent().setBackground(Theme.WINDOW_BACKGROUND);
        }
        for (java.awt.Component component : buttons.getComponents()) {
            if (component instanceof JButton button) {
                button.setBackground(Theme.BUTTON_BACKGROUND);
                button.setForeground(Theme.WINDOW_FOREGROUND);
                button.setBorder(Theme.raisedBorder());
            }
        }
    }

    void applyTheme() {
        // The buttons were coloured once when they were built and stayed dark in
        // the light palette, which is the grey they appeared as.
        restyleButtons();
        setBackground(Theme.WINDOW_BACKGROUND);
        caption.setForeground(Theme.WINDOW_FOREGROUND);
        tree.setBackground(Theme.isDark() ? Theme.EDITOR_BACKGROUND : java.awt.Color.WHITE);
        tree.setForeground(Theme.EDITOR_FOREGROUND);
        repaint();
    }

    /** Registers the receiver for a file the user opened. */
    public void setOpenHandler(Consumer<File> handler) {
        this.openHandler = handler == null ? file -> { } : handler;
    }

    /** Registers the receiver for a folder chosen as the working directory. */
    public void setWorkspaceHandler(Consumer<File> handler) {
        this.workspaceHandler = handler == null ? file -> { } : handler;
    }

    /**
     * Points the tree at a directory.
     *
     * @param directory the new root, or {@code null} to show nothing
     */
    public void setRootDirectory(File directory) {
        this.rootDirectory = directory;
        Node root = new Node(directory, true);
        model.setRoot(root);
        if (directory != null) {
            loadChildren(root);
            tree.expandPath(new TreePath(root.getPath()));
        }
    }

    /**
     * Re-reads the root and every folder that was open.
     *
     * <p>Rebuilding only the root left open folders showing stale children, so
     * a file created inside one did not appear until it was collapsed and
     * expanded again. The open paths are remembered and reopened.</p>
     */
    public void refresh() {
        List<File> open = new ArrayList<>();
        for (int row = 0; row < tree.getRowCount(); row++) {
            TreePath path = tree.getPathForRow(row);
            if (path != null && tree.isExpanded(path)
                    && path.getLastPathComponent() instanceof Node node
                    && node.file() != null) {
                open.add(node.file());
            }
        }
        setRootDirectory(rootDirectory);
        for (File directory : open) {
            TreePath path = pathOf(directory);
            if (path != null) {
                tree.expandPath(path);
            }
        }
    }

    /** @return the tree path of a directory, or {@code null} when it is not shown. */
    private TreePath pathOf(File directory) {
        Node root = (Node) model.getRoot();
        if (root == null || root.file() == null) {
            return null;
        }
        if (directory.equals(root.file())) {
            return new TreePath(root.getPath());
        }
        java.util.ArrayDeque<Node> queue = new java.util.ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Node node = queue.poll();
            for (int i = 0; i < node.getChildCount(); i++) {
                if (node.getChildAt(i) instanceof Node child && child.file() != null) {
                    if (directory.equals(child.file())) {
                        return new TreePath(child.getPath());
                    }
                    if (child.isDirectory()) {
                        queue.add(child);
                    }
                }
            }
        }
        return null;
    }

    /** @return the directory the tree is showing. */
    public File rootDirectory() {
        return rootDirectory;
    }

    /** @return the selected file, or {@code null}. */
    public File selectedFile() {
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return null;
        }
        return ((Node) path.getLastPathComponent()).file();
    }

    /** Reads a folder's children the first time it is opened. */
    private void loadChildren(Node node) {
        if (node.loaded || !node.isDirectory() || node.file() == null) {
            return;
        }
        node.loaded = true;
        node.removeAllChildren();
        File[] children = node.file().listFiles();
        if (children != null) {
            List<File> sorted = new ArrayList<>(Arrays.asList(children));
            sorted.sort(Comparator.comparing(File::isDirectory).reversed()
                    .thenComparing(file -> file.getName().toLowerCase(java.util.Locale.ROOT)));
            for (File child : sorted) {
                Node childNode = new Node(child, child.isDirectory());
                if (child.isDirectory() && hasVisibleChild(child)) {
                    // A placeholder, but only for a folder that really holds
                    // something; otherwise an empty folder shows a handle that
                    // expands to nothing.
                    childNode.add(new DefaultMutableTreeNode());
                }
                node.add(childNode);
            }
        }
        model.nodeStructureChanged(node);
    }

    /** @return whether a folder holds at least one entry. */
    private static boolean hasVisibleChild(File directory) {
        File[] children = directory.listFiles();
        return children != null && children.length > 0;
    }

    private void activateSelection() {
        File file = selectedFile();
        if (file == null) {
            return;
        }
        if (file.isDirectory()) {
            // Folders expand and collapse on a double click, as in any tree.
            // Making one the working directory is a menu action instead, so a
            // stray double click cannot move the interpreter somewhere else.
            TreePath path = tree.getSelectionPath();
            if (path != null) {
                if (tree.isExpanded(path)) {
                    tree.collapsePath(path);
                } else {
                    tree.expandPath(path);
                }
            }
        } else {
            openHandler.accept(file);
        }
    }

    private void maybeShowMenu(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path != null) {
            tree.setSelectionPath(path);
        }
        JPopupMenu menu = new JPopupMenu();
        File selected = selectedFile();
        if (selected != null) {
            JMenuItem open = new JMenuItem(Msg.EXPLORER_OPEN.text());
            open.addActionListener(a -> activateSelection());
            menu.add(open);
            if (selected.isDirectory()) {
                JMenuItem use = new JMenuItem(Msg.EXPLORER_USE_AS_WORKSPACE.text());
                use.addActionListener(a -> workspaceHandler.accept(selected));
                menu.add(use);
            }
            menu.addSeparator();
        }
        if (selected != null) {
            JMenuItem remove = new JMenuItem(Msg.EXPLORER_DELETE.text());
            remove.addActionListener(a -> deleteSelected());
            menu.add(remove);
            menu.addSeparator();
        }
        JMenuItem file = new JMenuItem(Msg.EXPLORER_NEW_FILE.text());
        file.addActionListener(a -> createFile());
        JMenuItem folder = new JMenuItem(Msg.EXPLORER_NEW_FOLDER.text());
        folder.addActionListener(a -> createFolder());
        JMenuItem reload = new JMenuItem(Msg.EXPLORER_REFRESH.text());
        reload.addActionListener(a -> refresh());
        menu.add(file);
        menu.add(folder);
        menu.add(reload);
        menu.show(tree, e.getX(), e.getY());
    }

    /** The folder new entries are created in: the selection, or the root. */
    private File targetDirectory() {
        File selected = selectedFile();
        if (selected == null) {
            return rootDirectory;
        }
        return selected.isDirectory() ? selected : selected.getParentFile();
    }

    /** Deletes the selected entry, after confirming. */
    private void deleteSelected() {
        File selected = selectedFile();
        if (selected == null || selected.equals(rootDirectory)) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                Msg.EXPLORER_CONFIRM_DELETE.format(selected.getName()),
                Msg.EXPLORER_DELETE.text(), JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        if (!delete(selected)) {
            JOptionPane.showMessageDialog(this,
                    Msg.EXPLORER_DELETE_FAILED.format(selected.getName()),
                    Msg.EXPLORER_TITLE.text(), JOptionPane.ERROR_MESSAGE);
        }
        File parent = selected.getParentFile();
        refresh();
        if (parent != null) {
            selectPathOf(parent);
        }
    }

    /** Deletes a file or a whole folder, deepest entries first. */
    private static boolean delete(File target) {
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!delete(child)) {
                        return false;
                    }
                }
            }
        }
        return target.delete();
    }

    /** Selects a folder in the tree after a rebuild. */
    private void selectPathOf(File directory) {
        Node root = (Node) model.getRoot();
        for (int i = 0; i < root.getChildCount(); i++) {
            if (root.getChildAt(i) instanceof Node node
                    && directory.equals(node.file())) {
                tree.setSelectionPath(new TreePath(node.getPath()));
                return;
            }
        }
    }

    private void createFile() {
        File directory = targetDirectory();
        if (directory == null) {
            return;
        }
        String name = JOptionPane.showInputDialog(this, Msg.EXPLORER_NAME_PROMPT.text(),
                Msg.EXPLORER_NEW_FILE.text(), JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) {
            return;
        }
        create(directory, name.strip(), false);
    }

    private void createFolder() {
        File directory = targetDirectory();
        if (directory == null) {
            return;
        }
        String name = JOptionPane.showInputDialog(this, Msg.EXPLORER_NAME_PROMPT.text(),
                Msg.EXPLORER_NEW_FOLDER.text(), JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) {
            return;
        }
        create(directory, name.strip(), true);
    }

    private void create(File directory, String name, boolean folder) {
        File target = new File(directory, name);
        if (target.exists()) {
            JOptionPane.showMessageDialog(this,
                    Msg.EXPLORER_EXISTS.format(name), Msg.EXPLORER_TITLE.text(),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            if (folder) {
                Files.createDirectories(target.toPath());
            } else {
                File parent = target.getParentFile();
                if (parent != null) {
                    Files.createDirectories(parent.toPath());
                }
                Files.createFile(target.toPath());
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), Msg.EXPLORER_FAILED.text(),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        refresh();
        if (!folder) {
            openHandler.accept(target);
        }
    }

    /** @return the tree, exposed for tests. */
    public JTree tree() {
        return tree;
    }

    /** Renderer that marks folders and greys hidden entries. */
    private static final class Renderer extends DefaultTreeCellRenderer {

        private static final long serialVersionUID = 1L;

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                                                      boolean expanded, boolean leaf, int row,
                                                      boolean focused) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, focused);
            setFont(Theme.mono(12));
            // The row background is set explicitly and the label made opaque,
            // because the tree's own background shows through otherwise and
            // left dark blocks behind every name in the light theme.
            java.awt.Color rowBackground = Theme.isDark()
                    ? Theme.EDITOR_BACKGROUND : java.awt.Color.WHITE;
            setOpaque(true);
            setBackground(selected ? Theme.EDITOR_SELECTION : rowBackground);
            setForeground(selected && Theme.isDark()
                    ? java.awt.Color.WHITE : Theme.EDITOR_FOREGROUND);
            if (value instanceof Node node && node.file() != null) {
                setText(node.file().getName());
                setToolTipText(node.file().getAbsolutePath());
            }
            return this;
        }
    }

    /** @return the smallest sensible width for the panel. */
    @Override
    public Dimension getMinimumSize() {
        return new Dimension(180, 120);
    }
}
