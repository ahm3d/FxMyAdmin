package com.javamyadmin.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits an SQL script into single statements, streaming line by line.
 * Understands quotes, backticks, comments, MySQL conditional comments
 * (kept, the server runs them) and DELIMITER changes.
 */
public final class SqlScriptReader {

    private static final Pattern DELIMITER = Pattern.compile("(?i)^\\s*DELIMITER\\s+(\\S+)\\s*$");

    private enum State { NORMAL, SINGLE, DOUBLE, BACKTICK, BLOCK_KEEP, BLOCK_SKIP }

    private final BufferedReader in;
    private State state = State.NORMAL;
    private String delimiter = ";";
    private StringBuilder sb = new StringBuilder();
    private boolean started;

    private String line;      // current line being processed
    private int pos;          // position inside current line
    private int lineNo;       // 1-based number of current line
    private int startLine;    // line where the statement in progress began
    private int lastLine;     // line where the last returned statement began

    public SqlScriptReader(Reader reader) {
        this.in = reader instanceof BufferedReader b ? b : new BufferedReader(reader, 1 << 16);
    }

    /** Line number where the statement last returned by {@link #next()} started. */
    public int line() {
        return lastLine;
    }

    /** Returns the next statement without its delimiter, or null at end of input. */
    public String next() throws IOException {
        while (true) {
            if (line == null) {
                line = in.readLine();
                pos = 0;
                if (line == null) return finish();
                lineNo++;
                if (state == State.NORMAL && !started) {
                    Matcher m = DELIMITER.matcher(line);
                    if (m.matches()) {
                        delimiter = m.group(1);
                        line = null;
                        continue;
                    }
                }
            }
            String stmt = scanLine();
            if (stmt != null) return stmt;
        }
    }

    /** Scans the rest of the current line. Returns a statement if one ends on it. */
    private String scanLine() {
        String l = line;
        int n = l.length();
        while (pos < n) {
            char c = l.charAt(pos);
            char nx = pos + 1 < n ? l.charAt(pos + 1) : '\0';
            switch (state) {
                case NORMAL -> {
                    if (c == '#' || (c == '-' && nx == '-' && (pos + 2 >= n || Character.isWhitespace(l.charAt(pos + 2))))) {
                        pos = n; // line comment
                        continue;
                    }
                    if (c == '/' && nx == '*') {
                        char third = pos + 2 < n ? l.charAt(pos + 2) : '\0';
                        if (third == '!' || third == '+') {
                            state = State.BLOCK_KEEP;
                            append("/*");
                        } else {
                            state = State.BLOCK_SKIP;
                        }
                        pos += 2;
                        continue;
                    }
                    if (c == '\'') state = State.SINGLE;
                    else if (c == '"') state = State.DOUBLE;
                    else if (c == '`') state = State.BACKTICK;
                    append(c);
                    pos++;
                    if (endsWithDelimiter()) return emit();
                    continue;
                }
                case SINGLE, DOUBLE -> {
                    char q = state == State.SINGLE ? '\'' : '"';
                    append(c);
                    if (c == '\\' && pos + 1 < n) {
                        append(nx);
                        pos += 2;
                        continue;
                    }
                    if (c == q) {
                        if (nx == q) {
                            append(nx);
                            pos += 2;
                            continue;
                        }
                        state = State.NORMAL;
                    }
                }
                case BACKTICK -> {
                    append(c);
                    if (c == '`') {
                        if (nx == '`') {
                            append(nx);
                            pos += 2;
                            continue;
                        }
                        state = State.NORMAL;
                    }
                }
                case BLOCK_KEEP -> {
                    append(c);
                    if (c == '*' && nx == '/') {
                        append('/');
                        pos += 2;
                        state = State.NORMAL;
                        if (endsWithDelimiter()) return emit();
                        continue;
                    }
                }
                case BLOCK_SKIP -> {
                    if (c == '*' && nx == '/') {
                        pos += 2;
                        state = State.NORMAL;
                        if (started) sb.append(' ');
                        continue;
                    }
                }
            }
            pos++;
        }
        if (state != State.BLOCK_SKIP && started) sb.append('\n');
        line = null;
        return null;
    }

    private void append(char c) {
        if (!started) {
            if (Character.isWhitespace(c)) return;
            started = true;
            startLine = lineNo;
        }
        sb.append(c);
    }

    private void append(String s) {
        for (int i = 0; i < s.length(); i++) append(s.charAt(i));
    }

    private boolean endsWithDelimiter() {
        int d = delimiter.length();
        int len = sb.length();
        if (len < d) return false;
        for (int i = 0; i < d; i++) {
            if (sb.charAt(len - d + i) != delimiter.charAt(i)) return false;
        }
        return true;
    }

    private String emit() {
        String s = sb.substring(0, sb.length() - delimiter.length()).trim();
        lastLine = startLine;
        sb = new StringBuilder();
        started = false;
        if (s.isEmpty()) return scanLine(); // stray delimiter like ";;"
        return s;
    }

    private String finish() {
        String s = sb.toString().trim();
        lastLine = startLine;
        sb = new StringBuilder();
        started = false;
        return s.isEmpty() ? null : s;
    }
}
