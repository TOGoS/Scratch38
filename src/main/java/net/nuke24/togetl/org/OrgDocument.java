package net.nuke24.togetl.org;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parsed Org document.
 */
public final class OrgDocument {
	private final String sourceIri;
	private final long byteLength;
	private final boolean bom;
	private final List<OrgHeading> headings;
	private final List<OrgDirective> directives;
	private final List<OrgTable> tables;
	
	public OrgDocument(
			String sourceIri,
			long byteLength,
			boolean bom,
			List<OrgHeading> headings,
			List<OrgDirective> directives,
			List<OrgTable> tables
	) {
		this.sourceIri = sourceIri;
		this.byteLength = byteLength;
		this.bom = bom;
		this.headings = Collections.unmodifiableList(new ArrayList<OrgHeading>(headings));
		this.directives = Collections.unmodifiableList(new ArrayList<OrgDirective>(directives));
		this.tables = Collections.unmodifiableList(new ArrayList<OrgTable>(tables));
	}
	
	public String getSourceIri() {
		return sourceIri;
	}
	
	public long getByteLength() {
		return byteLength;
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
}
