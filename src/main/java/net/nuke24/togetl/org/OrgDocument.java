package net.nuke24.togetl.org;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parsed Org document.
 */
public final class OrgDocument {
	private final boolean bom;
	private final List<OrgHeading> headings;
	private final List<OrgDirective> directives;
	private final List<OrgTable> tables;
	private final List<OrgDiagnostic> diagnostics;
	
	public OrgDocument(
			boolean bom,
			List<OrgHeading> headings,
			List<OrgDirective> directives,
			List<OrgTable> tables,
			List<OrgDiagnostic> diagnostics
	) {
		this.bom = bom;
		this.headings = Collections.unmodifiableList(new ArrayList<OrgHeading>(headings));
		this.directives = Collections.unmodifiableList(new ArrayList<OrgDirective>(directives));
		this.tables = Collections.unmodifiableList(new ArrayList<OrgTable>(tables));
		this.diagnostics = Collections.unmodifiableList(new ArrayList<OrgDiagnostic>(diagnostics));
	}
	
	public boolean hasBom() {
		return bom;
	}
	
	public List<OrgHeading> getHeadings() {
		return headings;
	}
	
	public List<OrgDirective> getDirectives() {
		return directives;
	}
	
	public List<OrgTable> getTables() {
		return tables;
	}
	
	/** Structural problems the parser recovered from, in source order of discovery. */
	public List<OrgDiagnostic> getDiagnostics() {
		return diagnostics;
	}
}
