package com.javamyadmin.ui;

import com.javamyadmin.db.ConnectionInfo;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.function.Consumer;
import java.util.prefs.Preferences;

/** The connect page: a centered login card. */
public class LoginView extends StackPane {

    private static final Preferences PREFS = Preferences.userNodeForPackage(LoginView.class);

    private final TextField host = new TextField(PREFS.get("host", "127.0.0.1"));
    private final Spinner<Integer> port = new Spinner<>(1, 65535, PREFS.getInt("port", 3306));
    private final TextField user = new TextField(PREFS.get("user", "root"));
    private final PasswordField pass = new PasswordField();
    private final Label error = new Label();
    private final Button connect = new Button("Connect");
    private final HBox busy;
    private final Label busyText = new Label();
    private final VBox fields;
    private final InvalidationListener themeListener;

    public LoginView(Consumer<ConnectionInfo> onConnect, Runnable onQuit) {
        getStyleClass().add("login-bg");

        // ----- brand -----
        ImageView logo = new ImageView(Look.image("icon128.png"));
        logo.setFitWidth(64);
        logo.setFitHeight(64);
        Text fx = new Text("Fx");
        fx.getStyleClass().add("title-fx");
        Text rest = new Text("MyAdmin");
        rest.getStyleClass().add("title-rest");
        TextFlow title = new TextFlow(fx, rest);
        Label subtitle = new Label("MySQL Manager");
        subtitle.getStyleClass().add("app-subtitle");
        VBox titles = new VBox(2, title, subtitle);
        titles.setAlignment(Pos.CENTER_LEFT);

        Button theme = new Button();
        theme.getStyleClass().add("icon-button");
        theme.setFocusTraversable(false);
        theme.setOnAction(e -> Look.toggle());
        Runnable themeText = () -> {
            theme.setText(Look.isDark() ? "☀" : "☾");
            theme.setTooltip(new Tooltip(Look.isDark() ? "Switch to light mode" : "Switch to dark mode"));
        };
        themeText.run();
        themeListener = o -> themeText.run();
        Look.darkProperty().addListener(new WeakInvalidationListener(themeListener));

        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox brand = new HBox(14, logo, titles, grow, theme);
        brand.setAlignment(Pos.CENTER_LEFT);

        // ----- form -----
        Label heading = new Label("Connect to a server");
        heading.getStyleClass().add("login-heading");
        Label hint = new Label("MySQL or MariaDB");
        hint.getStyleClass().add("app-subtitle");

        port.setEditable(true);
        port.setMaxWidth(Double.MAX_VALUE);
        host.setPromptText("127.0.0.1");
        user.setPromptText("user name");
        pass.setPromptText("password");

        VBox hostField = field("Host", host);
        VBox portField = field("Port", port);
        portField.setPrefWidth(110);
        portField.setMinWidth(110);
        HBox.setHgrow(hostField, Priority.ALWAYS);
        HBox hostRow = new HBox(10, hostField, portField);

        error.getStyleClass().add("login-error");
        error.setWrapText(true);
        error.setMaxWidth(Double.MAX_VALUE);
        error.setManaged(false);
        error.setVisible(false);

        connect.getStyleClass().addAll("primary", "big-button");
        connect.setDefaultButton(true);
        connect.setMaxWidth(Double.MAX_VALUE);
        connect.disableProperty().bind(host.textProperty().isEmpty().or(user.textProperty().isEmpty()));
        connect.setOnAction(e -> {
            port.increment(0); // commit typed text
            String h = host.getText().trim(), u = user.getText().trim();
            PREFS.put("host", h);
            PREFS.putInt("port", port.getValue());
            PREFS.put("user", u);
            hideError();
            onConnect.accept(new ConnectionInfo(h, port.getValue(), u, pass.getText()));
        });

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(22, 22);
        busyText.getStyleClass().add("app-subtitle");
        busy = new HBox(10, spinner, busyText);
        busy.setAlignment(Pos.CENTER);
        busy.setManaged(false);
        busy.setVisible(false);

        Button quit = new Button("Quit");
        quit.getStyleClass().add("link-button");
        quit.setOnAction(e -> onQuit.run());
        HBox quitRow = new HBox(quit);
        quitRow.setAlignment(Pos.CENTER);

        fields = new VBox(14, hostRow, field("User", user), field("Password", pass), error, connect);

        VBox card = new VBox(14, brand, sep(), heading, hint, fields, busy, quitRow);
        card.getStyleClass().add("login-card");
        card.setPadding(new Insets(30, 34, 22, 34));
        card.setMaxWidth(440);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        card.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) onQuit.run();
        });

        Label footer = new Label("Native MySQL client for Linux");
        footer.getStyleClass().add("app-subtitle");
        StackPane.setAlignment(footer, Pos.BOTTOM_CENTER);
        StackPane.setMargin(footer, new Insets(0, 0, 16, 0));

        setPadding(new Insets(24));
        getChildren().addAll(card, footer);
    }

    private static Region sep() {
        Region r = new Region();
        r.getStyleClass().add("card-separator");
        r.setMinHeight(1);
        r.setPrefHeight(1);
        return r;
    }

    private static VBox field(String label, Node input) {
        Label l = new Label(label);
        l.getStyleClass().add("form-label");
        return new VBox(5, l, input);
    }

    /** Puts the cursor in the first empty field. */
    public void focus() {
        if (host.getText().isEmpty()) host.requestFocus();
        else if (user.getText().isEmpty()) user.requestFocus();
        else pass.requestFocus();
    }

    public void setBusy(boolean on, String message) {
        busy.setManaged(on);
        busy.setVisible(on);
        busyText.setText(message == null ? "" : message);
        fields.setDisable(on);
    }

    public void showError(String message) {
        error.setText(message);
        error.setManaged(true);
        error.setVisible(true);
        pass.requestFocus();
        pass.selectAll();
    }

    private void hideError() {
        error.setManaged(false);
        error.setVisible(false);
    }
}
