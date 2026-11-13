package net.nuke24.togetl.org;

/**
 * Timestamp marker parsed from Org text.
 */
public final class OrgTimestamp {
	private final String text;
	private final int line;
	private final int column;
	
	public OrgTimestamp(String text, int line, int column) {
		this.text = text;
		this.line = line;
		this.column = column;
	}
	
	public String getText() {
		return text;
	}
	
	/**
	 * Active timestamps are written {@code <...>}; inactive ones {@code [...]}.
	 */
	public boolean isActive() {
		return text.startsWith("<");
	}
	
	public int getLine() {
		return line;
	}
	
	public int getColumn() {
		return column;
	}
}
