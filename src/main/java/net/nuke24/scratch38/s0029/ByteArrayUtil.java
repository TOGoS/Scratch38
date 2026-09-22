package net.nuke24.scratch38.s0029;

import java.io.UnsupportedEncodingException;

public class ByteArrayUtil
{
	private ByteArrayUtil() { throw new RuntimeException("Don't instantiate me, bro!"); }
	
	public static byte[] ascii(String s) {
		try {
			return s.getBytes("US-ASCII");
		} catch( UnsupportedEncodingException e ) {
			throw new RuntimeException(e);
		}
	}
	
	public static byte[] repeat(int value, int count) {
		byte[] b = new byte[count];
		for( int i = 0; i < count; ++i ) b[i] = (byte)value;
		return b;
	}
}
