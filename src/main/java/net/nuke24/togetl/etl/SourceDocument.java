package net.nuke24.togetl.etl;

import net.nuke24.togetl.ident.Identifiers;

/**
 * Identity for one source document in one document import: the minted
 * document and import IRIs. Byte-level facts come separately as
 * {@link SourceProvenance}, once the input has been read.
 */
public final class SourceDocument {
	private final String sourceIri;
	private final String format;
	private final String parserVersion;
	private final String documentIri;
	private final String documentImportIri;
	
	private SourceDocument(String sourceIri, String format, String parserVersion) {
		this.sourceIri = sourceIri;
		this.format = format;
		this.parserVersion = parserVersion;
		this.documentIri = Identifiers.documentIri(sourceIri);
		this.documentImportIri = Identifiers.documentImportIri(documentIri, parserVersion);
	}
	
	public static SourceDocument of(String sourceIri, String format, String parserVersion) {
		if (sourceIri == null || sourceIri.isEmpty()) {
			throw new IllegalArgumentException("sourceIri must not be empty");
		}
		return new SourceDocument(sourceIri, format, parserVersion);
	}
	
	public String getSourceIri() {
		return sourceIri;
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
}
