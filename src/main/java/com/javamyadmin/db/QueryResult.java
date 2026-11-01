package com.javamyadmin.db;

import java.util.List;

/**
 * Result of a statement. If {@code columns} is empty it was an update and
 * {@code updateCount} holds the number of affected rows.
 */
public record QueryResult(List<String> columns, List<List<Object>> rows, long updateCount, long millis) {

    public boolean hasResultSet() {
        return !columns.isEmpty();
    }
}
