package net.nuke24.togetl.tef;

import net.nuke24.togetl.rdf.Location;

/**
 * A half-open byte span with start and end line/column positions.
 *
 * <p>Columns are counted in bytes, not Unicode code points. That keeps span
 * computation cheap and deterministic; the source files are overwhelmingly
 * ASCII, and the line number is what matters most for provenance.</p>
 */
public final class TEFSpan {
    public static final TEFSpan NONE = new TEFSpan(-1, -1, 0, 0, 0, 0);

    public final long byteStart;
    public final long byteEnd;
    public final int startLine;
    public final int startColumn;
    public final int endLine;
    public final int endColumn;

    public TEFSpan(
            long byteStart,
            long byteEnd,
            int startLine,
            int startColumn,
            int endLine,
            int endColumn
    ) {
        this.byteStart = byteStart;
        this.byteEnd = byteEnd;
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.endLine = endLine;
        this.endColumn = endColumn;
    }

    public static TEFSpan point(long byteOffset, int line, int column) {
        return new TEFSpan(byteOffset, byteOffset, line, column, line, column);
    }

    public static TEFSpan join(TEFSpan start, TEFSpan end) {
        if (start == null || start == NONE) return end == null ? NONE : end;
        if (end == null || end == NONE) return start;
        return new TEFSpan(
                start.byteStart,
                end.byteEnd,
                start.startLine,
                start.startColumn,
                end.endLine,
                end.endColumn);
    }

    public Location toLocation() {
        if (this == NONE || startLine < 1 || startColumn < 1) {
            return Location.none();
        }
        if (startLine == endLine && startColumn == endColumn) {
            return Location.at(startLine, startColumn);
        }
        return Location.span(startLine, startColumn, endLine, endColumn);
    }

    @Override
    public String toString() {
        if (this == NONE) return "-";
        return "L" + startLine + "C" + startColumn + "...L" + endLine + "C" + endColumn
                + "@" + byteStart + ".." + byteEnd;
    }
}
