package net.nuke24.togetl.mapping;

import java.io.IOException;

import net.nuke24.togetl.rdf.Node;

/**
 * Applies cardinality and conflict rules to a mapped value.
 */
public interface ValueEmitter {
	void emit(ValueParserContext context, Node object) throws IOException;
}
