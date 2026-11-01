package com.javamyadmin.ui;

import com.javamyadmin.db.ColumnInfo;
import com.javamyadmin.db.Sql;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.util.List;

/** Returns a column definition such as "`age` INT NOT NULL DEFAULT '0' AFTER `name`". */
public class AddColumnDialog extends Dialog<String> {

    public AddColumnDialog(String table, List<ColumnInfo> existing) {
        setTitle("Add column");
        Look.apply(this);
        setHeaderText("Add a column to " + table);

        TextField name = new TextField();
        ComboBox<String> type = new ComboBox<>();
        type.getItems().addAll(ColumnTypes.COMMON);
        type.setEditable(true);
        type.setValue("VARCHAR(255)");
        CheckBox nullable = new CheckBox("Allow NULL");
        nullable.setSelected(true);
        TextField def = new TextField();
        def.setPromptText("none");
        ComboBox<String> position = new ComboBox<>();
        position.getItems().add("At end");
        position.getItems().add("At beginning");
        existing.forEach(c -> position.getItems().add("After " + c.name()));
        position.setValue("At end");

        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(8);
        g.setPadding(new Insets(16));
        g.addRow(0, new Label("Name:"), name);
        g.addRow(1, new Label("Type:"), type);
        g.addRow(2, new Label(""), nullable);
        g.addRow(3, new Label("Default:"), def);
        g.addRow(4, new Label("Position:"), position);
        getDialogPane().setContent(g);

        ButtonType ok = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        getDialogPane().lookupButton(ok).disableProperty().bind(name.textProperty().isEmpty());
        setOnShown(e -> name.requestFocus());

        setResultConverter(bt -> {
            if (bt != ok) return null;
            StringBuilder sb = new StringBuilder(Sql.id(name.getText().trim()))
                    .append(' ').append(type.getEditor().getText().trim())
                    .append(nullable.isSelected() ? " NULL" : " NOT NULL");
            if (!def.getText().isEmpty()) sb.append(" DEFAULT ").append(Sql.defaultExpr(def.getText()));
            String pos = position.getValue();
            if ("At beginning".equals(pos)) sb.append(" FIRST");
            else if (pos.startsWith("After ")) sb.append(" AFTER ").append(Sql.id(pos.substring(6)));
            return sb.toString();
        });
    }
}
