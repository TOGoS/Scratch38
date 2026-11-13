package net.nuke24.togetl.org;

import java.io.IOException;
import java.io.Reader;

/**
 * Splits input into lines the way Org does: only LF ends a line. A CR that
 * immediately precedes the LF is dropped; any other CR is ordinary text.
 */
final class OrgLineReader {
	private final Reader in;
	private final char[] buffer = new char[8192];
	private int bufferPos;
	private int bufferLimit;
	
	OrgLineReader(Reader in) {
		this.in = in;
	}
	
	/** Next line without its terminator, or null at end of input. */
	String readLine() throws IOException {
		StringBuilder line = new StringBuilder();
		boolean sawChar = false;
		while (true) {
			if (bufferPos >= bufferLimit) {
				bufferLimit = in.read(buffer, 0, buffer.length);
				bufferPos = 0;
				if (bufferLimit < 0) {
					bufferLimit = 0;
					return sawChar ? stripCr(line) : null;
				}
			}
			char c = buffer[bufferPos++];
			sawChar = true;
			if (c == '\n') {
				return stripCr(line);
			}
			line.append(c);
		}
	}
	
	private static String stripCr(StringBuilder line) {
		int length = line.length();
		if (length > 0 && line.charAt(length - 1) == '\r') {
			line.setLength(length - 1);
		}
		return line.toString();
	}
}
