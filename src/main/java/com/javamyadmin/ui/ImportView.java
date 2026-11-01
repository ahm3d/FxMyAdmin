package com.javamyadmin.ui;

import com.javamyadmin.db.Database;
import com.javamyadmin.db.Database.ImportResult;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

/** Runs an .sql or .sql.gz file against the server. */
public class ImportView extends VBox implements ContextView {

    private static final String NO_DB = "(none - the file chooses with USE)";

    private final Database db;
    private final Window owner;
    private final Consumer<String> status;
    private final Runnable onFinished;

    private final Label fileLabel = new Label("No file selected");
    private final ComboBox<String> target = new ComboBox<>();
    private final CheckBox noFk = new CheckBox("Disable foreign key checks during import");
    private final CheckBox keepGoing = new CheckBox("Continue after errors");
    private final Button start = new Button("Import");
    private final Button cancel = new Button("Cancel");
    private final ProgressBar progress = new ProgressBar(0);
    private final Label progressLabel = new Label();
    private final TextArea log = new TextArea();

    private File file;
    private AtomicBoolean cancelFlag;

    public ImportView(Database db, Window owner, Consumer<String> status, Runnable onFinished) {
        this.db = db;
        this.owner = owner;
        this.status = status;
        this.onFinished = onFinished;

        Label title = new Label("Import");
        title.getStyleClass().add("section-title");

        Button browse = new Button("Choose file…");
        browse.setOnAction(e -> chooseFile());
        HBox fileRow = new HBox(10, browse, fileLabel);
        fileRow.setAlignment(Pos.CENTER_LEFT);

        target.setPrefWidth(320);
        HBox targetRow = new HBox(10, new Label("Target database:"), target);
        targetRow.setAlignment(Pos.CENTER_LEFT);

        noFk.setSelected(true);

        start.getStyleClass().add("primary");
        start.setDisable(true);
        start.setOnAction(e -> runImport());
        cancel.setDisable(true);
        cancel.setOnAction(e -> {
            if (cancelFlag != null) cancelFlag.set(true);
            cancel.setDisable(true);
        });

        progress.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(progress, Priority.ALWAYS);
        HBox progressRow = new HBox(10, start, cancel, progress, progressLabel);
        progressRow.setAlignment(Pos.CENTER_LEFT);

        log.setEditable(false);
        log.getStyleClass().add("code");
        VBox.setVgrow(log, Priority.ALWAYS);

        Label hint = new Label("Supports .sql and gzip-compressed .sql.gz files (UTF-8), such as mysqldump "
                + "output or FxMyAdmin exports.");
        hint.setWrapText(true);
        hint.getStyleClass().add("field-type");

        setSpacing(10);
        setPadding(new Insets(14));
        getChildren().addAll(title, hint, fileRow, targetRow, noFk, keepGoing, progressRow, new Label("Log:"), log);
    }

    @Override
    public boolean needsTable() {
        return false;
    }

    @Override
    public void load(String dbName, String table) {
        if (cancelFlag != null) return; // import running, don't touch the form
        Async.run(db::databases, dbs -> {
            target.getItems().setAll(NO_DB);
            dbs.stream()
                    .filter(d -> !d.equals("information_schema") && !d.equals("performance_schema"))
                    .forEach(target.getItems()::add);
            target.setValue(dbName != null && target.getItems().contains(dbName) ? dbName : NO_DB);
        });
    }

    private void chooseFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Import SQL file");
        fc.setInitialDirectory(file != null ? file.getParentFile() : new File(System.getProperty("user.home")));
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("SQL files", "*.sql", "*.sql.gz", "*.gz"),
                new FileChooser.ExtensionFilter("All files", "*"));
        File f = fc.showOpenDialog(owner);
        if (f == null) return;
        file = f;
        fileLabel.setText(f.getAbsolutePath() + "  (" + humanSize(f.length()) + ")");
        start.setDisable(false);
    }

    private void runImport() {
        if (file == null) return;
        String dbName = NO_DB.equals(target.getValue()) ? null : target.getValue();
        String where = dbName == null ? "the server" : "database " + dbName;
        if (!Dialogs.confirm("Import " + file.getName() + " into " + where + "?",
                "Statements in the file run as-is. They may drop or overwrite existing tables.")) return;

        File f = file;
        boolean fk = noFk.isSelected(), cont = keepGoing.isSelected();
        AtomicBoolean cancelled = new AtomicBoolean();
        cancelFlag = cancelled;
        AtomicLong bytesRead = new AtomicLong();
        AtomicLong lastUi = new AtomicLong();
        long total = Math.max(1, f.length());

        setRunning(true);
        log.setText("Importing " + f + " into " + where + "…\n");
        progress.setProgress(0);
        long t0 = System.currentTimeMillis();

        Async.run(() -> {
            InputStream raw = new CountingStream(Files.newInputStream(f.toPath()), bytesRead);
            InputStream in = f.getName().endsWith(".gz") ? new GZIPInputStream(raw, 1 << 16) : raw;
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return db.importSql(dbName, r, fk, cont, cancelled::get, count -> {
                    long now = System.currentTimeMillis();
                    if (now - lastUi.get() < 150) return;
                    lastUi.set(now);
                    double p = (double) bytesRead.get() / total;
                    Platform.runLater(() -> {
                        progress.setProgress(p);
                        progressLabel.setText(count + " statements");
                    });
                });
            }
        }, (ImportResult res) -> {
            setRunning(false);
            double secs = (System.currentTimeMillis() - t0) / 1000.0;
            if (!res.cancelled() && res.errors().isEmpty()) progress.setProgress(1);
            progressLabel.setText(res.executed() + " statements");
            StringBuilder sb = new StringBuilder();
            for (String err : res.errors()) sb.append("ERROR ").append(err).append('\n');
            String summary = (res.cancelled() ? "Cancelled" : res.errors().isEmpty() ? "Done" : "Finished with errors")
                    + ": " + res.executed() + " statements executed, " + res.errors().size() + " error(s) in "
                    + String.format("%.1f", secs) + " s";
            sb.append(summary).append('\n');
            log.appendText(sb.toString());
            status.accept("Import: " + summary);
            onFinished.run();
        }, err -> {
            setRunning(false);
            log.appendText("FAILED: " + err + "\n");
            Dialogs.error(err);
            onFinished.run();
        });
    }

    private void setRunning(boolean running) {
        start.setDisable(running);
        cancel.setDisable(!running);
        target.setDisable(running);
        if (!running) cancelFlag = null;
    }

    private static String humanSize(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format("%.1f KB", b / 1024.0);
        if (b < 1024L * 1024 * 1024) return String.format("%.1f MB", b / 1024.0 / 1024);
        return String.format("%.2f GB", b / 1024.0 / 1024 / 1024);
    }

    /** Counts bytes read from the file, for the progress bar. */
    private static final class CountingStream extends FilterInputStream {
        private final AtomicLong count;

        CountingStream(InputStream in, AtomicLong count) {
            super(in);
            this.count = count;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) count.incrementAndGet();
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            int n = super.read(buf, off, len);
            if (n > 0) count.addAndGet(n);
            return n;
        }
    }
}
