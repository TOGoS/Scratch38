package net.nuke24.togetl.mapping;

import java.io.IOException;

/**
 * Turns one header value into one or more statements.
 */
public interface ValueParser {
	void parse(ValueParserContext context) throws IOException;
}
