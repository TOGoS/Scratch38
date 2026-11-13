package net.nuke24.togetl.dialect;

import java.io.IOException;

import net.nuke24.togetl.etl.SourceProvenance;
import net.nuke24.togetl.tef.TEFSink;

/**
 * Receives one TEF document and writes its statements. Call
 * {@link #finish(SourceProvenance)} after the document has been read; the
 * byte-level metadata is only known at that point.
 */
public interface TEFDocumentSink extends TEFSink {
	void finish(SourceProvenance provenance) throws IOException;
}
