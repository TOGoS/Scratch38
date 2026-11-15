package net.nuke24.togetl.hash;

import java.security.MessageDigest;

public final class BitprintScheme implements HashURIScheme {
	public static final String BITPRINTURNPREFIX = "urn:bitprint:";
	
	public static final BitprintScheme instance = new BitprintScheme();

	private BitprintScheme() {}
	
	public @Override MessageDigest newDigest() { return new BitprintDigest(); }
	
	public @Override String hashToUri(byte[] hash) {
		byte[] sha1Hash = new byte[20];
		byte[] tigerTreeHash = new byte[24];
		System.arraycopy(hash, 0, sha1Hash, 0, 20);
		System.arraycopy(hash, 20, tigerTreeHash, 0, 24);
		return BITPRINTURNPREFIX + Base32.encode(sha1Hash) + "." + Base32.encode(tigerTreeHash);
	}
}
