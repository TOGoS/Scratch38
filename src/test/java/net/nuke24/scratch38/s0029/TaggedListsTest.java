package net.nuke24.scratch38.s0029;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

public class TaggedListsTest extends TestCase {
	private static ByteBlob chunk(String s) {
		return ByteChunk.of(Main.ascii(s));
	}
	
	private static Tagged<HELOChunkType,ByteBlob> tagged(HELOChunkType tag, String s) {
		return new Tagged<HELOChunkType,ByteBlob>(tag, chunk(s));
	}
	
	private static String contentString(Tagged<HELOChunkType,ByteBlob> token) {
		StringBuilder sb = new StringBuilder();
		for( ByteChunk c : token.content.getChunks() ) {
			for( int i=0; i<c.length; ++i ) sb.append((char)(c.buffer[c.offset+i] & 0xFF));
		}
		return sb.toString();
	}
	
	public void testSimplifyAdjacent_emptyList() {
		List<Tagged<HELOChunkType,ByteBlob>> tokens = Collections.emptyList();
		assertSame(tokens, TaggedLists.simplifyAdjacent(tokens, EnumSet.of(HELOChunkType.DELIMITER)));
	}
	
	public void testSimplifyAdjacent_singleToken() {
		List<Tagged<HELOChunkType,ByteBlob>> tokens = Collections.singletonList(tagged(HELOChunkType.DELIMITER, "a"));
		assertSame(tokens, TaggedLists.simplifyAdjacent(tokens, EnumSet.of(HELOChunkType.DELIMITER)));
	}
	
	public void testSimplifyAdjacent_mergesWithinMergeableSet() {
		List<Tagged<HELOChunkType,ByteBlob>> tokens = Arrays.asList(
			tagged(HELOChunkType.DELIMITER, "a"),
			tagged(HELOChunkType.DELIMITER, "b")
		);
		List<Tagged<HELOChunkType,ByteBlob>> result = TaggedLists.simplifyAdjacent(tokens, EnumSet.of(HELOChunkType.DELIMITER));
		assertEquals(1, result.size());
		assertEquals(HELOChunkType.DELIMITER, result.get(0).tag);
		assertEquals("ab", contentString(result.get(0)));
	}
	
	public void testSimplifyAdjacent_mergesChainOfThree() {
		List<Tagged<HELOChunkType,ByteBlob>> tokens = Arrays.asList(
			tagged(HELOChunkType.DELIMITER, "a"),
			tagged(HELOChunkType.DELIMITER, "b"),
			tagged(HELOChunkType.DELIMITER, "c")
		);
		List<Tagged<HELOChunkType,ByteBlob>> result = TaggedLists.simplifyAdjacent(tokens, EnumSet.of(HELOChunkType.DELIMITER));
		assertEquals(1, result.size());
		assertEquals("abc", contentString(result.get(0)));
	}
	
	public void testSimplifyAdjacent_doesNotMergeOutsideMergeableSet() {
		List<Tagged<HELOChunkType,ByteBlob>> tokens = Arrays.asList(
			tagged(HELOChunkType.HEADER_NAME, "a"),
			tagged(HELOChunkType.HEADER_NAME, "b")
		);
		Set<HELOChunkType> mergeableTags = EnumSet.of(HELOChunkType.DELIMITER);
		List<Tagged<HELOChunkType,ByteBlob>> result = TaggedLists.simplifyAdjacent(tokens, mergeableTags);
		assertEquals(2, result.size());
		assertSame(tokens, result);
	}
	
	public void testSimplifyAdjacent_doesNotMergeDifferentTags() {
		List<Tagged<HELOChunkType,ByteBlob>> tokens = Arrays.asList(
			tagged(HELOChunkType.DELIMITER, "a"),
			tagged(HELOChunkType.HEADER_NAME, "b")
		);
		Set<HELOChunkType> mergeableTags = EnumSet.of(HELOChunkType.DELIMITER, HELOChunkType.HEADER_NAME);
		List<Tagged<HELOChunkType,ByteBlob>> result = TaggedLists.simplifyAdjacent(tokens, mergeableTags);
		assertEquals(2, result.size());
		assertSame(tokens, result);
	}
	
	public void testSimplifyAdjacent_mergesOnlyMatchingRuns() {
		List<Tagged<HELOChunkType,ByteBlob>> tokens = Arrays.asList(
			tagged(HELOChunkType.DELIMITER, "a"),
			tagged(HELOChunkType.DELIMITER, "b"),
			tagged(HELOChunkType.HEADER_NAME, "auth"),
			tagged(HELOChunkType.DELIMITER, "c")
		);
		List<Tagged<HELOChunkType,ByteBlob>> result = TaggedLists.simplifyAdjacent(tokens, EnumSet.of(HELOChunkType.DELIMITER));
		assertEquals(3, result.size());
		assertEquals(HELOChunkType.DELIMITER, result.get(0).tag);
		assertEquals("ab", contentString(result.get(0)));
		assertEquals(HELOChunkType.HEADER_NAME, result.get(1).tag);
		assertEquals("auth", contentString(result.get(1)));
		assertEquals(HELOChunkType.DELIMITER, result.get(2).tag);
		assertEquals("c", contentString(result.get(2)));
	}
}
