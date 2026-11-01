package com.javamyadmin.db;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * Wraps one JDBC connection. Not thread safe: callers run everything through
 * the single DB worker thread in {@link com.javamyadmin.ui.Async}.
 */
public final class Database implements AutoCloseable {

    private final ConnectionInfo info;
    private final Connection conn;

    private Database(ConnectionInfo info, Connection conn) {
        this.info = info;
        this.conn = conn;
    }

    public static Database connect(ConnectionInfo info) throws SQLException {
        return new Database(info, DriverManager.getConnection(info.jdbcUrl(), info.user(), info.password()));
    }

    public ConnectionInfo info() {
        return info;
    }

    public String serverVersion() throws SQLException {
        return conn.getMetaData().getDatabaseProductVersion();
    }

    // ---------- listing ----------

    public List<String> databases() throws SQLException {
        return stringColumn("SHOW DATABASES");
    }

    public List<String> tables(String db) throws SQLException {
        return stringColumn("SHOW FULL TABLES FROM " + Sql.id(db) + " WHERE Table_type = 'BASE TABLE'");
    }

    public List<String> views(String db) throws SQLException {
        return stringColumn("SHOW FULL TABLES FROM " + Sql.id(db) + " WHERE Table_type = 'VIEW'");
    }

    public List<ColumnInfo> columns(String db, String table) throws SQLException {
        List<ColumnInfo> out = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SHOW FULL COLUMNS FROM " + Sql.id(db, table))) {
            while (rs.next()) {
                out.add(new ColumnInfo(
                        rs.getString("Field"),
                        rs.getString("Type"),
                        "YES".equals(rs.getString("Null")),
                        rs.getString("Key"),
                        rs.getString("Default"),
                        rs.getString("Extra"),
                        rs.getString("Comment")));
            }
        }
        return out;
    }

    public String createStatement(String db, String table) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SHOW CREATE TABLE " + Sql.id(db, table))) {
            return rs.next() ? rs.getString(2) : "";
        }
    }

    public long countRows(String db, String table) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + Sql.id(db, table))) {
            rs.next();
            return rs.getLong(1);
        }
    }

    // ---------- browsing ----------

    public QueryResult page(String db, String table, String orderBy, boolean desc,
                            long offset, int limit) throws SQLException {
        String sql = "SELECT * FROM " + Sql.id(db, table)
                + (orderBy != null ? " ORDER BY " + Sql.id(orderBy) + (desc ? " DESC" : " ASC") : "")
                + " LIMIT " + limit + " OFFSET " + offset;
        long t0 = System.nanoTime();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return read(rs, t0);
        }
    }

    /** Runs arbitrary SQL (may contain several statements) and returns the last result. */
    public QueryResult execute(String db, String sql) throws SQLException {
        if (db != null) conn.setCatalog(db);
        long t0 = System.nanoTime();
        try (Statement st = conn.createStatement()) {
            boolean isRs = st.execute(sql);
            QueryResult last = null;
            long totalUpdates = 0;
            while (true) {
                if (isRs) {
                    try (ResultSet rs = st.getResultSet()) {
                        last = read(rs, t0);
                    }
                } else {
                    long n = st.getLargeUpdateCount();
                    if (n == -1) break;
                    totalUpdates += n;
                }
                isRs = st.getMoreResults();
            }
            if (last != null) return last;
            return new QueryResult(List.of(), List.of(), totalUpdates, elapsed(t0));
        }
    }

    // ---------- row editing ----------

    public void insert(String db, String table, Map<String, Object> values) throws SQLException {
        if (values.isEmpty()) {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("INSERT INTO " + Sql.id(db, table) + " () VALUES ()");
            }
            return;
        }
        List<String> cols = new ArrayList<>(values.keySet());
        String sql = "INSERT INTO " + Sql.id(db, table) + " ("
                + String.join(", ", cols.stream().map(Sql::id).toList()) + ") VALUES ("
                + String.join(", ", cols.stream().map(c -> "?").toList()) + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (String c : cols) ps.setObject(i++, values.get(c));
            ps.executeUpdate();
        }
    }

    /**
     * Updates one row identified by {@code where} (primary key columns, or all
     * columns when the table has no key). Returns the affected row count.
     */
    public int update(String db, String table, Map<String, Object> values,
                      Map<String, Object> where) throws SQLException {
        if (values.isEmpty()) return 0;
        List<String> setCols = new ArrayList<>(values.keySet());
        List<String> whereCols = new ArrayList<>(where.keySet());
        String sql = "UPDATE " + Sql.id(db, table) + " SET "
                + String.join(", ", setCols.stream().map(c -> Sql.id(c) + " = ?").toList())
                + whereClause(whereCols) + " LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (String c : setCols) ps.setObject(i++, values.get(c));
            for (String c : whereCols) {
                Object v = where.get(c);
                ps.setObject(i++, v);
                ps.setObject(i++, v);
            }
            return ps.executeUpdate();
        }
    }

    public int delete(String db, String table, Map<String, Object> where) throws SQLException {
        List<String> whereCols = new ArrayList<>(where.keySet());
        String sql = "DELETE FROM " + Sql.id(db, table) + whereClause(whereCols) + " LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (String c : whereCols) {
                Object v = where.get(c);
                ps.setObject(i++, v);
                ps.setObject(i++, v);
            }
            return ps.executeUpdate();
        }
    }

    /** NULL-safe match: (col = ? OR (col IS NULL AND ? IS NULL)). Each column binds twice. */
    private static String whereClause(List<String> cols) {
        return " WHERE " + String.join(" AND ", cols.stream()
                .map(c -> "(" + Sql.id(c) + " = ? OR (" + Sql.id(c) + " IS NULL AND ? IS NULL))")
                .toList());
    }

    // ---------- import ----------

    public record ImportResult(int executed, List<String> errors, boolean cancelled) {}

    /**
     * Runs every statement of an SQL script. {@code db} (may be null) is the
     * default database; the script can switch with USE.
     */
    public ImportResult importSql(String db, Reader script, boolean disableForeignKeys,
                                  boolean continueOnError, BooleanSupplier cancelled,
                                  IntConsumer onStatement) throws SQLException, IOException {
        if (db != null) conn.setCatalog(db);
        SqlScriptReader reader = new SqlScriptReader(script);
        List<String> errors = new ArrayList<>();
        int executed = 0;
        boolean wasCancelled = false;
        try (Statement st = conn.createStatement()) {
            if (disableForeignKeys) st.execute("SET FOREIGN_KEY_CHECKS = 0");
            try {
                String sql;
                while ((sql = reader.next()) != null) {
                    if (cancelled.getAsBoolean()) {
                        wasCancelled = true;
                        break;
                    }
                    try {
                        boolean isRs = st.execute(sql);
                        while (true) {
                            if (isRs) st.getResultSet().close();
                            else if (st.getUpdateCount() == -1) break;
                            isRs = st.getMoreResults();
                        }
                        executed++;
                        onStatement.accept(executed);
                    } catch (SQLException e) {
                        String shortSql = sql.length() > 300 ? sql.substring(0, 300) + "…" : sql;
                        errors.add("Line " + reader.line() + ": " + e.getMessage() + "\n    " + shortSql);
                        if (!continueOnError) break;
                    }
                }
            } finally {
                if (disableForeignKeys) st.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
        }
        return new ImportResult(executed, errors, wasCancelled);
    }

    // ---------- export ----------

    public void exportSql(String db, List<String> tables, boolean structure, boolean data,
                          Writer out) throws SQLException, IOException {
        out.write("-- FxMyAdmin SQL dump\n");
        out.write("-- Host: " + info.host() + "   Database: " + db + "\n");
        out.write("-- Server version: " + serverVersion() + "\n");
        out.write("-- Generated: " + LocalDateTime.now().withNano(0) + "\n\n");
        out.write("SET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS = 0;\n\n");

        for (String table : tables) {
            out.write("-- --------------------------------------------------------\n");
            out.write("-- Table " + Sql.id(table) + "\n\n");
            if (structure) {
                out.write("DROP TABLE IF EXISTS " + Sql.id(table) + ";\n");
                out.write(createStatement(db, table) + ";\n\n");
            }
            if (data) writeInserts(db, table, out);
        }
        out.write("SET FOREIGN_KEY_CHECKS = 1;\n");
    }

    private void writeInserts(String db, String table, Writer out) throws SQLException, IOException {
        final int batch = 100;
        try (Statement st = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            st.setFetchSize(Integer.MIN_VALUE); // stream rows instead of loading the whole table
            try (ResultSet rs = st.executeQuery("SELECT * FROM " + Sql.id(db, table))) {
                ResultSetMetaData md = rs.getMetaData();
                int n = md.getColumnCount();
                StringBuilder header = new StringBuilder("INSERT INTO " + Sql.id(table) + " (");
                for (int i = 1; i <= n; i++) {
                    if (i > 1) header.append(", ");
                    header.append(Sql.id(md.getColumnLabel(i)));
                }
                header.append(") VALUES\n");

                int inBatch = 0;
                while (rs.next()) {
                    out.write(inBatch == 0 ? header.toString() : ",\n");
                    out.write("(");
                    for (int i = 1; i <= n; i++) {
                        if (i > 1) out.write(", ");
                        out.write(Sql.literal(rs.getObject(i)));
                    }
                    out.write(")");
                    if (++inBatch == batch) {
                        out.write(";\n");
                        inBatch = 0;
                    }
                }
                if (inBatch > 0) out.write(";\n");
                out.write("\n");
            }
        }
    }

    public void exportCsv(String db, String table, Writer out) throws SQLException, IOException {
        try (Statement st = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            st.setFetchSize(Integer.MIN_VALUE);
            try (ResultSet rs = st.executeQuery("SELECT * FROM " + Sql.id(db, table))) {
                ResultSetMetaData md = rs.getMetaData();
                int n = md.getColumnCount();
                for (int i = 1; i <= n; i++) {
                    if (i > 1) out.write(',');
                    out.write(csv(md.getColumnLabel(i)));
                }
                out.write("\r\n");
                while (rs.next()) {
                    for (int i = 1; i <= n; i++) {
                        if (i > 1) out.write(',');
                        Object v = rs.getObject(i);
                        if (v instanceof byte[]) v = rs.getString(i);
                        out.write(v == null ? "" : csv(Sql.text(v)));
                    }
                    out.write("\r\n");
                }
            }
        }
    }

    private static String csv(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    // ---------- internals ----------

    private List<String> stringColumn(String sql) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) out.add(rs.getString(1));
        }
        return out;
    }

    private static QueryResult read(ResultSet rs, long t0) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int n = md.getColumnCount();
        List<String> cols = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) cols.add(md.getColumnLabel(i));
        List<List<Object>> rows = new ArrayList<>();
        while (rs.next()) {
            List<Object> row = new ArrayList<>(n);
            for (int i = 1; i <= n; i++) row.add(rs.getObject(i));
            rows.add(row);
        }
        return new QueryResult(cols, rows, -1, elapsed(t0));
    }

    private static long elapsed(long t0) {
        return (System.nanoTime() - t0) / 1_000_000;
    }

    @Override
    public void close() {
        try {
            conn.close();
        } catch (SQLException ignored) {
        }
    }
}
