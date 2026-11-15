package net.nuke24.togetl.tef;

import java.io.IOException;

/**
 * Receives TEF structure as {@link TEFReader} finishes it. Document comments
 * and directives arrive when they are read, and each entry arrives once the
 * next entry starts or the input ends.
 */
public interface TEFSink {
	/** A comment that appears before the first entry. */
	void documentComment(TEFComment comment) throws IOException;

	/** Every directive in the document, including those inside entries. */
	void documentDirective(TEFDirective directive) throws IOException;

	void entry(TEFEntry entry) throws IOException;
}
