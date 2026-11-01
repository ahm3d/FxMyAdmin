package com.javamyadmin.ui;

import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.List;
import java.util.Objects;
import java.util.prefs.Preferences;

/** App icons and the light / dark theme, applied to every window. */
public final class Look {

    public enum Mode { SYSTEM, LIGHT, DARK }

    private static final Preferences PREFS = Preferences.userNodeForPackage(Look.class);
    private static final String BASE_CSS = resource("app.css").toExternalForm();
    private static final String DARK_CSS = resource("dark.css").toExternalForm();

    private static ObjectProperty<Mode> mode;
    private static BooleanBinding dark;
    private static List<Image> icons;

    private Look() {}

    // ---------- theme ----------

    /** Must be used on the JavaFX thread after startup. */
    public static ObjectProperty<Mode> modeProperty() {
        init();
        return mode;
    }

    public static boolean isDark() {
        init();
        return dark.get();
    }

    public static javafx.beans.value.ObservableBooleanValue darkProperty() {
        init();
        return dark;
    }

    public static void toggle() {
        modeProperty().set(isDark() ? Mode.LIGHT : Mode.DARK);
    }

    private static void init() {
        if (mode != null) return;
        Mode saved;
        try {
            saved = Mode.valueOf(PREFS.get("theme", Mode.SYSTEM.name()));
        } catch (IllegalArgumentException e) {
            saved = Mode.SYSTEM;
        }
        mode = new SimpleObjectProperty<>(saved);
        mode.addListener((o, a, b) -> PREFS.put("theme", b.name()));
        var scheme = Platform.getPreferences().colorSchemeProperty();
        dark = Bindings.createBooleanBinding(
                () -> mode.get() == Mode.DARK || (mode.get() == Mode.SYSTEM && scheme.get() == ColorScheme.DARK),
                mode, scheme);
    }

    /** Adds the app stylesheets to a scene and keeps them in sync with the theme. */
    public static void apply(Scene scene) {
        init();
        Runnable update = () -> {
            ObservableList<String> ss = scene.getStylesheets();
            if (!ss.contains(BASE_CSS)) ss.add(0, BASE_CSS);
            boolean d = dark.get();
            if (d && !ss.contains(DARK_CSS)) ss.add(DARK_CSS);
            if (!d) ss.remove(DARK_CSS);
        };
        update.run();
        InvalidationListener l = o -> update.run();
        scene.getProperties().put("look.theme-listener", l); // lives as long as the scene
        dark.addListener(new WeakInvalidationListener(l));
    }

    // ---------- icons ----------

    public static List<Image> icons() {
        if (icons == null) {
            icons = List.of(image("icon32.png"), image("icon64.png"), image("icon128.png"), image("icon256.png"));
        }
        return icons;
    }

    public static Image image(String name) {
        return new Image(Objects.requireNonNull(Look.class.getResourceAsStream("/com/javamyadmin/" + name)));
    }

    public static void apply(Stage stage) {
        if (stage.getIcons().isEmpty()) stage.getIcons().addAll(icons());
    }

    /** Icon and theme for a dialog. */
    public static void apply(Dialog<?> dialog) {
        Scene scene = dialog.getDialogPane().getScene();
        if (scene == null) return;
        apply(scene);
        if (scene.getWindow() instanceof Stage s) apply(s);
    }

    private static java.net.URL resource(String name) {
        return Objects.requireNonNull(Look.class.getResource("/com/javamyadmin/" + name), name);
    }
}
