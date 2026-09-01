package net.nuke24.scratch38.s0029;

import junit.framework.TestCase;

public class ByteChunkTest extends TestCase {
	private static byte[] ascii(int padding, String s) {
		byte[] pad = new byte[padding];
		byte[] content;
		try {
			content = s.getBytes("US-ASCII");
		} catch( java.io.UnsupportedEncodingException e ) {
			throw new RuntimeException(e);
		}
		byte[] buf = new byte[pad.length + content.length];
		System.arraycopy(content, 0, buf, pad.length, content.length);
		return buf;
	}
	
	public void testEqualsAndHashCode_sameContentDifferentBuffers() {
		ByteChunk a = new ByteChunk(ascii(7, "hello"), 7, 5);
		ByteChunk b = new ByteChunk(ascii(7, "hello"), 7, 5);
		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
	}
	
	public void testEqualsAndHashCode_sameContentDifferentOffsets() {
		byte[] buf = ascii(7, "helloXXXhello");
		ByteChunk a = new ByteChunk(buf, 7, 5);
		ByteChunk b = new ByteChunk(buf, 15, 5);
		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
	}
	
	public void testEquals_reflexive() {
		ByteChunk a = new ByteChunk(ascii(7, "hello"), 7, 5);
		assertEquals(a, a);
	}
	
	public void testNotEquals_differentLength() {
		ByteChunk a = new ByteChunk(ascii(7, "hello"), 7, 5);
		ByteChunk b = new ByteChunk(ascii(7, "hell"), 7, 4);
		assertFalse(a.equals(b));
	}
	
	public void testNotEquals_differentContent() {
		ByteChunk a = new ByteChunk(ascii(7, "hello"), 7, 5);
		ByteChunk b = new ByteChunk(ascii(7, "jello"), 7, 5);
		assertFalse(a.equals(b));
	}
	
	public void testNotEquals_otherTypesAndNull() {
		ByteChunk a = new ByteChunk(ascii(7, "hello"), 7, 5);
		assertFalse(a.equals("hello"));
		assertFalse(a.equals(null));
	}
	
	public void testToString_empty() {
		ByteChunk c = new ByteChunk(ascii(7, ""), 7, 0);
		assertEquals("ByteChunk{data:,%7D", c.toString());
	}
	
	public void testToString_withBraces() {
		ByteChunk c = new ByteChunk(ascii(7, "a{b}c"), 7, 5);
		assertEquals("ByteChunk{data:,a%7Bb%7Dc%7D", c.toString());
	}
}
