package com.javamyadmin.ui;

import com.javamyadmin.db.Database;
import com.javamyadmin.db.Sql;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

public class MainWindow {

    /** What a tree node represents. */
    record Node(Kind kind, String db, String name) {
        enum Kind { SERVER, DATABASE, TABLE, VIEW, LOADING }

        @Override
        public String toString() {
            return name;
        }
    }

    private final Stage stage;
    private final Database db;
    private final Runnable onLogout;

    private final TreeView<Node> tree = new TreeView<>();
    private final TabPane tabs = new TabPane();
    private final Label breadcrumb = new Label();
    private final Label status = new Label();

    private final BrowseView browse;
    private final StructureView structure;
    private final SqlView sql;
    private final ExportView export;
    private final ImportView importView;

    private String currentDb;
    private String currentTable;

    public MainWindow(Stage stage, Database db, Runnable onLogout) {
        this.stage = stage;
        this.db = db;
        this.onLogout = onLogout;
        this.browse = new BrowseView(db, this::setStatus);
        this.structure = new StructureView(db, this::setStatus, this::refreshCurrentDb, this::onTableDropped);
        this.sql = new SqlView(db, this::setStatus);
        this.export = new ExportView(db, stage, this::setStatus);
        this.importView = new ImportView(db, stage, this::setStatus, this::afterImport);

        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                tab("Browse", browse),
                tab("Structure", structure),
                tab("SQL", sql),
                tab("Export", export),
                tab("Import", importView));
        tabs.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> loadSelectedTab());

        buildTree();

        breadcrumb.getStyleClass().add("breadcrumb");
        VBox right = new VBox(breadcrumb, tabs);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        SplitPane split = new SplitPane(tree, right);
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(0.22);
        SplitPane.setResizableWithParent(tree, false);

        HBox statusBar = new HBox(status);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setPadding(new Insets(3, 8, 3, 8));

        BorderPane root = new BorderPane(split);
        root.setTop(buildMenu());
        root.setBottom(statusBar);

        Scene scene = new Scene(root, Math.max(stage.getWidth(), 1280), Math.max(stage.getHeight(), 800));
        Look.apply(scene);
        stage.setScene(scene);
        stage.setTitle("FxMyAdmin - " + db.info().label());
        updateContext(null, null);
        Async.run(db::serverVersion, v -> setStatus("Connected to " + db.info().label() + "  (server " + v + ")"));
    }

    public void show() {
        stage.show();
    }

    // ---------- menu ----------

    private MenuBar buildMenu() {
        MenuItem newDb = new MenuItem("New database…");
        newDb.setOnAction(e -> createDatabase());
        MenuItem importItem = new MenuItem("Import SQL file…");
        importItem.setAccelerator(KeyCombination.keyCombination("Shortcut+I"));
        importItem.setOnAction(e -> showImport());
        MenuItem refresh = new MenuItem("Refresh tree");
        refresh.setAccelerator(KeyCombination.keyCombination("F5"));
        refresh.setOnAction(e -> reloadDatabases());
        MenuItem logout = new MenuItem("Disconnect");
        logout.setOnAction(e -> onLogout.run());
        MenuItem quit = new MenuItem("Quit");
        quit.setAccelerator(KeyCombination.keyCombination("Shortcut+Q"));
        quit.setOnAction(e -> stage.close());
        Menu file = new Menu("Server", null, newDb, importItem, refresh, new SeparatorMenuItem(), logout, quit);

        ToggleGroup themeGroup = new ToggleGroup();
        Menu themeMenu = new Menu("Theme");
        for (Look.Mode m : Look.Mode.values()) {
            String label = switch (m) {
                case SYSTEM -> "Follow system";
                case LIGHT -> "Light";
                case DARK -> "Dark";
            };
            RadioMenuItem item = new RadioMenuItem(label);
            item.setToggleGroup(themeGroup);
            item.setSelected(Look.modeProperty().get() == m);
            item.setOnAction(e -> Look.modeProperty().set(m));
            themeMenu.getItems().add(item);
        }
        Look.modeProperty().addListener((o, a, m) ->
                themeGroup.getToggles().get(m.ordinal()).setSelected(true));
        MenuItem toggleDark = new MenuItem("Toggle dark mode");
        toggleDark.setAccelerator(KeyCombination.keyCombination("Shortcut+Shift+L"));
        toggleDark.setOnAction(e -> Look.toggle());
        Menu view = new Menu("View", null, themeMenu, toggleDark);

        MenuItem about = new MenuItem("About FxMyAdmin");
        about.setOnAction(e -> showAbout());
        return new MenuBar(file, view, new Menu("Help", null, about));
    }

    private void showAbout() {
        javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.NONE,
                "", javafx.scene.control.ButtonType.CLOSE);
        a.setTitle("About FxMyAdmin");
        a.initOwner(stage);
        javafx.scene.image.ImageView poster = new javafx.scene.image.ImageView(Look.image("poster.jpg"));
        poster.setFitWidth(720);
        poster.setPreserveRatio(true);
        Label version = new Label("FxMyAdmin 0.1.0  ·  A native MySQL / MariaDB manager for Linux");
        version.getStyleClass().add("app-subtitle");
        a.getDialogPane().setContent(new VBox(12, poster, version));
        Look.apply(a);
        a.showAndWait();
    }

    private static Tab tab(String title, javafx.scene.Node content) {
        return new Tab(title, content);
    }

    // ---------- tree ----------

    private void buildTree() {
        TreeItem<Node> root = new TreeItem<>(new Node(Node.Kind.SERVER, null, db.info().label()));
        root.setExpanded(true);
        tree.setRoot(root);
        tree.setCellFactory(tv -> new NodeCell());
        tree.getSelectionModel().selectedItemProperty().addListener((o, a, item) -> {
            if (item == null) return;
            Node n = item.getValue();
            switch (n.kind()) {
                case DATABASE -> updateContext(n.db(), null);
                case TABLE, VIEW -> updateContext(n.db(), n.name());
                case SERVER -> updateContext(null, null);
                default -> { }
            }
        });
        reloadDatabases();
    }

    private void reloadDatabases() {
        reloadDatabases(() -> { });
    }

    private void reloadDatabases(Runnable after) {
        TreeItem<Node> root = tree.getRoot();
        Async.run(db::databases, dbs -> {
            root.getChildren().setAll(dbs.stream().map(this::databaseItem).toList());
            after.run();
        });
    }

    private void showImport() {
        tabs.getSelectionModel().select(4);
    }

    /** Imports can create databases and tables, so refresh the tree and keep the selection. */
    private void afterImport() {
        String dbName = currentDb;
        reloadDatabases(() -> {
            TreeItem<Node> item = dbName == null ? null : findDb(dbName);
            if (item != null) {
                item.setExpanded(true);
                tree.getSelectionModel().select(item);
            }
        });
    }

    private TreeItem<Node> databaseItem(String name) {
        TreeItem<Node> item = new TreeItem<>(new Node(Node.Kind.DATABASE, name, name));
        item.getChildren().add(loadingItem());
        item.expandedProperty().addListener((o, was, now) -> {
            if (now && isUnloaded(item)) loadTables(item);
        });
        return item;
    }

    private static TreeItem<Node> loadingItem() {
        return new TreeItem<>(new Node(Node.Kind.LOADING, null, "Loading…"));
    }

    private static boolean isUnloaded(TreeItem<Node> item) {
        return item.getChildren().size() == 1
                && item.getChildren().get(0).getValue().kind() == Node.Kind.LOADING;
    }

    private void loadTables(TreeItem<Node> dbItem) {
        String name = dbItem.getValue().db();
        Async.run(() -> List.of(db.tables(name), db.views(name)), lists -> {
            var children = new java.util.ArrayList<TreeItem<Node>>();
            for (String t : lists.get(0)) children.add(new TreeItem<>(new Node(Node.Kind.TABLE, name, t)));
            for (String v : lists.get(1)) children.add(new TreeItem<>(new Node(Node.Kind.VIEW, name, v)));
            dbItem.getChildren().setAll(children);
        });
    }

    private TreeItem<Node> findDb(String name) {
        for (TreeItem<Node> item : tree.getRoot().getChildren()) {
            if (item.getValue().db().equals(name)) return item;
        }
        return null;
    }

    /** Reloads the table list of the database currently in context. */
    private void refreshCurrentDb() {
        if (currentDb == null) return;
        TreeItem<Node> item = findDb(currentDb);
        if (item != null) {
            loadTables(item);
            item.setExpanded(true);
        }
    }

    private void onTableDropped() {
        String dbName = currentDb;
        refreshCurrentDb();
        TreeItem<Node> item = findDb(dbName);
        if (item != null) tree.getSelectionModel().select(item);
    }

    private final class NodeCell extends TreeCell<Node> {
        @Override
        protected void updateItem(Node n, boolean empty) {
            super.updateItem(n, empty);
            getStyleClass().removeAll("node-server", "node-db", "node-table", "node-view", "node-loading");
            if (empty || n == null) {
                setText(null);
                setContextMenu(null);
                return;
            }
            setText(n.name());
            switch (n.kind()) {
                case SERVER -> {
                    getStyleClass().add("node-server");
                    setContextMenu(menu(item("New database…", MainWindow.this::createDatabase),
                            item("Import SQL file…", MainWindow.this::showImport),
                            item("Refresh", MainWindow.this::reloadDatabases)));
                }
                case DATABASE -> {
                    getStyleClass().add("node-db");
                    setContextMenu(menu(
                            item("Create table…", () -> createTable(n.db())),
                            item("Import SQL file…", () -> {
                                tree.getSelectionModel().select(getTreeItem());
                                showImport();
                            }),
                            item("Refresh", () -> { loadTables(getTreeItem()); getTreeItem().setExpanded(true); }),
                            item("Drop database…", () -> dropDatabase(n.db()))));
                }
                case TABLE -> {
                    getStyleClass().add("node-table");
                    setContextMenu(menu(
                            item("Truncate…", () -> structure.truncate(n.db(), n.name())),
                            item("Drop…", () -> structure.drop(n.db(), n.name()))));
                }
                case VIEW -> {
                    getStyleClass().add("node-view");
                    setContextMenu(null);
                }
                case LOADING -> {
                    getStyleClass().add("node-loading");
                    setContextMenu(null);
                }
            }
        }
    }

    private static ContextMenu menu(MenuItem... items) {
        return new ContextMenu(items);
    }

    private static MenuItem item(String text, Runnable action) {
        MenuItem mi = new MenuItem(text);
        mi.setOnAction(e -> action.run());
        return mi;
    }

    // ---------- database actions ----------

    private void createDatabase() {
        Dialogs.prompt("Create database", "Name:").ifPresent(name ->
                Async.run(() -> db.execute(null, "CREATE DATABASE " + Sql.id(name)
                        + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"), r -> {
                    setStatus("Database " + name + " created");
                    reloadDatabases();
                }));
    }

    private void dropDatabase(String name) {
        if (!Dialogs.confirm("Drop database " + name + "?",
                "This permanently deletes the database and ALL of its tables and data.")) return;
        Async.run(() -> db.execute(null, "DROP DATABASE " + Sql.id(name)), r -> {
            setStatus("Database " + name + " dropped");
            tree.getSelectionModel().select(tree.getRoot());
            reloadDatabases();
        });
    }

    private void createTable(String dbName) {
        new CreateTableDialog(dbName).showAndWait().ifPresent(ddl ->
                Async.run(() -> db.execute(dbName, ddl), r -> {
                    setStatus("Table created");
                    TreeItem<Node> item = findDb(dbName);
                    if (item != null) {
                        loadTables(item);
                        item.setExpanded(true);
                    }
                }));
    }

    // ---------- context ----------

    private void updateContext(String dbName, String table) {
        currentDb = dbName;
        currentTable = table;
        if (dbName == null) breadcrumb.setText("Server: " + db.info().label());
        else if (table == null) breadcrumb.setText("Server: " + db.info().label() + "  ›  Database: " + dbName);
        else breadcrumb.setText("Server: " + db.info().label() + "  ›  Database: " + dbName + "  ›  Table: " + table);

        boolean hasTable = table != null;
        tabs.getTabs().get(0).setDisable(!hasTable); // Browse
        tabs.getTabs().get(1).setDisable(!hasTable); // Structure
        tabs.getTabs().get(3).setDisable(dbName == null); // Export

        Tab selected = tabs.getSelectionModel().getSelectedItem();
        if (selected.isDisable()) tabs.getSelectionModel().select(2); // SQL
        else if (hasTable && selected == tabs.getTabs().get(2) && dbName != null) tabs.getSelectionModel().select(0);
        loadSelectedTab();
    }

    private void loadSelectedTab() {
        Tab t = tabs.getSelectionModel().getSelectedItem();
        if (t != null && t.getContent() instanceof ContextView v) {
            if (v.needsTable() && currentTable == null) return;
            v.load(currentDb, currentTable);
        }
    }

    private void setStatus(String text) {
        status.setText(text);
    }
}
