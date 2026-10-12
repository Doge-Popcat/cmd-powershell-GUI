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
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
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
        repaint();
    }

    private void applyTheme() {
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

    /** Re-reads the root and the expanded folders. */
    public void refresh() {
        setRootDirectory(rootDirectory);
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
                if (child.isDirectory()) {
                    // A placeholder so the handle is drawn before loading.
                    childNode.add(new DefaultMutableTreeNode());
                }
                node.add(childNode);
            }
        }
        model.nodeStructureChanged(node);
    }

    private void activateSelection() {
        File file = selectedFile();
        if (file == null) {
            return;
        }
        if (file.isDirectory()) {
            workspaceHandler.accept(file);
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
            setBackground(selected ? Theme.EDITOR_SELECTION
                    : (Theme.isDark() ? Theme.EDITOR_BACKGROUND : java.awt.Color.WHITE));
            setForeground(Theme.EDITOR_FOREGROUND);
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
