package com.javamyadmin.db;

/** One row of SHOW FULL COLUMNS. */
public record ColumnInfo(String name, String type, boolean nullable, String key,
                         String defaultValue, String extra, String comment) {

    public boolean isPrimaryKey() {
        return "PRI".equals(key);
    }

    public boolean isAutoIncrement() {
        return extra != null && extra.contains("auto_increment");
    }
}
