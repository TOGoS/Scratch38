package net.nuke24.togetl.org;

import java.util.function.Consumer;

/**
 * Push stage that splits text into lines and pushes each line downstream.
 *
 * <p>Only LF ends a line, as in Org. A CR immediately before an LF is dropped;
 * any other CR is ordinary text. Chunk boundaries do not matter.</p>
 */
final class OrgLineSplitter {
	private final Consumer<String> downstream;
	private final StringBuilder pending = new StringBuilder();
	
	OrgLineSplitter(Consumer<String> downstream) {
		this.downstream = downstream;
	}
	
	void feed(String chunk) {
		for (int i = 0; i < chunk.length(); i++) {
			char c = chunk.charAt(i);
			if (c == '\n') {
				emit(true);
			} else {
				pending.append(c);
			}
		}
	}
	
	/** Pushes a final unterminated line, if any. */
	void finish() {
		if (pending.length() > 0) {
			emit(false);
		}
	}
	
	private void emit(boolean terminated) {
		int length = pending.length();
		if (terminated && length > 0 && pending.charAt(length - 1) == '\r') {
			pending.setLength(length - 1);
		}
		String line = pending.toString();
		pending.setLength(0);
		downstream.accept(line);
	}
}
