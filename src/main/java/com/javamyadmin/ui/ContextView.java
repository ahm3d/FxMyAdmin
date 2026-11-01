package com.javamyadmin.ui;

/** A tab that shows something about the selected database and/or table. */
public interface ContextView {
    /** {@code table} is null when a database (not a table) is selected. */
    void load(String db, String table);

    boolean needsTable();
}
