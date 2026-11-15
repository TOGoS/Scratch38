package net.nuke24.togetl.util;

import java.io.IOException;
import java.util.Optional;

/**
 * A pipeline stage. It receives {@code Optional.of(item)} for each item, then
 * {@code Optional.empty()} exactly once when the input ends.
 */
public interface Sink<T> {
	void accept(Optional<T> item) throws IOException;
}
