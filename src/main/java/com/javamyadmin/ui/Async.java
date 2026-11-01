package com.javamyadmin.ui;

import javafx.application.Platform;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Runs database work on one background thread (JDBC connections are not
 * thread safe) and hands results back to the JavaFX thread.
 */
public final class Async {

    @FunctionalInterface
    public interface Job<T> {
        T run() throws Exception;
    }

    private static final ExecutorService DB = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "db-worker");
        t.setDaemon(true);
        return t;
    });

    private Async() {}

    public static <T> void run(Job<T> job, Consumer<T> onSuccess) {
        run(job, onSuccess, Dialogs::error);
    }

    public static <T> void run(Job<T> job, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        DB.submit(() -> {
            try {
                T result = job.run();
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (Throwable e) {
                Platform.runLater(() -> onError.accept(e));
            }
        });
    }
}
