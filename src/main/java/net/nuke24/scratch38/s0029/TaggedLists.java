package net.nuke24.scratch38.s0029;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class TaggedLists {
	/**
	 * Merges adjacent tokens sharing the same tag, but only for tags in {@code mergeableTags};
	 * tokens with any other tag are left as-is, even when adjacent to a same-tagged token.
	 */
	public static <T> List<Tagged<T,ByteBlob>> simplifyAdjacent(List<Tagged<T,ByteBlob>> tokens, Set<T> mergeableTags) {
		if( tokens.size() == 0 ) return tokens;
		
		List<Tagged<T,ByteBlob>> result = new ArrayList<Tagged<T,ByteBlob>>();
		Tagged<T,ByteBlob> acc = tokens.get(0);
		for( int i=1; i<tokens.size(); ++i ) {
			Tagged<T,ByteBlob> next = tokens.get(i);
			if( mergeableTags.contains(acc.tag) && Objects.equals(acc.tag, next.tag) ) {
				acc = new Tagged<T,ByteBlob>(acc.tag, ByteBlobs.concat(Arrays.<ByteBlob>asList(acc.content, next.content)));
				continue;
			}
			result.add(acc);
			acc = next;
		}
		result.add(acc);
		
		// If no change, re-use the original list.
		if( result.size() == tokens.size() ) return tokens;
		
		return result;
	}
}
