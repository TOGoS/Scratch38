package net.nuke24.scratch38.s0029;

import java.security.MessageDigest;

/**
 * Self-contained SHA-1 as a streaming {@link MessageDigest}, no javax.crypto,
 * so the same block-at-a-time logic can be ported to C++ later.
 */
public class SHA1MessageDigest extends MessageDigest {
	public static final int BLOCK_SIZE = 64;
	public static final int DIGEST_SIZE = 20;
	
	private int h0, h1, h2, h3, h4;
	private final byte[] block = new byte[BLOCK_SIZE];
	private int blockLen;
	private long byteCount;
	private final int[] w = new int[80];
	
	public SHA1MessageDigest() {
		super("SHA-1");
		engineReset();
	}
	
	@Override
	protected void engineReset() {
		h0 = 0x67452301;
		h1 = 0xEFCDAB89;
		h2 = 0x98BADCFE;
		h3 = 0x10325476;
		h4 = 0xC3D2E1F0;
		blockLen = 0;
		byteCount = 0;
	}
	
	@Override
	protected int engineGetDigestLength() {
		return DIGEST_SIZE;
	}
	
	@Override
	protected void engineUpdate(byte input) {
		block[blockLen++] = input;
		++byteCount;
		if( blockLen == BLOCK_SIZE ) {
			processBlock();
			blockLen = 0;
		}
	}
	
	@Override
	protected void engineUpdate(byte[] input, int offset, int len) {
		for( int i = 0; i < len; ++i ) {
			engineUpdate(input[offset + i]);
		}
	}
	
	@Override
	protected byte[] engineDigest() {
		long ml = byteCount * 8;
		// Padding (does not count toward the message length):
		padByte((byte)0x80);
		while( blockLen != BLOCK_SIZE - 8 ) padByte((byte)0);
		for( int i = 7; i >= 0; --i ) padByte((byte)(ml >>> (8 * i)));
		
		byte[] digest = new byte[DIGEST_SIZE];
		putInt(digest, 0, h0);
		putInt(digest, 4, h1);
		putInt(digest, 8, h2);
		putInt(digest, 12, h3);
		putInt(digest, 16, h4);
		engineReset();
		return digest;
	}
	
	private void padByte(byte b) {
		block[blockLen++] = b;
		if( blockLen == BLOCK_SIZE ) {
			processBlock();
			blockLen = 0;
		}
	}
	
	private void processBlock() {
		for( int i = 0; i < 16; ++i ) {
			int j = i * 4;
			w[i] = ((block[j] & 0xFF) << 24)
			     | ((block[j+1] & 0xFF) << 16)
			     | ((block[j+2] & 0xFF) << 8)
			     |  (block[j+3] & 0xFF);
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
	
	private static void putInt(byte[] dest, int offset, int value) {
		dest[offset]   = (byte)(value >>> 24);
		dest[offset+1] = (byte)(value >>> 16);
		dest[offset+2] = (byte)(value >>> 8);
		dest[offset+3] = (byte)value;
	}
}
