package com.javamyadmin;

import com.javamyadmin.db.ConnectionInfo;
import com.javamyadmin.db.Database;
import com.javamyadmin.ui.Async;
import com.javamyadmin.ui.Dialogs;
import com.javamyadmin.ui.LoginView;
import com.javamyadmin.ui.Look;
import com.javamyadmin.ui.MainWindow;
import com.javamyadmin.ui.Splash;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    /** Minimum time the loading screen stays up, so it doesn't just flash. */
    private static final long SPLASH_MIN_MS = 1600;

    private Stage stage;
    private Database db;
    private LoginView login;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        Look.apply(stage);
        stage.setMinWidth(900);
        stage.setMinHeight(560);

        Splash splash = new Splash();
        splash.show();
        long t0 = System.currentTimeMillis();
        Async.run(() -> {
            Class.forName("com.mysql.cj.jdbc.Driver"); // warm up the driver while the poster shows
            long left = SPLASH_MIN_MS - (System.currentTimeMillis() - t0);
            if (left > 0) Thread.sleep(left);
            return null;
        }, x -> {
            showLogin();
            splash.close();
        }, e -> {
            showLogin();
            splash.close();
        });
    }

    private void showLogin() {
        login = new LoginView(this::connect, Platform::exit);
        Scene scene = new Scene(login, 1240, 720);
        Look.apply(scene);
        stage.setScene(scene);
        stage.setTitle("FxMyAdmin");
        stage.show();
        login.focus();
    }

    private void connect(ConnectionInfo info) {
        login.setBusy(true, "Connecting to " + info.label() + "…");
        Async.run(() -> Database.connect(info), database -> {
            this.db = database;
            new MainWindow(stage, database, this::logout).show();
        }, e -> {
            login.setBusy(false, null);
            login.showError(Dialogs.message(e));
        });
    }

    private void logout() {
        Database old = db;
        db = null;
        if (old != null) Async.run(() -> { old.close(); return null; }, x -> { });
        showLogin();
    }

    @Override
    public void stop() {
        if (db != null) db.close();
    }
}
