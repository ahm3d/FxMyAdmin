package com.javamyadmin.ui;

import com.javamyadmin.db.Database;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Export a database (SQL) or a single table (SQL or CSV) to a file. */
public class ExportView extends VBox implements ContextView {

    private final Database db;
    private final Window owner;
    private final Consumer<String> status;

    private final RadioButton sqlFormat = new RadioButton("SQL");
    private final RadioButton csvFormat = new RadioButton("CSV (current table only)");
    private final CheckBox structure = new CheckBox("Structure (DROP + CREATE TABLE)");
    private final CheckBox data = new CheckBox("Data (INSERT statements)");
    private final ListView<String> tableList = new ListView<>();
    private final Map<String, BooleanProperty> checked = new LinkedHashMap<>();
    private final Label info = new Label();

    private String dbName;
    private String table;

    public ExportView(Database db, Window owner, Consumer<String> status) {
        this.db = db;
        this.owner = owner;
        this.status = status;

        ToggleGroup fmt = new ToggleGroup();
        sqlFormat.setToggleGroup(fmt);
        csvFormat.setToggleGroup(fmt);
        sqlFormat.setSelected(true);
        structure.setSelected(true);
        data.setSelected(true);

        tableList.setCellFactory(CheckBoxListCell.forListView(checked::get));
        tableList.setPrefHeight(260);

        Button all = new Button("Select all");
        all.setOnAction(e -> checked.values().forEach(p -> p.set(true)));
        Button none = new Button("Select none");
        none.setOnAction(e -> checked.values().forEach(p -> p.set(false)));

        VBox sqlOptions = new VBox(6, structure, data, new Label("Tables:"), tableList, new HBox(6, all, none));
        sqlOptions.disableProperty().bind(csvFormat.selectedProperty());

        Button export = new Button("Export…");
        export.getStyleClass().add("primary");
        export.setOnAction(e -> export());

        Label title = new Label("Export");
        title.getStyleClass().add("section-title");

        setSpacing(10);
        setPadding(new Insets(14));
        getChildren().addAll(title, new Label("Format:"), new HBox(16, sqlFormat, csvFormat),
                sqlOptions, export, info);
    }

    @Override
    public boolean needsTable() {
        return false;
    }

    @Override
    public void load(String dbName, String table) {
        this.dbName = dbName;
        this.table = table;
        csvFormat.setDisable(table == null);
        if (table == null && csvFormat.isSelected()) sqlFormat.setSelected(true);
        info.setText("");
        Async.run(() -> db.tables(dbName), tables -> {
            checked.clear();
            for (String t : tables) checked.put(t, new SimpleBooleanProperty(table == null || t.equals(table)));
            ObservableList<String> items = FXCollections.observableArrayList(tables);
            tableList.setItems(items);
        });
    }

    private void export() {
        boolean csv = csvFormat.isSelected();
        List<String> tables = checked.entrySet().stream().filter(e -> e.getValue().get()).map(Map.Entry::getKey).toList();
        if (!csv && tables.isEmpty()) {
            Dialogs.info("Nothing to export", "Select at least one table.");
            return;
        }
        if (!csv && !structure.isSelected() && !data.isSelected()) {
            Dialogs.info("Nothing to export", "Select structure and/or data.");
            return;
        }

        FileChooser fc = new FileChooser();
        fc.setTitle("Save export");
        String base = csv ? table : (tables.size() == 1 ? tables.get(0) : dbName);
        fc.setInitialFileName(base + (csv ? ".csv" : ".sql"));
        fc.setInitialDirectory(new File(System.getProperty("user.home")));
        fc.getExtensionFilters().add(csv
                ? new FileChooser.ExtensionFilter("CSV files", "*.csv")
                : new FileChooser.ExtensionFilter("SQL files", "*.sql"));
        File file = fc.showSaveDialog(owner);
        if (file == null) return;

        String d = dbName, t = table;
        boolean withStructure = structure.isSelected(), withData = data.isSelected();
        info.setText("Exporting…");
        Async.run(() -> {
            try (BufferedWriter w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                if (csv) db.exportCsv(d, t, w);
                else db.exportSql(d, tables, withStructure, withData, w);
            }
            return file.length();
        }, bytes -> {
            info.setText("Saved " + file + " (" + bytes / 1024 + " KB)");
            status.accept("Export finished: " + file.getName());
        }, err -> {
            info.setText("Export failed");
            Dialogs.error(err);
        });
    }
}
