package net.nuke24.togetl.dialect;

import java.io.IOException;

import net.nuke24.togetl.etl.ImportReport;
import net.nuke24.togetl.etl.SourceDocument;
import net.nuke24.togetl.rdf.StatementConsumer;
import net.nuke24.togetl.tef.TEFEntry;
import net.nuke24.togetl.tef.TEFSink;

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
	public TEFSink begin(
			SourceDocument source,
			boolean bom,
			StatementConsumer out,
			ImportReport report
	) throws IOException {
		return new Session(source, bom, out, report);
	}
	
	private static final class Session extends DialectSupport.DocumentSession {
		Session(
				SourceDocument source,
				boolean bom,
				StatementConsumer out,
				ImportReport report
		) throws IOException {
			super(source, bom, out, report);
		}
		
		@Override
		protected void mapEntry(TEFEntry entry, int entryIndex) throws IOException {
			DialectSupport.emitGenericEntry(source, out, entry, entryIndex, report);
		}
	}
}
