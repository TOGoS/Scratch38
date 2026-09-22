package net.nuke24.scratch38.s0029;

import java.util.ArrayList;
import java.util.List;

public class ByteBlobs
{
	/** Content-based equality, consistent across any combination of {@link ByteBlob} implementations. */
	public static boolean contentEquals(ByteBlob a, ByteBlob b) {
		if( a == b ) return true;
		if( a.length() != b.length() ) return false;
		
		List<ByteChunk> aChunks = a.getChunks();
		List<ByteChunk> bChunks = b.getChunks();
		int ai = 0, ap = 0;
		int bi = 0, bp = 0;
		while( ai < aChunks.size() && bi < bChunks.size() ) {
			ByteChunk ac = aChunks.get(ai);
			ByteChunk bc = bChunks.get(bi);
			if( ap == ac.length ) { ++ai; ap = 0; continue; }
			if( bp == bc.length ) { ++bi; bp = 0; continue; }
			if( ac.buffer[ac.offset+ap] != bc.buffer[bc.offset+bp] ) return false;
			++ap; ++bp;
		}
		return true;
	}
	
	/** Content-based hash, consistent across any combination of {@link ByteBlob} implementations. */
	public static int contentHashCode(ByteBlob blob) {
		int hash = 1;
		for( ByteChunk c : blob.getChunks() ) {
			for( int i=0; i<c.length; ++i ) hash = 31 * hash + c.buffer[c.offset+i];
		}
		return hash;
	}
	
	public static ByteBlob simplifyAdjacent(ByteBlob left, ByteBlob right) {
		if( left.length() == 0 ) return right;
		if( right.length() == 0 ) return left;
		
		if( left instanceof ByteChunk && right instanceof ByteChunk ) {
			ByteChunk leftChunk = (ByteChunk)left;
			ByteChunk rightChunk = (ByteChunk)right;
			if( leftChunk.buffer == rightChunk.buffer && leftChunk.offset + leftChunk.length == rightChunk.offset ) {
				return new ByteChunk(leftChunk.buffer, leftChunk.offset, leftChunk.length + rightChunk.length);
			}
		}
		
		return null;
	}
	
	public static ByteBlob concat(List<ByteBlob> blobs) {
		// Don't bother stream/filtering an empty list!
		if( blobs.size() == 0 ) return ByteChunk.EMPTY;
		
		// Simplify the list, if possible, by removing empty
		// elements and joining adjacent chunks
		List<ByteBlob> mergedBlobs = new ArrayList<>();
		ByteBlob acc = blobs.get(0);
		for( int i=1; i<blobs.size(); ++i ) {
			ByteBlob next = blobs.get(i);
			if( acc.length() == 0 ) {
				acc = next;
				continue;
			}
			ByteBlob simplified = simplifyAdjacent(acc, next);
			if( simplified != null ) {
				acc = simplified;
				continue;
			}
			
			if( acc.length() > 0 ) mergedBlobs.add(acc);
			acc = next;
		}
		if( acc.length() > 0 ) mergedBlobs.add(acc);
		
		// If no change, re-use the original list.
		if( mergedBlobs.size() == blobs.size() ) mergedBlobs = blobs;
		
		if( mergedBlobs.size() == 0 ) return ByteChunk.EMPTY;
		if( mergedBlobs.size() == 1 ) return mergedBlobs.get(0);
		
		return new CompoundByteBlob(mergedBlobs);
	}
}
