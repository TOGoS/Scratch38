package net.nuke24.togetl.org;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An Org table, optionally associated with a {@code #+NAME:} or
 * {@code #+CAPTION:} directive.
 */
public final class OrgTable {
	private final String name;
	private final String caption;
	private final int line;
	private final int headingIndex;
	private final List<OrgTableRow> rows = new ArrayList<OrgTableRow>();
	
	public OrgTable(String name, String caption, int line, int headingIndex) {
		this.name = name;
		this.caption = caption;
		this.line = line;
		this.headingIndex = headingIndex;
	}
	
	public String getName() {
		return name;
	}
	
	public String getCaption() {
		return caption;
	}
	
	public int getLine() {
		return line;
	}
	
	/** Index of the enclosing heading, or -1 before the first heading. */
	public int getHeadingIndex() {
		return headingIndex;
	}
	
	public List<OrgTableRow> getRows() {
		return Collections.unmodifiableList(rows);
	}
	
	public void addRow(OrgTableRow row) {
		rows.add(row);
	}
	
	public int getWidth() {
		int width = 0;
		for (OrgTableRow row : rows) {
			width = Math.max(width, row.getCellCount());
		}
		return width;
	}
}
