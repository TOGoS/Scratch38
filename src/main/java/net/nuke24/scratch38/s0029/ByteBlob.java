package net.nuke24.scratch38.s0029;

import java.util.List;

public interface ByteBlob {
	public int length();
	public List<ByteChunk> getChunks();
}
