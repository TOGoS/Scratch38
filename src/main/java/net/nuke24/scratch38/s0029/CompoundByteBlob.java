package net.nuke24.scratch38.s0029;

import java.util.ArrayList;
import java.util.List;

/**
 * Note that this class does no optimizations for
 * cases where the component list could have been simplified.
 * Use ByteBlobs.concat(...) instead of using new CompoundByteBlob(...) directly.
 */
public class CompoundByteBlob implements ByteBlob {
	protected final List<ByteBlob> components;
	
	public CompoundByteBlob(List<ByteBlob> components) {
		this.components = components;
	}
	
	public @Override int length() {
		int length = 0;
		for( ByteBlob blob : components ) {
			length += blob.length();
		}
		return length;
	}
	
	public @Override List<ByteChunk> getChunks() {
		List<ByteChunk> chunks = new ArrayList<>();
		for( ByteBlob component : this.components ) chunks.addAll(component.getChunks());
		return chunks;
	}
}
