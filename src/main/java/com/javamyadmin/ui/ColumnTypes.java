package com.javamyadmin.ui;

import java.util.List;

final class ColumnTypes {
    private ColumnTypes() {}

    static final List<String> COMMON = List.of(
            "INT", "BIGINT", "TINYINT", "SMALLINT", "DECIMAL(10,2)", "DOUBLE", "FLOAT",
            "VARCHAR(255)", "CHAR(36)", "TEXT", "MEDIUMTEXT", "LONGTEXT",
            "DATE", "DATETIME", "TIMESTAMP", "TIME", "YEAR",
            "BOOLEAN", "JSON", "BLOB", "LONGBLOB", "ENUM('a','b')");
}
