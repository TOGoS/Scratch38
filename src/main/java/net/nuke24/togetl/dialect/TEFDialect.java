package net.nuke24.togetl.dialect;

import java.io.IOException;

import net.nuke24.togetl.etl.ImportReport;
import net.nuke24.togetl.etl.SourceDocument;
import net.nuke24.togetl.rdf.StatementConsumer;
import net.nuke24.togetl.tef.TEFDocument;

/**
 * Maps parsed TEF entries to statements.
 */
public interface TEFDialect {
	String getName();
	
	void map(
			TEFDocument document,
			SourceDocument source,
			StatementConsumer out,
			ImportReport report
	) throws IOException;
}
