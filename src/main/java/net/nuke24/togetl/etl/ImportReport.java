package net.nuke24.togetl.etl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
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
 * order so reports can be diffed. This class owns both the write-side
 * aggregation and the on-disk TSV format; {@link #readTsv(Reader, String)}
 * parses the same format that {@link #writeTsv(Writer)} produces.</p>
 */
public final class ImportReport {
	public static final String UNMAPPED_ENTRY_TYPE = "unmapped-entry-type";
	public static final String UNMAPPED_FIELD = "unmapped-field";
	public static final String UNMAPPED_DIRECTIVE = "unmapped-directive";
	public static final String CONFLICT = "conflict";
	public static final String AS_WRITTEN_ENTRY = "as-written-entry";
	public static final String AS_WRITTEN_FIELD = "as-written-field";
	public static final String INVALID_IRI = "invalid-iri";
	public static final String EMPTY_VALUE = "empty-value";
	public static final String UNPARSED_VALUE = "unparsed-value";
	
	public static final String TSV_HEADER =
			"kind\tentryType\tfield\tcount\tfirstLocation";
	
	private final Map<String, AggregatedRow> rows = new TreeMap<String, AggregatedRow>();
	
	public void unmappedEntryType(String entryType, Location location) {
		record(UNMAPPED_ENTRY_TYPE, entryType, "", location);
	}
	
	public void unmappedField(String entryType, String field, Location location) {
		record(UNMAPPED_FIELD, entryType, field, location);
	}
	
	public void unmappedDirective(String name, Location location) {
		record(UNMAPPED_DIRECTIVE, "", name, location);
	}
	
	public void conflict(String entryType, String field, Location location) {
		record(CONFLICT, entryType, field, location);
	}
	
	public void record(String kind, String entryType, String field, Location location) {
		if (kind == null || kind.isEmpty()) {
			throw new IllegalArgumentException("Report row kind must not be empty");
		}
		String key = kind + '\u0000' + entryType + '\u0000' + field;
		AggregatedRow row = rows.get(key);
		if (row == null) {
			row = new AggregatedRow(
					kind,
					entryType == null ? "" : entryType,
					field == null ? "" : field,
					location == null ? Location.none() : location);
			rows.put(key, row);
		}
		row.count++;
	}
	
	public List<Row> getRows() {
		List<Row> result = new ArrayList<Row>(rows.size());
		for (AggregatedRow row : rows.values()) {
			result.add(new Row(
					row.kind,
					row.entryType,
					row.field,
					row.count,
					row.firstLocation));
		}
		return Collections.unmodifiableList(result);
	}
	
	public int getRowCount() {
		return rows.size();
	}
	
	public int getOccurrenceCount() {
		int total = 0;
		for (AggregatedRow row : rows.values()) {
			total += row.count;
		}
		return total;
	}
	
	public void writeTsv(Writer out) throws IOException {
		out.write(TSV_HEADER);
		out.write('\n');
		for (Row row : getRows()) {
			out.write(clean(row.getKind()));
			out.write('\t');
			out.write(clean(row.getEntryType()));
			out.write('\t');
			out.write(clean(row.getField()));
			out.write('\t');
			out.write(Integer.toString(row.getCount()));
			out.write('\t');
			out.write(row.getFirstLocation().toString());
			out.write('\n');
		}
	}
	
	/**
	 * Parses a report TSV.
	 *
	 * <p>Header detection is structural: only a line exactly equal to
	 * {@link #TSV_HEADER} is skipped, so a data row whose kind happens to be
	 * {@code kind} is still read.</p>
	 *
	 * @param sourceName report file name, used in error messages; may be null.
	 */
	public static List<Row> readTsv(Reader in, String sourceName)
			throws IOException, ReportParseException {
		BufferedReader reader = in instanceof BufferedReader
				? (BufferedReader) in
				: new BufferedReader(in);
		List<Row> rows = new ArrayList<Row>();
		int lineNumber = 0;
		String line;
		while ((line = reader.readLine()) != null) {
			lineNumber++;
			if (line.trim().isEmpty() || TSV_HEADER.equals(line)) {
				continue;
			}
			String[] columns = line.split("\t", -1);
			if (columns.length != 5) {
				throw new ReportParseException(
						sourceName,
						lineNumber,
						"expected 5 report columns, got " + columns.length);
			}
			int count;
			try {
				count = Integer.parseInt(columns[3]);
			} catch (NumberFormatException e) {
				throw new ReportParseException(
						sourceName,
						lineNumber,
						"invalid report count: " + columns[3],
						e);
			}
			Location location;
			try {
				location = Location.parse(columns[4]);
			} catch (IllegalArgumentException e) {
				throw new ReportParseException(
						sourceName,
						lineNumber,
						"invalid report location: " + columns[4],
						e);
			}
			try {
				rows.add(new Row(
						columns[0],
						columns[1],
						columns[2],
						count,
						location));
			} catch (IllegalArgumentException e) {
				throw new ReportParseException(
						sourceName,
						lineNumber,
						e.getMessage(),
						e);
			}
		}
		return rows;
	}
	
	private static String clean(String value) {
		return value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
	}
	
	private static final class AggregatedRow {
		final String kind;
		final String entryType;
		final String field;
		final Location firstLocation;
		int count = 0;
		
		AggregatedRow(
				String kind,
				String entryType,
				String field,
				Location firstLocation
		) {
			this.kind = kind;
			this.entryType = entryType;
			this.field = field;
			this.firstLocation = firstLocation;
		}
	}
	
	/**
	 * One report row. Instances are immutable so the same type can represent
	 * both aggregated write-side rows and rows read back from a report file.
	 */
	public static final class Row {
		private final String kind;
		private final String entryType;
		private final String field;
		private final int count;
		private final Location firstLocation;
		
		public Row(
				String kind,
				String entryType,
				String field,
				int count,
				Location firstLocation
		) {
			if (kind == null || kind.isEmpty()) {
				throw new IllegalArgumentException("Report row kind must not be empty");
			}
			if (count < 1) {
				throw new IllegalArgumentException("Report row count must be >= 1");
			}
			this.kind = kind;
			this.entryType = entryType == null ? "" : entryType;
			this.field = field == null ? "" : field;
			this.count = count;
			this.firstLocation = firstLocation == null ? Location.none() : firstLocation;
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
