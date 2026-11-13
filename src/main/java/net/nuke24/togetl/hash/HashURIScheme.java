package net.nuke24.togetl.hash;

import java.security.MessageDigest;

public interface HashURIScheme {
	public MessageDigest newDigest();
	public String hashToUri(byte[] hash);
}
