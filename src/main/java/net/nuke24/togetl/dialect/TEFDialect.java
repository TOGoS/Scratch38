package net.nuke24.togetl.dialect;

import java.io.IOException;

import net.nuke24.togetl.etl.ImportReport;
import net.nuke24.togetl.etl.SourceDocument;
import net.nuke24.togetl.rdf.StatementConsumer;
import net.nuke24.togetl.tef.TEFSink;

/**
 * Maps TEF structure to statements.
 */
public interface TEFDialect {
	String getName();

	/**
	 * Starts mapping one document. The returned sink receives the document as
	 * it is read and writes statements to {@code out} as entries arrive.
	 */
	TEFSink begin(
			SourceDocument source,
			boolean bom,
			StatementConsumer out,
			ImportReport report
	) throws IOException;
}
