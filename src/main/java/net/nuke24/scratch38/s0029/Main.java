package net.nuke24.scratch38.s0029;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;

public class Main {
	static int failures = 0;
	
	static byte[] ascii(String s) {
		try {
			return s.getBytes("US-ASCII");
		} catch( UnsupportedEncodingException e ) {
			throw new RuntimeException(e);
		}
	}
	
	static byte[] repeat(int value, int count) {
		byte[] b = new byte[count];
		for( int i = 0; i < count; ++i ) b[i] = (byte)value;
		return b;
	}
	
	static void check(String label, String expected, String actual) {
		boolean ok = expected.equals(actual);
		if( !ok ) ++failures;
		System.out.println((ok ? "PASS " : "FAIL ") + label);
		if( !ok ) {
			System.out.println("  expected " + expected);
			System.out.println("  actual   " + actual);
		}
	}
	
	public static byte[] sha1(byte[] message) {
		MessageDigest md = new SHA1MessageDigest();
		md.update(message);
		return md.digest();
	}
	
	/**
	 * HMAC of {@code message} under {@code key}, using {@code md} as the hash and
	 * {@code blockSize} as the hash's input block size (64 for SHA-1).
	 */
	public static byte[] hmac(MessageDigest md, int blockSize, byte[] key, byte[] message) {
		if( key.length > blockSize ) {
			md.reset();
			key = md.digest(key);
		}
		byte[] iKeyPad = new byte[blockSize];
		byte[] oKeyPad = new byte[blockSize];
		for( int i = 0; i < blockSize; ++i ) {
			int kb = i < key.length ? key[i] & 0xFF : 0;
			iKeyPad[i] = (byte)(kb ^ 0x36);
			oKeyPad[i] = (byte)(kb ^ 0x5C);
		}
		
		md.reset();
		md.update(iKeyPad);
		md.update(message);
		byte[] innerHash = md.digest();
		
		md.reset();
		md.update(oKeyPad);
		md.update(innerHash);
		return md.digest();
	}
	
	public static byte[] hmacSha1(byte[] key, byte[] message) {
		return hmac(new SHA1MessageDigest(), SHA1MessageDigest.BLOCK_SIZE, key, message);
	}
	
	
	public static void main(String[] args) {
		// SHA-1 test vectors (FIPS 180 / common)
		check("sha1(\"abc\")", "a9993e364706816aba3e25717850c26c9cd0d89d",
			StringUtil.toHex(sha1(ascii("abc"))));
		check("sha1(\"\")", "da39a3ee5e6b4b0d3255bfef95601890afd80709",
			StringUtil.toHex(sha1(ascii(""))));
		
		// HMAC-SHA1 test vectors (RFC 2202)
		check("hmac case 1", "b617318655057264e28bc0b6fb378c8ef146be00",
			StringUtil.toHex(hmacSha1(repeat(0x0b, 20), ascii("Hi There"))));
		check("hmac case 2", "effcdf6ae5eb2fa2d27416d5f184df9c259a7c79",
			StringUtil.toHex(hmacSha1(ascii("Jefe"), ascii("what do ya want for nothing?"))));
		check("hmac case 3", "125d7342b9ac11cd91a39af48aa17b4f63f175d3",
			StringUtil.toHex(hmacSha1(repeat(0xaa, 20), repeat(0xdd, 50))));
		
		// The HELO spec's worked example, computed over the exact <pre> bytes.
		String heloMessage =
			"#HELO/PUT /switch1/state\n" +
			"auth NHS1 12346;XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\n" +
			"\n" +
			"on\n";
		System.out.println("HELO example HMAC-SHA1 (secret \"foo\"): " +
			StringUtil.toHex(hmacSha1(ascii("foo"), ascii(heloMessage))));
		System.out.println("spec claims:                          7a83499ddfaad3c92862bdecbf017f0f68d2cf3a");
		
		if( failures > 0 ) {
			System.out.println(failures + " test(s) FAILED");
			System.exit(1);
		}
		System.out.println("All test vectors passed.");
	}
}
