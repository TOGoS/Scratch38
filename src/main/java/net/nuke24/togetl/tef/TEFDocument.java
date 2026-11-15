package net.nuke24.togetl.tef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The result of reading one TEF document.
 */
public final class TEFDocument {
	// TODO: document entity ID. Documents may want an entity ID in addition to the
	// hash-based ID of a specific snapshot of the file.
	private final String sourceIri;
	private final long byteLength;
	private final boolean bom;
	private final List<TEFComment> comments;
	private final List<TEFDirective> directives;
	private final List<TEFEntry> entries;
	
	public TEFDocument(
			String sourceIri,
			long byteLength,
			boolean bom,
			List<TEFComment> comments,
			List<TEFDirective> directives,
			List<TEFEntry> entries
	) {
		this.sourceIri = sourceIri;
		this.byteLength = byteLength;
		this.bom = bom;
		this.comments = Collections.unmodifiableList(new ArrayList<TEFComment>(comments));
		this.directives = Collections.unmodifiableList(new ArrayList<TEFDirective>(directives));
		this.entries = Collections.unmodifiableList(new ArrayList<TEFEntry>(entries));
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
	
	public List<TEFComment> getComments() {
		return comments;
	}
	
	public List<TEFDirective> getDirectives() {
		return directives;
	}
	
	public List<TEFEntry> getEntries() {
		return entries;
	}
	
	public TEFDirective getDirective(String name) {
		for (TEFDirective directive : directives) {
			if (directive.getName().equals(name)) {
				return directive;
			}
		}
		return null;
	}
}
