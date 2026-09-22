package net.nuke24.scratch38.s0029;

import java.util.List;

/**
 * equals/hashCode are content-based and consistent across implementations
 * (see {@link ByteBlobs#contentEquals} / {@link ByteBlobs#contentHashCode});
 * toString is a debugging aid and may differ between equal instances,
 * since it reveals internal representation rather than just content.
 */
public interface ByteBlob {
	public int length();
	public List<ByteChunk> getChunks();
}
