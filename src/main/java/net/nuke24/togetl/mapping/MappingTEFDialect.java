package net.nuke24.togetl.mapping;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.nuke24.togetl.dialect.DialectSupport;
import net.nuke24.togetl.dialect.TEFDialect;
import net.nuke24.togetl.etl.ImportReport;
import net.nuke24.togetl.etl.SourceDocument;
import net.nuke24.togetl.ident.Identifiers;
import net.nuke24.togetl.rdf.IRI;
import net.nuke24.togetl.rdf.Literal;
import net.nuke24.togetl.rdf.Location;
import net.nuke24.togetl.rdf.Node;
import net.nuke24.togetl.rdf.StatementConsumer;
import net.nuke24.togetl.tef.TEFDocument;
import net.nuke24.togetl.tef.TEFEntry;
import net.nuke24.togetl.tef.TEFHeader;
import net.nuke24.togetl.vocab.Vocab;

/**
 * Maps TEF entries through a declarative mapping table.
 *
 * <p>Entries without an entry rule and headers without a field rule fall back
 * to the generic structural capture, so a mapping table can grow incrementally
 * without dropping source data.</p>
 */
public final class MappingTEFDialect implements TEFDialect {
	private final String name;
	private final MappingTable table;
	
	public MappingTEFDialect(String name, MappingTable table) {
		if (name == null || name.isEmpty()) {
			throw new IllegalArgumentException("Dialect name must not be empty");
		}
		if (table == null) {
			throw new IllegalArgumentException("Mapping table must not be null");
		}
		this.name = name;
		this.table = table;
	}
	
	public MappingTable getTable() {
		return table;
	}
	
	@Override
	public String getName() {
		return name;
	}
	
	@Override
	public void map(
			TEFDocument document,
			SourceDocument source,
			StatementConsumer out,
			ImportReport report
	) throws IOException {
		DialectSupport.emitDocumentMetadata(document, source, out);
		DialectSupport.emitDocumentAnnotations(document, source, out, report);
		
		String documentIri = source.getIri();
		String documentHash = Identifiers.documentHash(documentIri);
		IRI doc = IRI.of(documentIri);
		ConflictTracker conflicts = new ConflictTracker();
		CardinalityEmitter emitter = new CardinalityEmitter(out, report, conflicts);
		
		for (int entryIndex = 0; entryIndex < document.getEntries().size(); entryIndex++) {
			TEFEntry entry = document.getEntries().get(entryIndex);
			EntryRule entryRule = table.entryRule(entry.getType());
			String subjectText = entryRule == null
					? null
					: entryRule.buildSubject(entry, documentIri, documentHash, entryIndex);
					
			if (subjectText == null) {
				DialectSupport.emitGenericEntry(
						document, source, entry, entryIndex, out, report);
				continue;
			}
			
			IRI subject = IRI.of(subjectText);
			Location entryLocation = DialectSupport.locationOf(entry.getSpan());
			
			if (entryRule.getTypeIri() != null) {
				DialectSupport.emit(source, out, entryLocation, subject,
						Vocab.RDF_TYPE, IRI.of(entryRule.getTypeIri()));
			}
			DialectSupport.emit(source, out, entryLocation, subject,
					Vocab.TOGETL_DOCUMENT_LINK, doc);
			DialectSupport.emit(source, out, entryLocation, subject,
					Vocab.TEF_ENTRY_INDEX,
					Literal.of(Integer.toString(entryIndex), Vocab.XSD_INTEGER));
			if (!entry.getType().isEmpty()) {
				DialectSupport.emit(source, out, entryLocation, subject,
						Vocab.TEF_ENTRY_TYPE, Literal.of(entry.getType()));
			}
			if (entryRule.getAsWritten() != null) {
				report.record("as-written-entry", entry.getType(), "", entryLocation);
				DialectSupport.emit(source, out, entryLocation, subject,
						Vocab.TOGETL_AS_WRITTEN, Literal.of(entryRule.getAsWritten()));
			}
			
			if (!entry.getContent().isEmpty()) {
				conflicts.emit(source, out, report, subject, Vocab.RDFS_COMMENT,
						Literal.of(entry.getContent()), "1",
						entry.getType(), "content", entryLocation);
			}
			
			for (int headerIndex = 0; headerIndex < entry.getHeaders().size(); headerIndex++) {
				TEFHeader header = entry.getHeaders().get(headerIndex);
				FieldRule fieldRule = table.fieldRule(entry.getType(), header.getKey());
				Location location = DialectSupport.locationOf(header.getSpan());
				if (fieldRule == null) {
					DialectSupport.emitGenericHeader(
							source, out, documentHash, subject, entryIndex, headerIndex, header);
					report.unmappedField(entry.getType(), header.getKey(), location);
					continue;
				}
				if (fieldRule.getAsWritten() != null) {
					report.record("as-written-field", entry.getType(), header.getKey(), location);
					DialectSupport.emit(source, out, location, subject,
							Vocab.TOGETL_AS_WRITTEN, Literal.of(fieldRule.getAsWritten()));
				}
				ValueParser parser = ValueParsers.byName(fieldRule.getValueParser());
				ValueParserContext context = new ValueParserContext(
						source,
						entry.getType(),
						entryIndex,
						subject,
						fieldRule,
						header,
						headerIndex,
						out,
						report,
						emitter);
				parser.parse(context);
			}
			
			for (int commentIndex = 0; commentIndex < entry.getComments().size(); commentIndex++) {
				DialectSupport.emitGenericComment(
						source, out, documentHash, subject, entryIndex, commentIndex,
						entry.getComments().get(commentIndex));
			}
			for (int directiveIndex = 0; directiveIndex < entry.getDirectives().size(); directiveIndex++) {
				DialectSupport.emitGenericDirective(
						source, out, documentHash, subject, entryIndex, directiveIndex,
						entry.getDirectives().get(directiveIndex));
			}
		}
		
		DialectSupport.emitIssues(document, source, out);
	}
	
	/**
	 * Tracks which values have already been emitted for a subject/predicate
	 * pair so that functional fields can report conflicts without losing data.
	 */
	private static final class ConflictTracker {
		private final Map<String, Set<String>> seen = new HashMap<String, Set<String>>();
		
		void emit(
				SourceDocument source,
				StatementConsumer out,
				ImportReport report,
				IRI subject,
				IRI predicate,
				Node object,
				String cardinality,
				String entryType,
				String field,
				Location location
		) throws IOException {
			if ("*".equals(cardinality)) {
				DialectSupport.emit(source, out, location, subject, predicate, object);
				return;
			}
			String key = subject.toString() + '\u0000' + predicate.toString();
			Set<String> objects = seen.get(key);
			if (objects == null) {
				objects = new LinkedHashSet<String>();
				seen.put(key, objects);
			}
			String canonical = object.toString();
			if (objects.contains(canonical)) {
				return;
			}
			if (!objects.isEmpty()) {
				report.conflict(entryType, field, location);
			}
			objects.add(canonical);
			DialectSupport.emit(source, out, location, subject, predicate, object);
		}
	}
	
	private static final class CardinalityEmitter implements ValueEmitter {
		private final StatementConsumer out;
		private final ImportReport report;
		private final ConflictTracker conflicts;
		
		CardinalityEmitter(StatementConsumer out, ImportReport report, ConflictTracker conflicts) {
			this.out = out;
			this.report = report;
			this.conflicts = conflicts;
		}
		
		@Override
		public void emit(ValueParserContext context, Node object) throws IOException {
			conflicts.emit(
					context.getSource(),
					out,
					report,
					context.getSubject(),
					IRI.of(context.getRule().getPredicate()),
					object,
					context.getRule().getCardinality(),
					context.getEntryType(),
					context.getRule().getHeader(),
					context.getLocation());
		}
	}
}
