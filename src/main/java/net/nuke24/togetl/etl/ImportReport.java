package net.nuke24.togetl.etl;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.nuke24.togetl.rdf.Location;

/**
 * Aggregated report of source constructs that the selected dialect did not map
 * to domain terms.
 *
 * <p>Rows are keyed by kind, entry type, and field, and are emitted in a stable
 * order so reports can be diffed.</p>
 */
public final class ImportReport {
	private final Map<String, Row> rows = new TreeMap<String, Row>();
	
	public void unmappedEntryType(String entryType, Location location) {
		record("unmapped-entry-type", entryType, "", location);
	}
	
	public void unmappedField(String entryType, String field, Location location) {
		record("unmapped-field", entryType, field, location);
	}
	
	public void unmappedDirective(String name, Location location) {
		record("unmapped-directive", "", name, location);
	}
	
	public void conflict(String entryType, String field, Location location) {
		record("conflict", entryType, field, location);
	}
	
	public void record(String kind, String entryType, String field, Location location) {
		String key = kind + '\u0000' + entryType + '\u0000' + field;
		Row row = rows.get(key);
		if (row == null) {
			row = new Row(kind, entryType, field, location == null ? Location.none() : location);
			rows.put(key, row);
		}
		row.count++;
	}
	
	public List<Row> getRows() {
		return Collections.unmodifiableList(new ArrayList<Row>(rows.values()));
	}
	
	public int getRowCount() {
		return rows.size();
	}
	
	public int getOccurrenceCount() {
		int total = 0;
		for (Row row : rows.values()) {
			total += row.count;
		}
		return total;
	}
	
	public void writeTsv(Writer out) throws IOException {
		out.write("kind\tentryType\tfield\tcount\tfirstLocation\n");
		for (Row row : rows.values()) {
			out.write(clean(row.kind));
			out.write('\t');
			out.write(clean(row.entryType));
			out.write('\t');
			out.write(clean(row.field));
			out.write('\t');
			out.write(Integer.toString(row.count));
			out.write('\t');
			out.write(row.firstLocation.toString());
			out.write('\n');
		}
	}
	
	private static String clean(String value) {
		return value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
	}
	
	public static final class Row {
		private final String kind;
		private final String entryType;
		private final String field;
		private final Location firstLocation;
		private int count;
		
		Row(String kind, String entryType, String field, Location firstLocation) {
			this.kind = kind;
			this.entryType = entryType;
			this.field = field;
			this.firstLocation = firstLocation;
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
		
		public Location getFirstLocation() {
			return firstLocation;
		}
	}
}
