package net.nuke24.togetl.hash;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Mirrors ContentCouch's BitprintDigest: a MessageDigest wrapping SHA-1 and a
 * Tiger tree hash, formatted as {@code urn:bitprint:<sha1>.<tigerTree>}.
 */
public final class BitprintDigest extends MessageDigest {
	public static String format(byte[] hash) {
		byte[] sha1Hash = new byte[20];
		byte[] tigerTreeHash = new byte[24];
		System.arraycopy(hash, 0, sha1Hash, 0, 20);
		System.arraycopy(hash, 20, tigerTreeHash, 0, 24);
		return Base32.encode(sha1Hash) + "." + Base32.encode(tigerTreeHash);
	}
	
	public static String of(byte[] bytes) {
		BitprintDigest digest = new BitprintDigest();
		digest.update(bytes);
		return "urn:bitprint:" + format(digest.digest());
	}
	
	private MessageDigest sha1;
	private TigerTree tigerTree;
	
	public BitprintDigest() {
		super("Bitprint");
		try {
			sha1 = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("SHA-1 is unavailable", e);
		}
		tigerTree = new TigerTree();
	}
	
	@Override
	protected void engineUpdate(byte input) {
		sha1.update(input);
		tigerTree.update(input);
	}
	
	@Override
	protected void engineUpdate(byte[] input, int offset, int len) {
		sha1.update(input, offset, len);
		tigerTree.update(input, offset, len);
	}
	
	@Override
	protected byte[] engineDigest() {
		byte[] sha1Hash = sha1.digest();
		byte[] tigerTreeHash = tigerTree.digest();
		byte[] hash = new byte[44];
		System.arraycopy(sha1Hash, 0, hash, 0, 20);
		System.arraycopy(tigerTreeHash, 0, hash, 20, 24);
		return hash;
	}
	
	@Override
	protected void engineReset() {
		sha1.reset();
		tigerTree.reset();
	}
	
	@Override
	public int engineGetDigestLength() {
		return 44;
	}
}
