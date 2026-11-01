package com.javamyadmin.ui;

import com.javamyadmin.db.QueryResult;
import com.javamyadmin.db.Sql;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;

import java.util.List;

/** A TableView that displays any QueryResult with dynamic columns. */
public class ResultGrid extends TableView<List<Object>> {

    private List<String> columnNames = List.of();

    public ResultGrid() {
        setPlaceholder(new javafx.scene.control.Label("No rows"));
        getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        getStyleClass().add("result-grid");
    }

    public void show(QueryResult r) {
        columnNames = r.columns();
        getColumns().clear();
        for (int i = 0; i < r.columns().size(); i++) {
            final int idx = i;
            TableColumn<List<Object>, Object> col = new TableColumn<>(r.columns().get(i));
            col.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().get(idx)));
            col.setCellFactory(c -> new ValueCell());
            col.setSortable(false);
            col.setPrefWidth(Math.max(90, Math.min(260, r.columns().get(i).length() * 10 + 40)));
            getColumns().add(col);
        }
        setItems(FXCollections.observableArrayList(r.rows()));
    }

    public void clear() {
        columnNames = List.of();
        getColumns().clear();
        getItems().clear();
    }

    public List<String> columnNames() {
        return columnNames;
    }

    private static final class ValueCell extends TableCell<List<Object>, Object> {
        @Override
        protected void updateItem(Object v, boolean empty) {
            super.updateItem(v, empty);
            getStyleClass().remove("null-cell");
            if (empty) {
                setText(null);
                setTooltip(null);
                return;
            }
            String s = Sql.display(v);
            if (v == null) getStyleClass().add("null-cell");
            String oneLine = s.length() > 200 ? s.substring(0, 200) + "…" : s;
            setText(oneLine.replace('\n', ' '));
            setTooltip(s.length() > 40 ? new Tooltip(s.length() > 2000 ? s.substring(0, 2000) + "…" : s) : null);
        }
    }
}
