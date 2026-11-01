package com.javamyadmin.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;

import java.util.Optional;

public final class Dialogs {
    private Dialogs() {}

    /** The most useful message in an exception chain. */
    public static String message(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String msg = root.getMessage() != null ? root.getMessage() : root.toString();
        // the driver's first line is the useful part of long link-failure messages
        int cut = msg.indexOf("\n\n");
        return cut > 0 ? msg.substring(0, cut) : msg;
    }

    public static void error(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String msg = message(e);
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Error");
        a.setHeaderText(root.getClass().getSimpleName());
        TextArea text = new TextArea(msg);
        text.setEditable(false);
        text.setWrapText(true);
        text.setPrefRowCount(4);
        a.getDialogPane().setContent(text);
        Look.apply(a);
        a.showAndWait();
    }

    public static void info(String header, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg);
        a.setHeaderText(header);
        Look.apply(a);
        a.showAndWait();
    }

    public static boolean confirm(String header, String msg) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.OK, ButtonType.CANCEL);
        a.setHeaderText(header);
        Look.apply(a);
        return a.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    public static Optional<String> prompt(String header, String label) {
        TextInputDialog d = new TextInputDialog();
        d.setHeaderText(header);
        d.setContentText(label);
        Look.apply(d);
        return d.showAndWait().map(String::trim).filter(s -> !s.isEmpty());
    }
}
