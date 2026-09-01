package net.nuke24.scratch38.s0029;

import java.security.MessageDigest;

/**
 * HMAC (RFC 2104) over any streaming {@link MessageDigest}, plus SHA-1
 * conveniences backed by {@link Sha1MessageDigest}.
 */
public class HmacSha1 {
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
	
	public static byte[] sha1(byte[] message) {
		MessageDigest md = new Sha1MessageDigest();
		md.update(message);
		return md.digest();
	}
	
	public static byte[] hmacSha1(byte[] key, byte[] message) {
		return hmac(new Sha1MessageDigest(), Sha1MessageDigest.BLOCK_SIZE, key, message);
	}
	
	public static String toHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for( byte b : bytes ) {
			sb.append(Character.forDigit((b >> 4) & 0xF, 16));
			sb.append(Character.forDigit(b & 0xF, 16));
		}
		return sb.toString();
	}
}
