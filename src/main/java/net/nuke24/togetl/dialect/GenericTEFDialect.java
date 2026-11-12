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
 * Captures a TEF document structurally without assigning domain meaning.
 *
 * <p>Every entry, header, comment, and directive survives as a resource or
 * blank node, so the generic dialect never drops source data. The report lists
 * the entry types and header keys that a domain dialect would need to handle.</p>
 */
public final class GenericTEFDialect implements TEFDialect {
    public static final String NAME = "generic";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void map(
            TEFDocument document,
            SourceDocument source,
            StatementConsumer out,
            ImportReport report
    ) throws IOException {
        String documentIri = source.getIri();
        IRI doc = IRI.of(documentIri);
        String scope = Identifiers.documentHash(documentIri);

        emit(out, source, Location.none(), doc, Vocab.RDF_TYPE, Vocab.TOGETL_DOCUMENT);
        emit(out, source, Location.none(), doc, Vocab.TOGETL_SOURCE_URL,
                Literal.of(source.getSourceIri()));
        emit(out, source, Location.none(), doc, Vocab.TOGETL_SHA256,
                Literal.of(source.getSha256()));
        emit(out, source, Location.none(), doc, Vocab.TOGETL_BYTE_LENGTH,
                Literal.of(Long.toString(source.getByteLength()), Vocab.XSD_INTEGER));
        emit(out, source, Location.none(), doc, Vocab.TOGETL_FORMAT,
                Literal.of(source.getFormat()));
        emit(out, source, Location.none(), doc, Vocab.TOGETL_PARSER_VERSION,
                Literal.of(source.getParserVersion()));
        emit(out, source, Location.none(), doc, Vocab.TOGETL_HAS_BOM,
                Literal.of(document.hasBom() ? "true" : "false", Vocab.XSD_BOOLEAN));

        for (int i = 0; i < document.getComments().size(); i++) {
            TEFComment comment = document.getComments().get(i);
            BlankNode node = BlankNode.of("tef-doc-comment-" + scope + "-" + i);
            Location location = locationOf(comment.getSpan());
            emit(out, source, location, doc, Vocab.TEF_COMMENT_LINK, node);
            emit(out, source, location, node, Vocab.RDF_TYPE, Vocab.TEF_COMMENT);
            emit(out, source, location, node, Vocab.TEF_TEXT, Literal.of(comment.getText()));
        }

        for (int i = 0; i < document.getDirectives().size(); i++) {
            TEFDirective directive = document.getDirectives().get(i);
            BlankNode node = BlankNode.of("tef-doc-directive-" + scope + "-" + i);
            Location location = locationOf(directive.getSpan());
            emit(out, source, location, doc, Vocab.TEF_DIRECTIVE_LINK, node);
            emit(out, source, location, node, Vocab.RDF_TYPE, Vocab.TEF_DIRECTIVE);
            emit(out, source, location, node, Vocab.TEF_NAME, Literal.of(directive.getName()));
            emit(out, source, location, node, Vocab.TEF_VALUE, Literal.of(directive.getValue()));
            emit(out, source, location, node, Vocab.TEF_RAW, Literal.of(directive.getRaw()));
            if (!"format".equals(directive.getName()) && !"alias".equals(directive.getName())) {
                report.unmappedDirective(directive.getName(), location);
            }
        }

        for (int entryIndex = 0; entryIndex < document.getEntries().size(); entryIndex++) {
            TEFEntry entry = document.getEntries().get(entryIndex);
            IRI entryIri = IRI.of(Identifiers.tefEntryIri(documentIri, entryIndex));
            Location entryLocation = locationOf(entry.getSpan());

            emit(out, source, entryLocation, entryIri, Vocab.RDF_TYPE, Vocab.TEF_ENTRY);
            emit(out, source, entryLocation, entryIri, Vocab.TOGETL_DOCUMENT_LINK, doc);
            emit(out, source, entryLocation, entryIri, Vocab.TEF_ENTRY_INDEX,
                    Literal.of(Integer.toString(entryIndex), Vocab.XSD_INTEGER));
            if (!entry.getType().isEmpty()) {
                emit(out, source, entryLocation, entryIri, Vocab.TEF_ENTRY_TYPE,
                        Literal.of(entry.getType()));
                report.unmappedEntryType(entry.getType(), entryLocation);
            }
            if (!entry.getValue().isEmpty()) {
                emit(out, source, entryLocation, entryIri, Vocab.TEF_ENTRY_VALUE,
                        Literal.of(entry.getValue()));
            }
            if (!entry.getContent().isEmpty()) {
                emit(out, source, entryLocation, entryIri, Vocab.TEF_CONTENT,
                        Literal.of(entry.getContent()));
            }

            for (int headerIndex = 0; headerIndex < entry.getHeaders().size(); headerIndex++) {
                TEFHeader header = entry.getHeaders().get(headerIndex);
                BlankNode node = BlankNode.of(
                        "tef-header-" + scope + "-" + entryIndex + "-" + headerIndex);
                Location location = locationOf(header.getSpan());
                emit(out, source, location, entryIri, Vocab.TEF_HEADER_LINK, node);
                emit(out, source, location, node, Vocab.RDF_TYPE, Vocab.TEF_HEADER);
                emit(out, source, location, node, Vocab.TEF_KEY, Literal.of(header.getKey()));
                emit(out, source, location, node, Vocab.TEF_VALUE, Literal.of(header.getValue()));
                emit(out, source, location, node, Vocab.TEF_INDEX,
                        Literal.of(Integer.toString(headerIndex), Vocab.XSD_INTEGER));
                report.unmappedField(entry.getType(), header.getKey(), location);
            }

            for (int commentIndex = 0; commentIndex < entry.getComments().size(); commentIndex++) {
                TEFComment comment = entry.getComments().get(commentIndex);
                BlankNode node = BlankNode.of(
                        "tef-comment-" + scope + "-" + entryIndex + "-" + commentIndex);
                Location location = locationOf(comment.getSpan());
                emit(out, source, location, entryIri, Vocab.TEF_COMMENT_LINK, node);
                emit(out, source, location, node, Vocab.RDF_TYPE, Vocab.TEF_COMMENT);
                emit(out, source, location, node, Vocab.TEF_TEXT, Literal.of(comment.getText()));
            }

            for (int directiveIndex = 0; directiveIndex < entry.getDirectives().size(); directiveIndex++) {
                TEFDirective directive = entry.getDirectives().get(directiveIndex);
                BlankNode node = BlankNode.of(
                        "tef-directive-" + scope + "-" + entryIndex + "-" + directiveIndex);
                Location location = locationOf(directive.getSpan());
                emit(out, source, location, entryIri, Vocab.TEF_DIRECTIVE_LINK, node);
                emit(out, source, location, node, Vocab.RDF_TYPE, Vocab.TEF_DIRECTIVE);
                emit(out, source, location, node, Vocab.TEF_NAME, Literal.of(directive.getName()));
                emit(out, source, location, node, Vocab.TEF_VALUE, Literal.of(directive.getValue()));
                emit(out, source, location, node, Vocab.TEF_RAW, Literal.of(directive.getRaw()));
            }
        }

        for (int i = 0; i < document.getIssues().size(); i++) {
            TEFIssue issue = document.getIssues().get(i);
            BlankNode node = BlankNode.of("tef-issue-" + scope + "-" + i);
            Location location = locationOf(issue.getSpan());
            emit(out, source, location, doc, Vocab.TOGETL_PARSE_ISSUE_LINK, node);
            emit(out, source, location, node, Vocab.RDF_TYPE, Vocab.TOGETL_PARSE_ISSUE);
            emit(out, source, location, node, Vocab.TOGETL_SEVERITY,
                    Literal.of(issue.getSeverity().name().toLowerCase(java.util.Locale.ROOT)));
            emit(out, source, location, node, Vocab.TOGETL_CODE, Literal.of(issue.getCode()));
            emit(out, source, location, node, Vocab.TOGETL_MESSAGE, Literal.of(issue.getMessage()));
            if (issue.getRaw() != null) {
                emit(out, source, location, node, Vocab.TOGETL_RAW, Literal.of(issue.getRaw()));
            }
        }
    }

    private static void emit(
            StatementConsumer out,
            SourceDocument source,
            Location location,
            Node subject,
            IRI predicate,
            Node object
    ) throws IOException {
        out.accept(new Statement(source.getIri(), location, subject, predicate, object));
    }

    private static Location locationOf(TEFSpan span) {
        return span == null ? Location.none() : span.toLocation();
    }
}
