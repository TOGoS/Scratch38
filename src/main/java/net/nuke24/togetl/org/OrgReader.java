package net.nuke24.togetl.org;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Entry points for reading Org documents.
 *
 * <p>These methods pull text from a source in chunks and push it through
 * {@link OrgLineSplitter} into an {@link OrgParser}. Code that already has
 * lines can push them into an {@code OrgParser} directly, as described there.</p>
 */
public final class OrgReader {
	private static final int CHUNK_SIZE = 8192;
	
	private OrgReader() {
	}
	
	public static OrgDocument read(byte[] bytes) {
		try (Reader reader = new InputStreamReader(
				new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
			return read(reader);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	
	public static OrgDocument read(Reader reader) throws IOException {
		OrgParser parser = new OrgParser();
		OrgLineSplitter splitter = new OrgLineSplitter(parser);
		char[] chunk = new char[CHUNK_SIZE];
		int count;
		while ((count = reader.read(chunk)) >= 0) {
			splitter.accept(Optional.of(new String(chunk, 0, count)));
		}
		splitter.accept(Optional.empty());
		return parser.document();
	}
}
