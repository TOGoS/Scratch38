package net.nuke24.scratch38.s0029;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

public class ByteBlobsTest extends TestCase {
	private static ByteChunk ascii(int padding, String s) {
		byte[] pad = new byte[padding];
		byte[] content;
		try {
			content = s.getBytes("US-ASCII");
		} catch( java.io.UnsupportedEncodingException e ) {
			throw new RuntimeException(e);
		}
		byte[] buf = new byte[pad.length + content.length];
		System.arraycopy(content, 0, buf, pad.length, content.length);
		return new ByteChunk(buf, padding, content.length);
	}
	
	private static String contentString(ByteBlob blob) {
		StringBuilder sb = new StringBuilder();
		for( ByteChunk c : blob.getChunks() ) {
			for( int i=0; i<c.length; ++i ) sb.append((char)(c.buffer[c.offset+i] & 0xFF));
		}
		return sb.toString();
	}
	
	public void testSimplifyAdjacent_leftEmpty() {
		ByteChunk a = ascii(7, "hello");
		assertSame(a, ByteBlobs.simplifyAdjacent(ByteChunk.EMPTY, a));
	}
	
	public void testSimplifyAdjacent_rightEmpty() {
		ByteChunk a = ascii(7, "hello");
		assertSame(a, ByteBlobs.simplifyAdjacent(a, ByteChunk.EMPTY));
	}
	
	public void testSimplifyAdjacent_nonAdjacent() {
		ByteChunk a = ascii(7, "hello");
		ByteChunk b = ascii(7, "world");
		assertNull(ByteBlobs.simplifyAdjacent(a, b));
	}
	
	public void testSimplifyAdjacent_adjacentMerges() {
		ByteChunk buf = ascii(7, "helloworld");
		ByteChunk a = new ByteChunk(buf.buffer, 7, 5);
		ByteChunk b = new ByteChunk(buf.buffer, 12, 5);
		ByteBlob merged = ByteBlobs.simplifyAdjacent(a, b);
		assertTrue(merged instanceof ByteChunk);
		assertEquals("helloworld", contentString(merged));
	}
	
	public void testConcat_empty() {
		assertSame(ByteChunk.EMPTY, ByteBlobs.concat(Collections.<ByteBlob>emptyList()));
	}
	
	public void testConcat_singleChunk() {
		ByteChunk a = ascii(7, "hello");
		assertSame(a, ByteBlobs.concat(Collections.<ByteBlob>singletonList(a)));
	}
	
	public void testConcat_adjacentChunksMerge() {
		ByteChunk buf = ascii(7, "helloworld");
		ByteChunk a = new ByteChunk(buf.buffer, 7, 5);
		ByteChunk b = new ByteChunk(buf.buffer, 12, 5);
		ByteBlob result = ByteBlobs.concat(Arrays.<ByteBlob>asList(a, b));
		assertTrue(result instanceof ByteChunk);
		assertEquals("helloworld", contentString(result));
	}
	
	public void testConcat_nonAdjacentChunksDoNotMerge() {
		ByteChunk a = ascii(7, "hello");
		ByteChunk b = ascii(7, "world");
		ByteBlob result = ByteBlobs.concat(Arrays.<ByteBlob>asList(a, b));
		assertTrue(result instanceof CompoundByteBlob);
		assertEquals(2, result.getChunks().size());
		assertEquals("helloworld", contentString(result));
	}
	
	public void testConcat_emptyInterspersedStillMerges() {
		ByteChunk buf = ascii(7, "helloworld");
		ByteChunk a = new ByteChunk(buf.buffer, 7, 5);
		ByteChunk b = new ByteChunk(buf.buffer, 12, 5);
		ByteBlob result = ByteBlobs.concat(Arrays.<ByteBlob>asList(a, ByteChunk.EMPTY, b));
		assertTrue(result instanceof ByteChunk);
		assertEquals("helloworld", contentString(result));
	}
	
	public void testConcat_chainOfThreeAdjacentChunksMerges() {
		ByteChunk buf = ascii(7, "helloworldfoo");
		ByteChunk a = new ByteChunk(buf.buffer, 7, 5);
		ByteChunk b = new ByteChunk(buf.buffer, 12, 5);
		ByteChunk c = new ByteChunk(buf.buffer, 17, 3);
		ByteBlob result = ByteBlobs.concat(Arrays.<ByteBlob>asList(a, b, c));
		assertTrue(result instanceof ByteChunk);
		assertEquals("helloworldfoo", contentString(result));
	}
	
	public void testConcat_partialMerge() {
		ByteChunk buf = ascii(7, "helloworld");
		ByteChunk a = new ByteChunk(buf.buffer, 7, 5);
		ByteChunk b = new ByteChunk(buf.buffer, 12, 5);
		ByteChunk c = ascii(7, "foo");
		ByteBlob result = ByteBlobs.concat(Arrays.<ByteBlob>asList(a, b, c));
		assertTrue(result instanceof CompoundByteBlob);
		List<ByteChunk> chunks = result.getChunks();
		assertEquals(2, chunks.size());
		assertEquals("helloworldfoo", contentString(result));
	}
}
