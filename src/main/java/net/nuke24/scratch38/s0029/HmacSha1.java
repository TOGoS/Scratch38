package net.nuke24.scratch38.s0029;

/**
 * Self-contained SHA-1 and HMAC-SHA1, no javax.crypto,
 * so the same logic can be ported to C++ later.
 */
public class HmacSha1 {
	public static final int BLOCK_SIZE = 64;
	public static final int DIGEST_SIZE = 20;
	
	/** SHA-1 of the given bytes; returns 20 bytes. */
	public static byte[] sha1(byte[] message) {
		int h0 = 0x67452301;
		int h1 = 0xEFCDAB89;
		int h2 = 0x98BADCFE;
		int h3 = 0x10325476;
		int h4 = 0xC3D2E1F0;
		
		// Padded length: message + 0x80 + zeros + 8-byte length, rounded up to a multiple of 64.
		long ml = (long)message.length * 8;
		int paddedLen = ((message.length + 8) / BLOCK_SIZE + 1) * BLOCK_SIZE;
		byte[] padded = new byte[paddedLen];
		System.arraycopy(message, 0, padded, 0, message.length);
		padded[message.length] = (byte)0x80;
		for( int i = 0; i < 8; ++i ) {
			padded[paddedLen - 1 - i] = (byte)(ml >>> (8 * i));
		}
		
		int[] w = new int[80];
		for( int chunk = 0; chunk < paddedLen; chunk += BLOCK_SIZE ) {
			for( int i = 0; i < 16; ++i ) {
				int j = chunk + i * 4;
				w[i] = ((padded[j] & 0xFF) << 24)
				     | ((padded[j+1] & 0xFF) << 16)
				     | ((padded[j+2] & 0xFF) << 8)
				     |  (padded[j+3] & 0xFF);
			}
			for( int i = 16; i < 80; ++i ) {
				w[i] = Integer.rotateLeft(w[i-3] ^ w[i-8] ^ w[i-14] ^ w[i-16], 1);
			}
			
			int a = h0, b = h1, c = h2, d = h3, e = h4;
			for( int i = 0; i < 80; ++i ) {
				int f, k;
				if( i < 20 ) {
					f = (b & c) | (~b & d);
					k = 0x5A827999;
				} else if( i < 40 ) {
					f = b ^ c ^ d;
					k = 0x6ED9EBA1;
				} else if( i < 60 ) {
					f = (b & c) | (b & d) | (c & d);
					k = 0x8F1BBCDC;
				} else {
					f = b ^ c ^ d;
					k = 0xCA62C1D6;
				}
				int temp = Integer.rotateLeft(a, 5) + f + e + k + w[i];
				e = d;
				d = c;
				c = Integer.rotateLeft(b, 30);
				b = a;
				a = temp;
			}
			
			h0 += a; h1 += b; h2 += c; h3 += d; h4 += e;
		}
		
		byte[] digest = new byte[DIGEST_SIZE];
		putInt(digest, 0, h0);
		putInt(digest, 4, h1);
		putInt(digest, 8, h2);
		putInt(digest, 12, h3);
		putInt(digest, 16, h4);
		return digest;
	}
	
	/** HMAC-SHA1 per RFC 2104; returns 20 bytes. */
	public static byte[] hmacSha1(byte[] key, byte[] message) {
		if( key.length > BLOCK_SIZE ) {
			key = sha1(key);
		}
		byte[] iKeyPad = new byte[BLOCK_SIZE];
		byte[] oKeyPad = new byte[BLOCK_SIZE];
		for( int i = 0; i < BLOCK_SIZE; ++i ) {
			int kb = i < key.length ? key[i] & 0xFF : 0;
			iKeyPad[i] = (byte)(kb ^ 0x36);
			oKeyPad[i] = (byte)(kb ^ 0x5C);
		}
		
		byte[] inner = new byte[BLOCK_SIZE + message.length];
		System.arraycopy(iKeyPad, 0, inner, 0, BLOCK_SIZE);
		System.arraycopy(message, 0, inner, BLOCK_SIZE, message.length);
		byte[] innerHash = sha1(inner);
		
		byte[] outer = new byte[BLOCK_SIZE + DIGEST_SIZE];
		System.arraycopy(oKeyPad, 0, outer, 0, BLOCK_SIZE);
		System.arraycopy(innerHash, 0, outer, BLOCK_SIZE, DIGEST_SIZE);
		return sha1(outer);
	}
	
	public static String toHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for( byte b : bytes ) {
			sb.append(Character.forDigit((b >> 4) & 0xF, 16));
			sb.append(Character.forDigit(b & 0xF, 16));
		}
		return sb.toString();
	}
	
	private static void putInt(byte[] dest, int offset, int value) {
		dest[offset]   = (byte)(value >>> 24);
		dest[offset+1] = (byte)(value >>> 16);
		dest[offset+2] = (byte)(value >>> 8);
		dest[offset+3] = (byte)value;
	}
}
