package net.nuke24.togetl.dialect;

import java.io.IOException;

import net.nuke24.togetl.etl.ImportReport;
import net.nuke24.togetl.etl.SourceDocument;
import net.nuke24.togetl.rdf.StatementConsumer;
import net.nuke24.togetl.tef.TEFDocument;
import net.nuke24.togetl.tef.TEFEntry;

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
		DialectSupport.emitDocumentMetadata(document, source, out);
		DialectSupport.emitDocumentAnnotations(document, source, out, report);
		
		for (int entryIndex = 0; entryIndex < document.getEntries().size(); entryIndex++) {
			TEFEntry entry = document.getEntries().get(entryIndex);
			DialectSupport.emitGenericEntry(document, source, entry, entryIndex, out, report);
		}
		
		DialectSupport.emitIssues(document, source, out);
	}
}
