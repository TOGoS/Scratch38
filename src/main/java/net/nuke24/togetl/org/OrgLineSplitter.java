package net.nuke24.togetl.org;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Stage that splits text into lines.
 *
 * <p>Input is {@code Optional.of(chunk)} for each chunk, then
 * {@code Optional.empty()} at end of input. Output is one
 * {@code Optional.of(line)} per line, then {@code Optional.empty()}.</p>
 *
 * <p>Only LF ends a line, as in Org. A CR immediately before an LF is dropped;
 * any other CR is ordinary text. Chunk boundaries do not matter.</p>
 */
final class OrgLineSplitter implements Consumer<Optional<String>> {
	private final Consumer<Optional<String>> downstream;
	private final StringBuilder pending = new StringBuilder();
	
	OrgLineSplitter(Consumer<Optional<String>> downstream) {
		this.downstream = downstream;
	}
	
	@Override
	public void accept(Optional<String> item) {
		if (item.isPresent()) {
			feed(item.get());
		} else {
			if (pending.length() > 0) {
				emit(false);
			}
			downstream.accept(Optional.empty());
		}
	}
	
	private void feed(String chunk) {
		for (int i = 0; i < chunk.length(); i++) {
			char c = chunk.charAt(i);
			if (c == '\n') {
				emit(true);
			} else {
				pending.append(c);
			}
		}
	}
	
	private void emit(boolean terminated) {
		int length = pending.length();
		if (terminated && length > 0 && pending.charAt(length - 1) == '\r') {
			pending.setLength(length - 1);
		}
		String line = pending.toString();
		pending.setLength(0);
		downstream.accept(Optional.of(line));
	}
}
