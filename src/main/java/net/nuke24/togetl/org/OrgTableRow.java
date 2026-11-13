package net.nuke24.togetl.org;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One row of an Org table, including separator rows.
 */
public final class OrgTableRow {
	private final List<String> cells;
	private final boolean separator;
	private final int line;
	
	public OrgTableRow(List<String> cells, boolean separator, int line) {
		this.cells = Collections.unmodifiableList(new ArrayList<String>(cells));
		this.separator = separator;
		this.line = line;
	}
	
	public List<String> getCells() {
		return cells;
	}
	
	public String getCell(int index) {
		return index >= 0 && index < cells.size() ? cells.get(index) : "";
	}
	
	public int getCellCount() {
		return cells.size();
	}
	
	public boolean isSeparator() {
		return separator;
	}
	
	public int getLine() {
		return line;
	}
}
