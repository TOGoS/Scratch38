package net.nuke24.togetl.db;

/**
 * One row from an importer report TSV, such as a conflict or unmapped field.
 */
public final class ConflictRow {
    private final String kind;
    private final String entryType;
    private final String field;
    private final int count;
    private final String firstLocation;

    public ConflictRow(
            String kind,
            String entryType,
            String field,
            int count,
            String firstLocation
    ) {
        if (kind == null || kind.isEmpty()) {
            throw new IllegalArgumentException("Report row kind must not be empty");
        }
        this.kind = kind;
        this.entryType = entryType == null ? "" : entryType;
        this.field = field == null ? "" : field;
        this.count = count;
        this.firstLocation = firstLocation == null ? "" : firstLocation;
    }

    public String getKind() {
        return kind;
    }

    public String getEntryType() {
        return entryType;
    }

    public String getField() {
        return field;
    }

    public int getCount() {
        return count;
    }

    public String getFirstLocation() {
        return firstLocation;
    }
}
