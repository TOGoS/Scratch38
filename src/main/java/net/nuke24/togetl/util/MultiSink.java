package net.nuke24.togetl.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Passes every item to each of its targets in turn. */
public final class MultiSink<T> implements Sink<T> {
	private final List<Sink<T>> targets;
	
	public MultiSink(List<? extends Sink<T>> targets) {
		this.targets = new ArrayList<Sink<T>>(targets);
	}
	
	@Override
	public void accept(Optional<T> item) throws IOException {
		for (Sink<T> target : targets) {
			target.accept(item);
		}
	}
}
