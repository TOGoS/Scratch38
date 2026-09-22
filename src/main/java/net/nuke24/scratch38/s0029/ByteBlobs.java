package net.nuke24.scratch38.s0029;

import java.nio.channels.ByteChannel;
import java.util.ArrayList;
import java.util.List;

public class ByteBlobs
{
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
