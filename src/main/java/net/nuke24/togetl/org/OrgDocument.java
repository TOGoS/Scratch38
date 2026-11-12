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
	
	public OrgDocument(String sourceIri, long byteLength, boolean bom, List<OrgHeading> headings) {
		this.sourceIri = sourceIri;
		this.byteLength = byteLength;
		this.bom = bom;
		this.headings = Collections.unmodifiableList(new ArrayList<OrgHeading>(headings));
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
}
