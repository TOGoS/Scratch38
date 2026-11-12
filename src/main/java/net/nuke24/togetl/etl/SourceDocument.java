package net.nuke24.togetl.etl;

import net.nuke24.togetl.ident.Identifiers;

/**
 * Provenance for one source document in one document import.
 */
public final class SourceDocument {
	private final String sourceIri;
	private final String sha256;
	private final long byteLength;
	private final String format;
	private final String parserVersion;
	private final String documentIri;
	private final String documentImportIri;
	
	private SourceDocument(
			String sourceIri,
			String sha256,
			long byteLength,
			String format,
			String parserVersion
	) {
		this.sourceIri = sourceIri;
		this.sha256 = sha256;
		this.byteLength = byteLength;
		this.format = format;
		this.parserVersion = parserVersion;
		this.documentIri = Identifiers.documentIri(sourceIri);
		this.documentImportIri = Identifiers.documentImportIri(documentIri, parserVersion);
	}
	
	public static SourceDocument of(
			String sourceIri,
			byte[] content,
			String format,
			String parserVersion
	) {
		if (sourceIri == null || sourceIri.isEmpty()) {
			throw new IllegalArgumentException("sourceIri must not be empty");
		}
		if (content == null) {
			throw new IllegalArgumentException("content must not be null");
		}
		return new SourceDocument(
				sourceIri,
				Identifiers.sha256(content),
				content.length,
				format,
				parserVersion);
	}
	
	public String getSourceIri() {
		return sourceIri;
	}
	
	public String getSha256() {
		return sha256;
	}
	
	public long getByteLength() {
		return byteLength;
	}
	
	public String getFormat() {
		return format;
	}
	
	public String getParserVersion() {
		return parserVersion;
	}
	
	/** The minted source-document IRI. */
	public String getDocumentIri() {
		return documentIri;
	}
	
	/** The minted document-import IRI used as statement source. */
	public String getDocumentImportIri() {
		return documentImportIri;
	}

	/** @deprecated Use {@link #getDocumentImportIri()}. */
	@Deprecated
	public String getImportRunIri() {
		return documentImportIri;
	}
	
	/** @deprecated Use {@link #getDocumentIri()}. */
	@Deprecated
	public String getIri() {
		return documentIri;
	}
}
