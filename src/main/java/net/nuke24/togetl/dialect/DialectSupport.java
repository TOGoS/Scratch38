package net.nuke24.togetl.dialect;

import java.io.IOException;

import net.nuke24.togetl.etl.ImportReport;
import net.nuke24.togetl.etl.SourceDocument;
import net.nuke24.togetl.ident.Identifiers;
import net.nuke24.togetl.rdf.BlankNode;
import net.nuke24.togetl.rdf.IRI;
import net.nuke24.togetl.rdf.Literal;
import net.nuke24.togetl.rdf.Location;
import net.nuke24.togetl.rdf.Node;
import net.nuke24.togetl.rdf.Statement;
import net.nuke24.togetl.rdf.StatementConsumer;
import net.nuke24.togetl.tef.TEFComment;
import net.nuke24.togetl.tef.TEFDirective;
import net.nuke24.togetl.tef.TEFEntry;
import net.nuke24.togetl.tef.TEFHeader;
import net.nuke24.togetl.tef.TEFSink;
import net.nuke24.togetl.tef.TEFSpan;
import net.nuke24.togetl.vocab.Vocab;

/**
 * Statement helpers shared by TEF dialects.
 */
public final class DialectSupport {
	private DialectSupport() {
	}
	
	/**
	 * Handles the document-level parts of one import: metadata first, then
	 * comments and directives as they are read, then one call per entry.
	 * Subclasses supply the per-entry mapping.
	 */
	public abstract static class DocumentSession implements TEFSink {
		protected final SourceDocument source;
		protected final StatementConsumer out;
		protected final ImportReport report;
		private final String scope;
		private int commentCount;
		private int directiveCount;
		private int entryCount;
		
		protected DocumentSession(
				SourceDocument source,
				boolean bom,
				StatementConsumer out,
				ImportReport report
		) throws IOException {
			this.source = source;
			this.out = out;
			this.report = report;
			this.scope = Identifiers.documentHash(source.getDocumentIri());
			emitDocumentMetadata(source, bom, out);
		}
		
		@Override
		public final void documentComment(TEFComment comment) throws IOException {
			emitDocumentComment(source, out, scope, commentCount++, comment);
		}
		
		@Override
		public final void documentDirective(TEFDirective directive) throws IOException {
			emitDocumentDirective(source, out, scope, directiveCount++, directive, report);
		}
		
		@Override
		public final void entry(TEFEntry entry) throws IOException {
			mapEntry(entry, entryCount++);
		}
		
		/** Maps one entry; {@code entryIndex} counts entries in document order from zero. */
		protected abstract void mapEntry(TEFEntry entry, int entryIndex) throws IOException;
	}
	
	public static void emit(
			SourceDocument source,
			StatementConsumer out,
			Location location,
			Node subject,
			IRI predicate,
			Node object
	) throws IOException {
		out.accept(new Statement(source.getDocumentImportIri(), location, subject, predicate, object));
	}
	
	public static Location locationOf(TEFSpan span) {
		return span == null ? Location.none() : span.toLocation();
	}
	
	public static void emitDocumentMetadata(
			SourceDocument source,
			boolean bom,
			StatementConsumer out
	) throws IOException {
		IRI doc = IRI.of(source.getDocumentIri());
		IRI documentImport = IRI.of(source.getDocumentImportIri());
		Location none = Location.none();
		emit(source, out, none, doc, Vocab.RDF_TYPE, Vocab.TOGETL_DOCUMENT);
		emit(source, out, none, doc, Vocab.TOGETL_SOURCE_URL, Literal.of(source.getSourceIri()));
		emit(source, out, none, doc, Vocab.TOGETL_SHA256, Literal.of(source.getSha256()));
		emit(source, out, none, doc, Vocab.TOGETL_BYTE_LENGTH,
				Literal.of(Long.toString(source.getByteLength()), Vocab.XSD_INTEGER));
		emit(source, out, none, documentImport, Vocab.RDF_TYPE, Vocab.TOGETL_DOCUMENT_IMPORT);
		emit(source, out, none, documentImport, Vocab.TOGETL_SOURCE_DOCUMENT, doc);
		emit(source, out, none, documentImport, Vocab.TOGETL_FORMAT, Literal.of(source.getFormat()));
		emit(source, out, none, documentImport, Vocab.TOGETL_PARSER_VERSION,
				Literal.of(source.getParserVersion()));
		emit(source, out, none, documentImport, Vocab.TOGETL_HAS_BOM,
				Literal.of(bom ? "true" : "false", Vocab.XSD_BOOLEAN));
	}
	
	public static void emitDocumentComment(
			SourceDocument source,
			StatementConsumer out,
			String scope,
			int index,
			TEFComment comment
	) throws IOException {
		IRI doc = IRI.of(source.getDocumentIri());
		Location location = locationOf(comment.getSpan());
		BlankNode node = BlankNode.of("tef-doc-comment-" + scope + "-" + index);
		emit(source, out, location, doc, Vocab.TEF_COMMENT_LINK, node);
		emit(source, out, location, node, Vocab.RDF_TYPE, Vocab.TEF_COMMENT);
		emit(source, out, location, node, Vocab.TEF_TEXT, Literal.of(comment.getText()));
	}
	
	public static void emitDocumentDirective(
			SourceDocument source,
			StatementConsumer out,
			String scope,
			int index,
			TEFDirective directive,
			ImportReport report
	) throws IOException {
		IRI doc = IRI.of(source.getDocumentIri());
		Location location = locationOf(directive.getSpan());
		BlankNode node = BlankNode.of("tef-doc-directive-" + scope + "-" + index);
		emit(source, out, location, doc, Vocab.TEF_DIRECTIVE_LINK, node);
		emit(source, out, location, node, Vocab.RDF_TYPE, Vocab.TEF_DIRECTIVE);
		emit(source, out, location, node, Vocab.TEF_NAME, Literal.of(directive.getName()));
		emit(source, out, location, node, Vocab.TEF_VALUE, Literal.of(directive.getValue()));
		emit(source, out, location, node, Vocab.TEF_RAW, Literal.of(directive.getRaw()));
		if (!"format".equals(directive.getName()) && !"alias".equals(directive.getName())) {
			report.unmappedDirective(directive.getName(), location);
		}
	}
	
	public static IRI emitGenericEntry(
			SourceDocument source,
			StatementConsumer out,
			TEFEntry entry,
			int entryIndex,
			ImportReport report
	) throws IOException {
		String scope = Identifiers.documentHash(source.getDocumentIri());
		IRI entryIri = IRI.of(Identifiers.tefEntryIri(source.getDocumentIri(), entryIndex));
		Location location = locationOf(entry.getSpan());
		
		emit(source, out, location, entryIri, Vocab.RDF_TYPE, Vocab.TEF_ENTRY);
		emit(source, out, location, entryIri, Vocab.TOGETL_DOCUMENT_LINK, IRI.of(source.getDocumentIri()));
		emit(source, out, location, entryIri, Vocab.TEF_ENTRY_INDEX,
				Literal.of(Integer.toString(entryIndex), Vocab.XSD_INTEGER));
		if (!entry.getType().isEmpty()) {
			emit(source, out, location, entryIri, Vocab.TEF_ENTRY_TYPE, Literal.of(entry.getType()));
			report.unmappedEntryType(entry.getType(), location);
		}
		if (!entry.getValue().isEmpty()) {
			emit(source, out, location, entryIri, Vocab.TEF_ENTRY_VALUE, Literal.of(entry.getValue()));
		}
		if (!entry.getContent().isEmpty()) {
			emit(source, out, location, entryIri, Vocab.TEF_CONTENT, Literal.of(entry.getContent()));
		}
		
		for (int i = 0; i < entry.getHeaders().size(); i++) {
			TEFHeader header = entry.getHeaders().get(i);
			emitGenericHeader(source, out, scope, entryIri, entryIndex, i, header);
			report.unmappedField(entry.getType(), header.getKey(), locationOf(header.getSpan()));
		}
		for (int i = 0; i < entry.getComments().size(); i++) {
			emitGenericComment(source, out, scope, entryIri, entryIndex, i, entry.getComments().get(i));
		}
		for (int i = 0; i < entry.getDirectives().size(); i++) {
			emitGenericDirective(source, out, scope, entryIri, entryIndex, i, entry.getDirectives().get(i));
		}
		return entryIri;
	}
	
	public static void emitGenericHeader(
			SourceDocument source,
			StatementConsumer out,
			String scope,
			IRI subject,
			int entryIndex,
			int headerIndex,
			TEFHeader header
	) throws IOException {
		Location location = locationOf(header.getSpan());
		BlankNode node = BlankNode.of(
				"tef-header-" + scope + "-" + entryIndex + "-" + headerIndex);
		emit(source, out, location, subject, Vocab.TEF_HEADER_LINK, node);
		emit(source, out, location, node, Vocab.RDF_TYPE, Vocab.TEF_HEADER);
		emit(source, out, location, node, Vocab.TEF_KEY, Literal.of(header.getKey()));
		emit(source, out, location, node, Vocab.TEF_VALUE, Literal.of(header.getValue()));
		emit(source, out, location, node, Vocab.TEF_INDEX,
				Literal.of(Integer.toString(headerIndex), Vocab.XSD_INTEGER));
	}
	
	public static void emitGenericComment(
			SourceDocument source,
			StatementConsumer out,
			String scope,
			IRI subject,
			int entryIndex,
			int commentIndex,
			TEFComment comment
	) throws IOException {
		Location location = locationOf(comment.getSpan());
		BlankNode node = BlankNode.of(
				"tef-comment-" + scope + "-" + entryIndex + "-" + commentIndex);
		emit(source, out, location, subject, Vocab.TEF_COMMENT_LINK, node);
		emit(source, out, location, node, Vocab.RDF_TYPE, Vocab.TEF_COMMENT);
		emit(source, out, location, node, Vocab.TEF_TEXT, Literal.of(comment.getText()));
	}
	
	public static void emitGenericDirective(
			SourceDocument source,
			StatementConsumer out,
			String scope,
			IRI subject,
			int entryIndex,
			int directiveIndex,
			TEFDirective directive
	) throws IOException {
		Location location = locationOf(directive.getSpan());
		BlankNode node = BlankNode.of(
				"tef-directive-" + scope + "-" + entryIndex + "-" + directiveIndex);
		emit(source, out, location, subject, Vocab.TEF_DIRECTIVE_LINK, node);
		emit(source, out, location, node, Vocab.RDF_TYPE, Vocab.TEF_DIRECTIVE);
		emit(source, out, location, node, Vocab.TEF_NAME, Literal.of(directive.getName()));
		emit(source, out, location, node, Vocab.TEF_VALUE, Literal.of(directive.getValue()));
		emit(source, out, location, node, Vocab.TEF_RAW, Literal.of(directive.getRaw()));
	}
}
