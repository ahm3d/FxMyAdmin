package com.javamyadmin.ui;

import com.javamyadmin.db.ColumnInfo;
import com.javamyadmin.db.Sql;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Form for inserting a row ({@code row == null}) or editing one.
 * Returns only the columns to write: for edits, just the changed ones.
 */
public class RowEditorDialog extends Dialog<Map<String, Object>> {

    private record Field(ColumnInfo col, TextInputControl input, CheckBox isNull, String original,
                         boolean wasNull, boolean readOnly) {}

    public RowEditorDialog(String table, List<ColumnInfo> columns, List<String> rowColumns, List<Object> row) {
        boolean insert = row == null;
        setTitle(insert ? "Insert row" : "Edit row");
        Look.apply(this);
        setHeaderText((insert ? "Insert into " : "Edit row in ") + table);
        setResizable(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        ColumnConstraints grow = new ColumnConstraints();
        grow.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(new ColumnConstraints(), new ColumnConstraints(), grow, new ColumnConstraints());

        List<Field> fields = new ArrayList<>();
        int r = 0;
        for (ColumnInfo c : columns) {
            Object value = null;
            if (!insert) {
                int idx = rowColumns.indexOf(c.name());
                value = idx >= 0 ? row.get(idx) : null;
            }
            boolean blobValue = value instanceof byte[];
            String text = insert ? "" : editText(value);

            TextInputControl input = isLongText(c.type()) ? new TextArea(text) : new TextField(text);
            if (input instanceof TextArea ta) {
                ta.setPrefRowCount(3);
                ta.setWrapText(true);
            }
            if (insert && c.isAutoIncrement()) input.setPromptText("auto");
            else if (insert && c.defaultValue() != null) input.setPromptText("default: " + c.defaultValue());
            if (blobValue) {
                input.setText(Sql.display(value));
            }

            CheckBox nul = new CheckBox("NULL");
            nul.setVisible(c.nullable());
            nul.setSelected(!insert && value == null && c.nullable());
            input.disableProperty().bind(nul.selectedProperty().or(new javafx.beans.property.SimpleBooleanProperty(blobValue)));

            Label name = new Label(c.name());
            name.getStyleClass().add(c.isPrimaryKey() ? "field-name-pk" : "field-name");
            Label type = new Label(c.type());
            type.getStyleClass().add("field-type");
            grid.addRow(r++, name, type, input, nul);
            fields.add(new Field(c, input, nul, text, !insert && value == null, blobValue));
        }

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPrefSize(720, Math.min(600, 60 + columns.size() * 42));
        getDialogPane().setContent(scroll);

        ButtonType save = new ButtonType(insert ? "Insert" : "Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        setResultConverter(bt -> {
            if (bt != save) return null;
            Map<String, Object> out = new LinkedHashMap<>();
            for (Field f : fields) {
                if (f.readOnly()) continue;
                boolean nowNull = f.isNull().isSelected();
                String text = f.input().getText();
                if (insert) {
                    if (nowNull) out.put(f.col().name(), null);
                    else if (!text.isEmpty()) out.put(f.col().name(), text);
                    else if (!f.col().isAutoIncrement() && f.col().defaultValue() == null && !f.col().nullable())
                        out.put(f.col().name(), "");
                    // otherwise leave it out so the column default / auto_increment applies
                } else {
                    if (nowNull != f.wasNull() || (!nowNull && !text.equals(f.original()))) {
                        out.put(f.col().name(), nowNull ? null : text);
                    }
                }
            }
            return out;
        });
    }

    private static String editText(Object v) {
        return Sql.text(v);
    }

    private static boolean isLongText(String type) {
        String t = type.toLowerCase();
        return t.contains("text") || t.contains("blob") || t.startsWith("json");
    }
}
