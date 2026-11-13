package net.nuke24.togetl.hash;

public final class BitprintScheme {
	public static final String BITPRINTURNPREFIX = "urn:bitprint:";

	private BitprintScheme() {
	}

	public static String hashToUrn(byte[] hash) {
		byte[] sha1Hash = new byte[20];
		byte[] tigerTreeHash = new byte[24];
		System.arraycopy(hash, 0, sha1Hash, 0, 20);
		System.arraycopy(hash, 20, tigerTreeHash, 0, 24);
		return BITPRINTURNPREFIX + Base32.encode(sha1Hash) + "." + Base32.encode(tigerTreeHash);
	}

	public static String of(byte[] bytes) {
		BitprintDigest digest = new BitprintDigest();
		digest.update(bytes);
		return hashToUrn(digest.digest());
	}
}
