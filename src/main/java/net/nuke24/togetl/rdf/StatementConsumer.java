package net.nuke24.togetl.rdf;

import java.io.IOException;

/**
 * Receives statements in source order.
 */
public interface StatementConsumer {
    void accept(Statement statement) throws IOException;
}
