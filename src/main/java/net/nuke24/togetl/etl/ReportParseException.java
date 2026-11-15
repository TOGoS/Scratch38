package net.nuke24.togetl.etl;

/**
 * Thrown when an importer report TSV is malformed. The message names the
 * report file and line.
 */
public final class ReportParseException extends Exception {
	private static final long serialVersionUID = 1L;
	
	private final String sourceName;
	private final int lineNumber;
	
	public ReportParseException(String sourceName, int lineNumber, String message) {
		super(formatMessage(sourceName, lineNumber, message));
		this.sourceName = sourceName;
		this.lineNumber = lineNumber;
	}
	
	public ReportParseException(
			String sourceName,
			int lineNumber,
			String message,
			Throwable cause
	) {
		super(formatMessage(sourceName, lineNumber, message), cause);
		this.sourceName = sourceName;
		this.lineNumber = lineNumber;
	}
	
	public String getSourceName() {
		return sourceName;
	}
	
	public int getLineNumber() {
		return lineNumber;
	}
	
	private static String formatMessage(String sourceName, int lineNumber, String message) {
		String name = sourceName == null || sourceName.isEmpty() ? "<report>" : sourceName;
		return name + " line " + lineNumber + ": " + message;
	}
}
