package com.javamyadmin.ui;

import com.javamyadmin.db.ColumnInfo;
import com.javamyadmin.db.Database;
import com.javamyadmin.db.QueryResult;
import com.javamyadmin.db.Sql;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Column list, CREATE TABLE text, and schema actions for one table. */
public class StructureView extends BorderPane implements ContextView {

    private final Database db;
    private final Consumer<String> status;
    private final Runnable onTablesChanged;
    private final Runnable onTableDropped;

    private final ResultGrid grid = new ResultGrid();
    private final TextArea ddl = new TextArea();

    private String dbName;
    private String table;
    private List<ColumnInfo> columns = List.of();

    public StructureView(Database db, Consumer<String> status, Runnable onTablesChanged, Runnable onTableDropped) {
        this.db = db;
        this.status = status;
        this.onTablesChanged = onTablesChanged;
        this.onTableDropped = onTableDropped;

        Button refresh = new Button("⟳ Refresh");
        refresh.setOnAction(e -> load(dbName, table));
        Button add = new Button("+ Add column");
        add.setOnAction(e -> addColumn());
        Button dropCol = new Button("Drop column");
        dropCol.disableProperty().bind(grid.getSelectionModel().selectedItemProperty().isNull());
        dropCol.setOnAction(e -> dropColumn());
        Button truncate = new Button("Truncate table");
        truncate.setOnAction(e -> truncate(dbName, table));
        Button drop = new Button("Drop table");
        drop.getStyleClass().add("danger");
        drop.setOnAction(e -> drop(dbName, table));

        FlowPane bar = new FlowPane(6, 6, refresh, add, dropCol, truncate, drop);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6));
        bar.getStyleClass().add("toolbar");

        ddl.setEditable(false);
        ddl.getStyleClass().add("code");

        SplitPane split = new SplitPane(grid, ddl);
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.6);

        setTop(bar);
        setCenter(split);
    }

    @Override
    public boolean needsTable() {
        return true;
    }

    @Override
    public void load(String dbName, String table) {
        this.dbName = dbName;
        this.table = table;
        Async.run(() -> new Object[]{db.columns(dbName, table), db.createStatement(dbName, table)}, res -> {
            if (!table.equals(this.table)) return;
            @SuppressWarnings("unchecked")
            List<ColumnInfo> cols = (List<ColumnInfo>) res[0];
            columns = cols;
            List<List<Object>> rows = new ArrayList<>();
            for (ColumnInfo c : cols) {
                rows.add(java.util.Arrays.asList(c.name(), c.type(), c.nullable() ? "YES" : "NO",
                        c.key(), c.defaultValue(), c.extra(), c.comment()));
            }
            grid.show(new QueryResult(List.of("Field", "Type", "Null", "Key", "Default", "Extra", "Comment"),
                    rows, -1, 0));
            ddl.setText((String) res[1]);
        });
    }

    private void addColumn() {
        String d = dbName, t = table;
        new AddColumnDialog(t, columns).showAndWait().ifPresent(def ->
                Async.run(() -> db.execute(d, "ALTER TABLE " + Sql.id(d, t) + " ADD COLUMN " + def), r -> {
                    status.accept("Column added");
                    load(d, t);
                }));
    }

    private void dropColumn() {
        List<Object> row = grid.getSelectionModel().getSelectedItem();
        if (row == null) return;
        String col = (String) row.get(0);
        String d = dbName, t = table;
        if (!Dialogs.confirm("Drop column " + col + "?", "All data in this column will be lost.")) return;
        Async.run(() -> db.execute(d, "ALTER TABLE " + Sql.id(d, t) + " DROP COLUMN " + Sql.id(col)), r -> {
            status.accept("Column " + col + " dropped");
            load(d, t);
        });
    }

    public void truncate(String d, String t) {
        if (!Dialogs.confirm("Truncate " + t + "?", "This deletes ALL rows in the table.")) return;
        Async.run(() -> db.execute(d, "TRUNCATE TABLE " + Sql.id(d, t)),
                r -> status.accept("Table " + t + " truncated"));
    }

    public void drop(String d, String t) {
        if (!Dialogs.confirm("Drop table " + t + "?", "This permanently deletes the table and its data.")) return;
        Async.run(() -> db.execute(d, "DROP TABLE " + Sql.id(d, t)), r -> {
            status.accept("Table " + t + " dropped");
            if (t.equals(table)) onTableDropped.run();
            else onTablesChanged.run();
        });
    }
}
