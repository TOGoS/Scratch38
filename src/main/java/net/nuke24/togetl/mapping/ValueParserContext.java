package net.nuke24.togetl.mapping;

import java.io.IOException;

import net.nuke24.togetl.dialect.DialectSupport;
import net.nuke24.togetl.etl.ImportReport;
import net.nuke24.togetl.etl.SourceDocument;
import net.nuke24.togetl.ident.Identifiers;
import net.nuke24.togetl.rdf.BlankNode;
import net.nuke24.togetl.rdf.IRI;
import net.nuke24.togetl.rdf.Location;
import net.nuke24.togetl.rdf.Node;
import net.nuke24.togetl.rdf.StatementConsumer;
import net.nuke24.togetl.tef.TEFHeader;

/**
 * Everything a value parser needs to emit statements for one header.
 */
public final class ValueParserContext {
    private final SourceDocument source;
    private final String documentHash;
    private final String entryType;
    private final int entryOrdinal;
    private final IRI subject;
    private final FieldRule rule;
    private final TEFHeader header;
    private final int fieldOrdinal;
    private final StatementConsumer out;
    private final ImportReport report;
    private final ValueEmitter emitter;

    public ValueParserContext(
            SourceDocument source,
            String entryType,
            int entryOrdinal,
            IRI subject,
            FieldRule rule,
            TEFHeader header,
            int fieldOrdinal,
            StatementConsumer out,
            ImportReport report,
            ValueEmitter emitter
    ) {
        this.source = source;
        this.documentHash = Identifiers.documentHash(source.getIri());
        this.entryType = entryType;
        this.entryOrdinal = entryOrdinal;
        this.subject = subject;
        this.rule = rule;
        this.header = header;
        this.fieldOrdinal = fieldOrdinal;
        this.out = out;
        this.report = report;
        this.emitter = emitter;
    }

    public SourceDocument getSource() {
        return source;
    }

    public String getDocumentHash() {
        return documentHash;
    }

    public String getEntryType() {
        return entryType;
    }

    public int getEntryOrdinal() {
        return entryOrdinal;
    }

    public IRI getSubject() {
        return subject;
    }

    public FieldRule getRule() {
        return rule;
    }

    public TEFHeader getHeader() {
        return header;
    }

    public int getFieldOrdinal() {
        return fieldOrdinal;
    }

    public Location getLocation() {
        return DialectSupport.locationOf(header.getSpan());
    }

    /** Emits through the dialect's cardinality and conflict handling. */
    public void emit(Node object) throws IOException {
        emitter.emit(this, object);
    }

    /** Emits a supporting statement without cardinality handling. */
    public void emitRaw(IRI subjectIri, IRI predicate, Node object) throws IOException {
        DialectSupport.emit(source, out, getLocation(), subjectIri, predicate, object);
    }

    /** Emits a supporting statement about any node (an IRI or blank node). */
    public void emitRaw(Node subjectNode, IRI predicate, Node object) throws IOException {
        DialectSupport.emit(source, out, getLocation(), subjectNode, predicate, object);
    }

    public BlankNode blankNode(String kind, int index) {
        return BlankNode.of(
                kind + "-" + documentHash + "-" + entryOrdinal + "-" + fieldOrdinal + "-" + index);
    }

    public void report(String kind) {
        report.record(kind, entryType, rule.getHeader(), getLocation());
    }
}
