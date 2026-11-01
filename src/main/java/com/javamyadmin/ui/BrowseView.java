package com.javamyadmin.ui;

import com.javamyadmin.db.ColumnInfo;
import com.javamyadmin.db.Database;
import com.javamyadmin.db.QueryResult;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableRow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Paged table data with insert / edit / delete. */
public class BrowseView extends BorderPane implements ContextView {

    private static final String NO_ORDER = "(none)";

    private final Database db;
    private final Consumer<String> status;
    private final ResultGrid grid = new ResultGrid();

    private final Button prev = new Button("◀");
    private final Button next = new Button("▶");
    private final Label pageLabel = new Label();
    private final ComboBox<Integer> pageSize = new ComboBox<>(FXCollections.observableArrayList(25, 50, 100, 250, 500));
    private final ComboBox<String> orderBy = new ComboBox<>();
    private final CheckBox desc = new CheckBox("DESC");
    private final Button edit = new Button("Edit");
    private final Button delete = new Button("Delete");

    private String dbName;
    private String table;
    private List<ColumnInfo> columns = List.of();
    private long total;
    private long offset;
    private boolean loadingOrder;

    public BrowseView(Database db, Consumer<String> status) {
        this.db = db;
        this.status = status;

        Button refresh = new Button("⟳ Refresh");
        refresh.setOnAction(e -> reload());
        Button insert = new Button("+ Insert");
        insert.setOnAction(e -> insertRow());
        edit.setOnAction(e -> editSelected());
        delete.setOnAction(e -> deleteSelected());
        edit.disableProperty().bind(grid.getSelectionModel().selectedItemProperty().isNull());
        delete.disableProperty().bind(grid.getSelectionModel().selectedItemProperty().isNull());

        pageSize.setValue(50);
        pageSize.setOnAction(e -> { offset = 0; reload(); });
        prev.setOnAction(e -> { offset = Math.max(0, offset - pageSize.getValue()); reload(); });
        next.setOnAction(e -> { offset += pageSize.getValue(); reload(); });
        orderBy.setOnAction(e -> { if (!loadingOrder) { offset = 0; reload(); } });
        desc.setOnAction(e -> { offset = 0; reload(); });

        FlowPane bar = new FlowPane(6, 6, refresh, insert, edit, delete, new Separator(javafx.geometry.Orientation.VERTICAL),
                new Label("Order by"), orderBy, desc,
                new Label("Rows"), pageSize, prev, pageLabel, next);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6));
        bar.getStyleClass().add("toolbar");

        grid.setRowFactory(tv -> {
            TableRow<List<Object>> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !row.isEmpty()) editSelected();
            });
            return row;
        });
        grid.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE) deleteSelected();
            else if (e.getCode() == KeyCode.ENTER) editSelected();
        });

        setTop(bar);
        setCenter(grid);
    }

    @Override
    public boolean needsTable() {
        return true;
    }

    @Override
    public void load(String dbName, String table) {
        boolean changed = !table.equals(this.table) || !dbName.equals(this.dbName);
        this.dbName = dbName;
        this.table = table;
        if (changed) {
            offset = 0;
            grid.clear();
            Async.run(() -> db.columns(dbName, table), cols -> {
                columns = cols;
                loadingOrder = true;
                List<String> names = new ArrayList<>();
                names.add(NO_ORDER);
                cols.forEach(c -> names.add(c.name()));
                orderBy.getItems().setAll(names);
                orderBy.setValue(cols.stream().filter(ColumnInfo::isPrimaryKey)
                        .map(ColumnInfo::name).findFirst().orElse(NO_ORDER));
                loadingOrder = false;
                reload();
            });
        } else {
            reload();
        }
    }

    private void reload() {
        if (table == null) return;
        String d = dbName, t = table;
        String order = NO_ORDER.equals(orderBy.getValue()) ? null : orderBy.getValue();
        boolean isDesc = desc.isSelected();
        long off = offset;
        int limit = pageSize.getValue();
        Async.run(() -> new Object[]{db.countRows(d, t), db.page(d, t, order, isDesc, off, limit)}, res -> {
            if (!t.equals(table)) return; // user moved on
            total = (Long) res[0];
            QueryResult r = (QueryResult) res[1];
            grid.show(r);
            long from = r.rows().isEmpty() ? 0 : off + 1;
            pageLabel.setText(from + "–" + (off + r.rows().size()) + " of " + total);
            prev.setDisable(off == 0);
            next.setDisable(off + limit >= total);
            status.accept("Showing rows " + from + "–" + (off + r.rows().size()) + " (" + total
                    + " total, query took " + r.millis() + " ms)");
        });
    }

    // ---------- editing ----------

    /** Columns that identify a row: the primary key, or every column when there is none. */
    private Map<String, Object> identify(List<Object> row) {
        List<String> names = grid.columnNames();
        Map<String, Object> where = new LinkedHashMap<>();
        boolean hasPk = columns.stream().anyMatch(ColumnInfo::isPrimaryKey);
        for (int i = 0; i < names.size(); i++) {
            String n = names.get(i);
            boolean isPk = columns.stream().anyMatch(c -> c.name().equals(n) && c.isPrimaryKey());
            if (!hasPk || isPk) where.put(n, row.get(i));
        }
        return where;
    }

    private void insertRow() {
        String d = dbName, t = table;
        new RowEditorDialog(t, columns, null, null).showAndWait().ifPresent(values ->
                Async.run(() -> { db.insert(d, t, values); return null; }, x -> {
                    status.accept("1 row inserted");
                    reload();
                }));
    }

    private void editSelected() {
        List<Object> row = grid.getSelectionModel().getSelectedItem();
        if (row == null) return;
        String d = dbName, t = table;
        Map<String, Object> where = identify(row);
        new RowEditorDialog(t, columns, grid.columnNames(), row).showAndWait().ifPresent(values -> {
            if (values.isEmpty()) return;
            Async.run(() -> db.update(d, t, values, where), n -> {
                status.accept(n + " row(s) updated");
                reload();
            });
        });
    }

    private void deleteSelected() {
        List<List<Object>> rows = new ArrayList<>(grid.getSelectionModel().getSelectedItems());
        if (rows.isEmpty()) return;
        if (!Dialogs.confirm("Delete " + rows.size() + " row(s)?", "This cannot be undone.")) return;
        String d = dbName, t = table;
        List<Map<String, Object>> keys = rows.stream().map(this::identify).toList();
        Async.run(() -> {
            int n = 0;
            for (Map<String, Object> k : keys) n += db.delete(d, t, k);
            return n;
        }, n -> {
            status.accept(n + " row(s) deleted");
            reload();
        });
    }
}
