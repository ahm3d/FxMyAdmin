package com.javamyadmin.db;

import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.HexFormat;

/** Helpers for building MySQL text safely. */
public final class Sql {
    private Sql() {}

    /** Quote an identifier with backticks. */
    public static String id(String name) {
        return "`" + name.replace("`", "``") + "`";
    }

    public static String id(String db, String table) {
        return id(db) + "." + id(table);
    }

    /** Render a Java value as a MySQL literal (used for SQL export). */
    public static String literal(Object v) {
        if (v == null) return "NULL";
        if (v instanceof Boolean b) return b ? "1" : "0";
        if (v instanceof Number n && !(v instanceof BigDecimal)) return n.toString();
        if (v instanceof BigDecimal d) return d.toPlainString();
        if (v instanceof byte[] bytes) {
            return bytes.length == 0 ? "''" : "0x" + HexFormat.of().formatHex(bytes);
        }
        String s = (v instanceof TemporalAccessor || v instanceof java.util.Date)
                ? v.toString().replace('T', ' ')
                : v.toString();
        return "'" + escape(s) + "'";
    }

    /** A column DEFAULT value: keywords and numbers stay raw, anything else is quoted. */
    public static String defaultExpr(String text) {
        String t = text.trim();
        if (t.matches("(?i)NULL|CURRENT_TIMESTAMP(\\(\\d?\\))?|NOW\\(\\)|-?\\d+(\\.\\d+)?")
                || (t.startsWith("(") && t.endsWith(")"))) {
            return t;
        }
        return literal(text);
    }

    public static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\0' -> sb.append("\\0");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\\' -> sb.append("\\\\");
                case '\'' -> sb.append("\\'");
                case '"' -> sb.append("\\\"");
                case '\u001a' -> sb.append("\\Z");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Plain text form of a value, MySQL style (1/0 for booleans, space in datetimes). */
    public static String text(Object v) {
        if (v == null) return "";
        if (v instanceof Boolean b) return b ? "1" : "0";
        if (v instanceof TemporalAccessor) return v.toString().replace('T', ' ');
        if (v instanceof java.sql.Timestamp ts) {
            String s = ts.toString();
            return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
        }
        return v.toString();
    }

    /** Text shown in the grid for a value. */
    public static String display(Object v) {
        if (v == null) return "NULL";
        if (v instanceof byte[] b) return "[BLOB - " + b.length + " B]";
        return text(v);
    }
}
