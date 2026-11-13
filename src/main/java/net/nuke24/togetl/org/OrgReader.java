package net.nuke24.togetl.org;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Entry points for reading Org documents.
 *
 * <p>Input is pulled from a source in chunks and pushed through a line
 * splitter into an {@link OrgParser}. Callers that already have text can drive
 * those stages directly.</p>
 */
public final class OrgReader {
	private static final int CHUNK_SIZE = 8192;
	
	private OrgReader() {
	}
	
	public static OrgDocument read(byte[] bytes) {
		try {
			return read(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8));
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
			splitter.feed(new String(chunk, 0, count));
		}
		splitter.finish();
		return parser.finish();
	}
}
