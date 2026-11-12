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
import net.nuke24.togetl.tef.TEFDocument;
import net.nuke24.togetl.tef.TEFEntry;
import net.nuke24.togetl.tef.TEFHeader;
import net.nuke24.togetl.tef.TEFIssue;
import net.nuke24.togetl.tef.TEFSpan;
import net.nuke24.togetl.vocab.Vocab;

/**
 * Statement helpers shared by TEF dialects.
 */
public final class DialectSupport {
	private DialectSupport() {
	}
	
	public static void emit(
			SourceDocument source,
			StatementConsumer out,
			Location location,
			Node subject,
			IRI predicate,
			Node object
	) throws IOException {
		out.accept(new Statement(source.getIri(), location, subject, predicate, object));
	}
	
	public static Location locationOf(TEFSpan span) {
		return span == null ? Location.none() : span.toLocation();
	}
	
	public static void emitDocumentMetadata(
			TEFDocument document,
			SourceDocument source,
			StatementConsumer out
	) throws IOException {
		IRI doc = IRI.of(source.getIri());
		Location none = Location.none();
		emit(source, out, none, doc, Vocab.RDF_TYPE, Vocab.TOGETL_DOCUMENT);
		emit(source, out, none, doc, Vocab.TOGETL_SOURCE_URL, Literal.of(source.getSourceIri()));
		emit(source, out, none, doc, Vocab.TOGETL_SHA256, Literal.of(source.getSha256()));
		emit(source, out, none, doc, Vocab.TOGETL_BYTE_LENGTH,
				Literal.of(Long.toString(source.getByteLength()), Vocab.XSD_INTEGER));
		emit(source, out, none, doc, Vocab.TOGETL_FORMAT, Literal.of(source.getFormat()));
		emit(source, out, none, doc, Vocab.TOGETL_PARSER_VERSION,
				Literal.of(source.getParserVersion()));
		emit(source, out, none, doc, Vocab.TOGETL_HAS_BOM,
				Literal.of(document.hasBom() ? "true" : "false", Vocab.XSD_BOOLEAN));
	}
	
	public static void emitDocumentAnnotations(
			TEFDocument document,
			SourceDocument source,
			StatementConsumer out,
			ImportReport report
	) throws IOException {
		String scope = Identifiers.documentHash(source.getIri());
		IRI doc = IRI.of(source.getIri());
		
		for (int i = 0; i < document.getComments().size(); i++) {
			TEFComment comment = document.getComments().get(i);
			Location location = locationOf(comment.getSpan());
			BlankNode node = BlankNode.of("tef-doc-comment-" + scope + "-" + i);
			emit(source, out, location, doc, Vocab.TEF_COMMENT_LINK, node);
			emit(source, out, location, node, Vocab.RDF_TYPE, Vocab.TEF_COMMENT);
			emit(source, out, location, node, Vocab.TEF_TEXT, Literal.of(comment.getText()));
		}
		
		for (int i = 0; i < document.getDirectives().size(); i++) {
			TEFDirective directive = document.getDirectives().get(i);
			Location location = locationOf(directive.getSpan());
			BlankNode node = BlankNode.of("tef-doc-directive-" + scope + "-" + i);
			emit(source, out, location, doc, Vocab.TEF_DIRECTIVE_LINK, node);
			emit(source, out, location, node, Vocab.RDF_TYPE, Vocab.TEF_DIRECTIVE);
			emit(source, out, location, node, Vocab.TEF_NAME, Literal.of(directive.getName()));
			emit(source, out, location, node, Vocab.TEF_VALUE, Literal.of(directive.getValue()));
			emit(source, out, location, node, Vocab.TEF_RAW, Literal.of(directive.getRaw()));
			if (!"format".equals(directive.getName()) && !"alias".equals(directive.getName())) {
				report.unmappedDirective(directive.getName(), location);
			}
		}
	}
	
	public static void emitIssues(
			TEFDocument document,
			SourceDocument source,
			StatementConsumer out
	) throws IOException {
		String scope = Identifiers.documentHash(source.getIri());
		IRI doc = IRI.of(source.getIri());
		for (int i = 0; i < document.getIssues().size(); i++) {
			TEFIssue issue = document.getIssues().get(i);
			Location location = locationOf(issue.getSpan());
			BlankNode node = BlankNode.of("tef-issue-" + scope + "-" + i);
			emit(source, out, location, doc, Vocab.TOGETL_PARSE_ISSUE_LINK, node);
			emit(source, out, location, node, Vocab.RDF_TYPE, Vocab.TOGETL_PARSE_ISSUE);
			emit(source, out, location, node, Vocab.TOGETL_SEVERITY,
					Literal.of(issue.getSeverity().name().toLowerCase(java.util.Locale.ROOT)));
			emit(source, out, location, node, Vocab.TOGETL_CODE, Literal.of(issue.getCode()));
			emit(source, out, location, node, Vocab.TOGETL_MESSAGE, Literal.of(issue.getMessage()));
			if (issue.getRaw() != null) {
				emit(source, out, location, node, Vocab.TOGETL_RAW, Literal.of(issue.getRaw()));
			}
		}
	}
	
	public static IRI emitGenericEntry(
			TEFDocument document,
			SourceDocument source,
			TEFEntry entry,
			int entryIndex,
			StatementConsumer out,
			ImportReport report
	) throws IOException {
		String scope = Identifiers.documentHash(source.getIri());
		IRI entryIri = IRI.of(Identifiers.tefEntryIri(source.getIri(), entryIndex));
		Location location = locationOf(entry.getSpan());
		
		emit(source, out, location, entryIri, Vocab.RDF_TYPE, Vocab.TEF_ENTRY);
		emit(source, out, location, entryIri, Vocab.TOGETL_DOCUMENT_LINK, IRI.of(source.getIri()));
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
