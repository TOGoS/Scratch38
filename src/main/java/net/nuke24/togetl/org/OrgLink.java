package net.nuke24.togetl.org;

/**
 * Link marker parsed from Org text.
 */
public final class OrgLink {
	private final String target;
	private final String label;
	private final int line;
	private final int column;
	
	public OrgLink(String target, String label, int line, int column) {
		this.target = target;
		this.label = label;
		this.line = line;
		this.column = column;
	}
	
	public String getTarget() {
		return target;
	}
	
	public String getLabel() {
		return label;
	}
	
	public int getLine() {
		return line;
	}
	
	public int getColumn() {
		return column;
	}
}
