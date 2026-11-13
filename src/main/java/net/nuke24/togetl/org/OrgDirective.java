package net.nuke24.togetl.org;

/**
 * A {@code #+NAME: value} style directive in an Org document.
 */
public final class OrgDirective {
	private final String name;
	private final String value;
	private final int line;
	private final int column;
	private final int headingIndex;
	
	public OrgDirective(String name, String value, int line, int column, int headingIndex) {
		this.name = name;
		this.value = value;
		this.line = line;
		this.column = column;
		this.headingIndex = headingIndex;
	}
	
	public String getName() {
		return name;
	}
	
	public String getValue() {
		return value;
	}
	
	public int getLine() {
		return line;
	}
	
	public int getColumn() {
		return column;
	}
	
	/** Index of the enclosing heading, or -1 before the first heading. */
	public int getHeadingIndex() {
		return headingIndex;
	}
}
