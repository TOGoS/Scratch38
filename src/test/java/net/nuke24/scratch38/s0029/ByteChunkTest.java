package net.nuke24.scratch38.s0029;

import junit.framework.TestCase;

public class ByteChunkTest extends TestCase {
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
	
	public void testEqualsAndHashCode_sameContentDifferentBuffers() {
		ByteChunk a = ascii(7, "hello");
		ByteChunk b = ascii(7, "hello");
		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
	}
	
	public void testEqualsAndHashCode_sameContentDifferentOffsets() {
		ByteChunk buf = ascii(7, "helloXXXhello");
		ByteChunk a = new ByteChunk(buf.buffer, 7, 5);
		ByteChunk b = new ByteChunk(buf.buffer, 15, 5);
		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
	}
	
	public void testEquals_reflexive() {
		ByteChunk a = ascii(7, "hello");
		assertEquals(a, a);
	}
	
	public void testNotEquals_differentLength() {
		ByteChunk a = ascii(7, "hello");
		ByteChunk b = ascii(7, "hell");
		assertFalse(a.equals(b));
	}
	
	public void testNotEquals_differentContent() {
		ByteChunk a = ascii(7, "hello");
		ByteChunk b = ascii(7, "jello");
		assertFalse(a.equals(b));
	}
	
	public void testNotEquals_otherTypesAndNull() {
		ByteChunk a = ascii(7, "hello");
		assertFalse(a.equals("hello"));
		assertFalse(a.equals(null));
	}
	
	public void testToString_empty() {
		ByteChunk c = ascii(7, "");
		assertEquals("ByteChunk{data:,%7D", c.toString());
	}
	
	public void testToString_withBraces() {
		ByteChunk c = ascii(7, "a{b}c");
		assertEquals("ByteChunk{data:,a%7Bb%7Dc%7D", c.toString());
	}
}
