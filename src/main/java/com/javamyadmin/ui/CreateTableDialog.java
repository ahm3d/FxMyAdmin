package com.javamyadmin.ui;

import com.javamyadmin.db.Sql;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/** Builds a CREATE TABLE statement from a simple column form, with a live preview. */
public class CreateTableDialog extends Dialog<String> {

    private final class ColumnRow {
        final TextField name = new TextField();
        final ComboBox<String> type = new ComboBox<>();
        final CheckBox nullable = new CheckBox();
        final TextField def = new TextField();
        final CheckBox pk = new CheckBox();
        final CheckBox ai = new CheckBox();
        final Button remove = new Button("✕");

        ColumnRow(String n, String t, boolean primary, boolean autoInc) {
            name.setText(n);
            name.setPromptText("column name");
            type.getItems().addAll(ColumnTypes.COMMON);
            type.setEditable(true);
            type.setValue(t);
            type.setPrefWidth(150);
            pk.setSelected(primary);
            ai.setSelected(autoInc);
            def.setPromptText("none");
            def.setPrefWidth(100);
            remove.setOnAction(e -> { rows.remove(this); layoutRows(); updatePreview(); });
            name.textProperty().addListener(o -> updatePreview());
            type.getEditor().textProperty().addListener(o -> updatePreview());
            type.valueProperty().addListener(o -> updatePreview());
            nullable.selectedProperty().addListener(o -> updatePreview());
            def.textProperty().addListener(o -> updatePreview());
            pk.selectedProperty().addListener(o -> updatePreview());
            ai.selectedProperty().addListener(o -> updatePreview());
        }

        String typeText() {
            String t = type.getEditor().getText();
            return t == null || t.isBlank() ? type.getValue() : t.trim();
        }
    }

    private final List<ColumnRow> rows = new ArrayList<>();
    private final GridPane grid = new GridPane();
    private final TextField tableName = new TextField();
    private final TextArea preview = new TextArea();
    private final String db;

    public CreateTableDialog(String db) {
        this.db = db;
        setTitle("Create table");
        Look.apply(this);
        setHeaderText("Create a table in " + db);
        setResizable(true);

        grid.setHgap(8);
        grid.setVgap(6);
        rows.add(new ColumnRow("id", "INT", true, true));
        rows.add(new ColumnRow("", "VARCHAR(255)", false, false));
        layoutRows();

        Button addRow = new Button("+ Add column");
        addRow.setOnAction(e -> { rows.add(new ColumnRow("", "VARCHAR(255)", false, false)); layoutRows(); updatePreview(); });

        tableName.setPromptText("table name");
        tableName.textProperty().addListener(o -> updatePreview());

        preview.setEditable(false);
        preview.setPrefRowCount(7);
        preview.getStyleClass().add("code");

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(220);

        VBox box = new VBox(10, new HBox(10, new Label("Table name:"), tableName), scroll, addRow,
                new Label("SQL preview:"), preview);
        box.setPadding(new Insets(12));
        box.setPrefWidth(760);
        getDialogPane().setContent(box);

        ButtonType ok = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        getDialogPane().lookupButton(ok).disableProperty().bind(tableName.textProperty().isEmpty());
        setOnShown(e -> tableName.requestFocus());
        setResultConverter(bt -> bt == ok ? buildSql() : null);
        updatePreview();
    }

    private void layoutRows() {
        grid.getChildren().clear();
        grid.addRow(0, new Label("Name"), new Label("Type"), new Label("Null"),
                new Label("Default"), new Label("Primary"), new Label("A_I"), new Label(""));
        int r = 1;
        for (ColumnRow c : rows) grid.addRow(r++, c.name, c.type, c.nullable, c.def, c.pk, c.ai, c.remove);
    }

    private void updatePreview() {
        preview.setText(buildSql());
    }

    private String buildSql() {
        List<String> defs = new ArrayList<>();
        List<String> pks = new ArrayList<>();
        for (ColumnRow c : rows) {
            String n = c.name.getText().trim();
            if (n.isEmpty()) continue;
            StringBuilder sb = new StringBuilder("  ").append(Sql.id(n)).append(' ').append(c.typeText());
            sb.append(c.nullable.isSelected() && !c.pk.isSelected() ? " NULL" : " NOT NULL");
            if (!c.def.getText().isEmpty()) sb.append(" DEFAULT ").append(Sql.defaultExpr(c.def.getText()));
            if (c.ai.isSelected()) sb.append(" AUTO_INCREMENT");
            defs.add(sb.toString());
            if (c.pk.isSelected()) pks.add(Sql.id(n));
        }
        if (!pks.isEmpty()) defs.add("  PRIMARY KEY (" + String.join(", ", pks) + ")");
        return "CREATE TABLE " + Sql.id(db, tableName.getText().trim()) + " (\n"
                + String.join(",\n", defs)
                + "\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }
}
