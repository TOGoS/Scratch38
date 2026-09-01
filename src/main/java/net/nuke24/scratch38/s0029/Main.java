package net.nuke24.scratch38.s0029;

import java.io.UnsupportedEncodingException;

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
	
	public static void main(String[] args) {
		// SHA-1 test vectors (FIPS 180 / common)
		check("sha1(\"abc\")", "a9993e364706816aba3e25717850c26c9cd0d89d",
			HmacSha1.toHex(HmacSha1.sha1(ascii("abc"))));
		check("sha1(\"\")", "da39a3ee5e6b4b0d3255bfef95601890afd80709",
			HmacSha1.toHex(HmacSha1.sha1(ascii(""))));
		
		// HMAC-SHA1 test vectors (RFC 2202)
		check("hmac case 1", "b617318655057264e28bc0b6fb378c8ef146be00",
			HmacSha1.toHex(HmacSha1.hmacSha1(repeat(0x0b, 20), ascii("Hi There"))));
		check("hmac case 2", "effcdf6ae5eb2fa2d27416d5f184df9c259a7c79",
			HmacSha1.toHex(HmacSha1.hmacSha1(ascii("Jefe"), ascii("what do ya want for nothing?"))));
		check("hmac case 3", "125d7342b9ac11cd91a39af48aa17b4f63f175d3",
			HmacSha1.toHex(HmacSha1.hmacSha1(repeat(0xaa, 20), repeat(0xdd, 50))));
		
		// The HELO spec's worked example, computed over the exact <pre> bytes.
		String heloMessage =
			"#HELO/PUT /switch1/state\n" +
			"auth NHS1 12346;XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\n" +
			"\n" +
			"on\n";
		System.out.println("HELO example HMAC-SHA1 (secret \"foo\"): " +
			HmacSha1.toHex(HmacSha1.hmacSha1(ascii("foo"), ascii(heloMessage))));
		System.out.println("spec claims:                          7a83499ddfaad3c92862bdecbf017f0f68d2cf3a");
		
		if( failures > 0 ) {
			System.out.println(failures + " test(s) FAILED");
			System.exit(1);
		}
		System.out.println("All test vectors passed.");
	}
}
