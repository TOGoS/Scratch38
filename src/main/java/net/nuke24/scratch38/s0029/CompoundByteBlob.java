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
	
	/** Content-based, consistent with any other {@link ByteBlob} implementation. */
	@Override
	public boolean equals(Object obj) {
		if( this == obj ) return true;
		if( !(obj instanceof ByteBlob) ) return false;
		return ByteBlobs.contentEquals(this, (ByteBlob)obj);
	}
	
	@Override
	public int hashCode() {
		return ByteBlobs.contentHashCode(this);
	}
	
	/** Unlike equals/hashCode, reveals internal structure rather than just content -- useful for debugging. */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("CompoundByteBlob[");
		for( int i=0; i<components.size(); ++i ) {
			if( i > 0 ) sb.append(", ");
			sb.append(components.get(i));
		}
		sb.append("]");
		return sb.toString();
	}
}
