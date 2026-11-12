package net.nuke24.togetl.org;

/**
 * Checkbox marker parsed from Org text.
 */
public final class OrgCheckbox {
	private final String state;
	private final int line;
	private final int column;
	
	public OrgCheckbox(String state, int line, int column) {
		this.state = state;
		this.line = line;
		this.column = column;
	}
	
	public String getState() {
		return state;
	}
	
	public int getLine() {
		return line;
	}
	
	public int getColumn() {
		return column;
	}
}
