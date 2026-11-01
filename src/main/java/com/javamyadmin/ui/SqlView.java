package com.javamyadmin.ui;

import com.javamyadmin.db.Database;
import com.javamyadmin.db.QueryResult;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/** Free-form SQL editor. Ctrl+Enter runs the selection, or everything if nothing is selected. */
public class SqlView extends BorderPane implements ContextView {

    private final Database db;
    private final Consumer<String> status;
    private final TextArea editor = new TextArea();
    private final ResultGrid grid = new ResultGrid();
    private final Label result = new Label();
    private final Label target = new Label();
    private final Button run = new Button("▶ Run  (Ctrl+Enter)");

    private String dbName;

    public SqlView(Database db, Consumer<String> status) {
        this.db = db;
        this.status = status;

        editor.getStyleClass().add("code");
        editor.setPromptText("SELECT * FROM ...");
        editor.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && e.isShortcutDown()) {
                e.consume();
                execute();
            }
        });

        run.getStyleClass().add("primary");
        run.setOnAction(e -> execute());
        Button clear = new Button("Clear");
        clear.setOnAction(e -> editor.clear());

        FlowPane bar = new FlowPane(6, 6, run, clear, target);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6));
        bar.getStyleClass().add("toolbar");

        result.setPadding(new Insets(4, 8, 4, 8));
        result.getStyleClass().add("query-result");
        VBox bottom = new VBox(result, grid);
        VBox.setVgrow(grid, Priority.ALWAYS);

        SplitPane split = new SplitPane(editor, bottom);
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.35);

        setTop(bar);
        setCenter(split);
    }

    @Override
    public boolean needsTable() {
        return false;
    }

    @Override
    public void load(String dbName, String table) {
        this.dbName = dbName;
        target.setText(dbName == null ? "No database selected" : "Runs in database: " + dbName);
        if (editor.getText().isBlank() && table != null) {
            editor.setText("SELECT * FROM `" + table.replace("`", "``") + "` LIMIT 100;");
        }
    }

    private void execute() {
        String selected = editor.getSelectedText();
        String sql = (selected == null || selected.isBlank()) ? editor.getText() : selected;
        if (sql.isBlank()) return;
        run.setDisable(true);
        result.setText("Running…");
        result.getStyleClass().remove("error");
        String d = dbName;
        Async.run(() -> db.execute(d, sql), (QueryResult r) -> {
            run.setDisable(false);
            if (r.hasResultSet()) {
                grid.show(r);
                result.setText(r.rows().size() + " row(s) returned in " + r.millis() + " ms");
            } else {
                grid.clear();
                result.setText(r.updateCount() + " row(s) affected in " + r.millis() + " ms");
            }
            status.accept(result.getText());
        }, err -> {
            run.setDisable(false);
            grid.clear();
            Throwable root = err;
            while (root.getCause() != null) root = root.getCause();
            result.setText("Error: " + root.getMessage());
            if (!result.getStyleClass().contains("error")) result.getStyleClass().add("error");
            status.accept("Query failed");
        });
    }
}
