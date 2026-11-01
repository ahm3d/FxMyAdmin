package com.javamyadmin.ui;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/** Borderless loading screen with the poster, shown while the app starts. */
public class Splash {

    private final Stage stage = new Stage(StageStyle.UNDECORATED);

    public Splash() {
        ImageView poster = new ImageView(Look.image("poster.jpg"));
        poster.setFitWidth(840);
        poster.setPreserveRatio(true);
        poster.setSmooth(true);

        ProgressBar bar = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
        bar.setMaxWidth(Double.MAX_VALUE);
        Label text = new Label("Loading…");
        VBox band = new VBox(8, text, bar);
        band.getStyleClass().add("splash-band");
        band.setMaxHeight(VBox.USE_PREF_SIZE);
        StackPane.setAlignment(band, Pos.BOTTOM_CENTER);

        StackPane root = new StackPane(poster, band);
        root.getStyleClass().add("splash");
        Scene scene = new Scene(root);
        Look.apply(scene);
        stage.setScene(scene);
        stage.setTitle("FxMyAdmin");
        Look.apply(stage);
    }

    public void show() {
        stage.centerOnScreen();
        stage.show();
    }

    public void close() {
        stage.close();
    }
}
