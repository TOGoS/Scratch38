package net.nuke24.scratch38.s0029;

import java.security.MessageDigest;
import java.util.List;

/**
 * HMAC (RFC 2104) over any streaming {@link MessageDigest}, plus SHA-1
 * conveniences backed by {@link SHA1MessageDigest}.
 */
public class HMACUtil {
	/**
	 * HMAC of {@code message} under {@code key}, using {@code md} as the hash and
	 * {@code blockSize} as the hash's input block size (64 for SHA-1).
	 */
	public static byte[] hmac(MessageDigest md, int blockSize, byte[] key, List<ByteChunk> message) {
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
		for( ByteChunk chunk : message ) {
			md.update(chunk.buffer, chunk.offset, chunk.length);
		}
		byte[] innerHash = md.digest();
		
		md.reset();
		md.update(oKeyPad);
		md.update(innerHash);
		return md.digest();
	}
}
