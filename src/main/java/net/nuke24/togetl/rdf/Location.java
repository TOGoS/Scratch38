package net.nuke24.togetl.rdf;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A position or span in a source document.
 *
 * <p>TOGETLTSV locations are "l(line)c(column)" (zero-based),
 * "L(line)C(column)" (one-based), or "b(offset)" (zero-based byte offset),
 * optionally a span written "start...end". Internally line and column are
 * normalized to one-based values.</p>
 */
public final class Location {
    private static final Pattern LINE_COLUMN =
            Pattern.compile("([Ll])(\\d+)[Cc](\\d+)(?:\\.\\.\\.([Ll])(\\d+)[Cc](\\d+))?");
    private static final Pattern BYTE_OFFSET =
            Pattern.compile("b(\\d+)(?:\\.\\.\\.b(\\d+))?");

    private static final Location NONE =
            new Location(null, null, null, null, null, null);

    private final Integer line;
    private final Integer column;
    private final Integer endLine;
    private final Integer endColumn;
    private final Long byteOffset;
    private final Long endByteOffset;

    private Location(
            Integer line,
            Integer column,
            Integer endLine,
            Integer endColumn,
            Long byteOffset,
            Long endByteOffset
    ) {
        this.line = line;
        this.column = column;
        this.endLine = endLine;
        this.endColumn = endColumn;
        this.byteOffset = byteOffset;
        this.endByteOffset = endByteOffset;
    }

    public static Location none() {
        return NONE;
    }

    /** One-based line and column. */
    public static Location at(int line, int column) {
        checkPositive(line, "line");
        checkPositive(column, "column");
        return new Location(line, column, null, null, null, null);
    }

    /** Zero-based line and column. */
    public static Location atZeroBased(int line, int column) {
        if (line < 0) throw new IllegalArgumentException("line must be >= 0");
        if (column < 0) throw new IllegalArgumentException("column must be >= 0");
        return at(line + 1, column + 1);
    }

    /** One-based line and column span. */
    public static Location span(int line, int column, int endLine, int endColumn) {
        checkPositive(line, "line");
        checkPositive(column, "column");
        checkPositive(endLine, "endLine");
        checkPositive(endColumn, "endColumn");
        return new Location(line, column, endLine, endColumn, null, null);
    }

    public static Location byteOffset(long offset) {
        if (offset < 0) throw new IllegalArgumentException("offset must be >= 0");
        return new Location(null, null, null, null, Long.valueOf(offset), null);
    }

    public static Location byteSpan(long start, long end) {
        if (start < 0 || end < start) throw new IllegalArgumentException("invalid byte span");
        return new Location(null, null, null, null, Long.valueOf(start), Long.valueOf(end));
    }

    public static Location parse(String text) {
        if ("-".equals(text)) {
            return NONE;
        }
        Matcher m = LINE_COLUMN.matcher(text);
        if (m.matches()) {
            int startLine = Integer.parseInt(m.group(2));
            int startColumn = Integer.parseInt(m.group(3));
            if ("l".equals(m.group(1))) {
                startLine++;
                startColumn++;
            }
            if (m.group(4) == null) {
                return at(startLine, startColumn);
            }
            int endLine = Integer.parseInt(m.group(5));
            int endColumn = Integer.parseInt(m.group(6));
            if ("l".equals(m.group(4))) {
                endLine++;
                endColumn++;
            }
            return span(startLine, startColumn, endLine, endColumn);
        }
        m = BYTE_OFFSET.matcher(text);
        if (m.matches()) {
            long start = Long.parseLong(m.group(1));
            if (m.group(2) == null) {
                return byteOffset(start);
            }
            return byteSpan(start, Long.parseLong(m.group(2)));
        }
        throw new IllegalArgumentException("Invalid location: " + text);
    }

    private static void checkPositive(int value, String what) {
        if (value < 1) throw new IllegalArgumentException(what + " must be >= 1");
    }

    public boolean isNone() {
        return this == NONE;
    }

    public boolean isLineColumn() {
        return line != null;
    }

    public boolean isByteOffset() {
        return byteOffset != null;
    }

    public int getLine() {
        requireLineColumn();
        return line.intValue();
    }

    public int getColumn() {
        requireLineColumn();
        return column.intValue();
    }

    public int getEndLine() {
        requireLineColumn();
        if (endLine == null) throw new IllegalStateException("Location has no end line");
        return endLine.intValue();
    }

    public int getEndColumn() {
        requireLineColumn();
        if (endColumn == null) throw new IllegalStateException("Location has no end column");
        return endColumn.intValue();
    }

    public long getByteOffset() {
        if (byteOffset == null) throw new IllegalStateException("Location has no byte offset");
        return byteOffset.longValue();
    }

    public long getEndByteOffset() {
        if (endByteOffset == null) throw new IllegalStateException("Location has no end byte offset");
        return endByteOffset.longValue();
    }

    public boolean hasSpan() {
        return endLine != null || endByteOffset != null;
    }

    private void requireLineColumn() {
        if (line == null) throw new IllegalStateException("Location is not line/column based");
    }

    @Override
    public String toString() {
        if (isNone()) {
            return "-";
        }
        if (line != null) {
            String start = "L" + line + "C" + column;
            return endLine == null ? start : start + "...L" + endLine + "C" + endColumn;
        }
        String start = "b" + byteOffset;
        return endByteOffset == null ? start : start + "...b" + endByteOffset;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Location)) return false;
        Location that = (Location) other;
        return Objects.equals(line, that.line)
                && Objects.equals(column, that.column)
                && Objects.equals(endLine, that.endLine)
                && Objects.equals(endColumn, that.endColumn)
                && Objects.equals(byteOffset, that.byteOffset)
                && Objects.equals(endByteOffset, that.endByteOffset);
    }

    @Override
    public int hashCode() {
        return Objects.hash(line, column, endLine, endColumn, byteOffset, endByteOffset);
    }
}
