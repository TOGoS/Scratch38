package net.nuke24.togetl.ident;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Mints the tag URIs used for instances and derived records.
 *
 * <p>The date in the authority is fixed. Changing it would silently re-identify
 * every instance, so it is a constant rather than an option.</p>
 */
public final class Identifiers {
	public static final String TAG_DATE = "2026-11";
	public static final String TAG_PREFIX = "tag:nuke24.net," + TAG_DATE + ":togetl:";
	public static final String DATA_TAG_PREFIX = "tag:nuke24.net," + TAG_DATE + ":3dparts:";
	
	private static final char[] HEX = "0123456789abcdef".toCharArray();
	
	private Identifiers() {
	}
	
	/**
	 * Mints {@code tag:nuke24.net,2026-11:togetl:<type>:<key>}. The type is
	 * expected to be a lowercase slug; the key is percent-encoded outside
	 * {@code A-Za-z0-9-._~:}.
	 */
	public static String mint(String type, String key) {
		return mintNamespace(TAG_PREFIX, type, key);
	}
	
	public static String mintData(String type, String key) {
		return mintNamespace(DATA_TAG_PREFIX, type, key);
	}
	
	public static String mintNamespace(String namespace, String type, String key) {
		checkType(type);
		if (namespace == null || namespace.isEmpty()) {
			throw new IllegalArgumentException("Identifier namespace must not be empty");
		}
		if (key == null) {
			throw new IllegalArgumentException("Identifier key must not be null");
		}
		return namespace + type + ":" + encodeKey(key);
	}
	
	/**
	 * The document IRI is derived from the source URL, not from its content, so
	 * that a moved document keeps its identity. The content hash is recorded
	 * separately as {@code togetl:sha256}.
	 */
	public static String documentIri(String sourceUrl) {
		if (sourceUrl == null || sourceUrl.isEmpty()) {
			throw new IllegalArgumentException("sourceUrl must not be empty");
		}
		return mint("document", shortSha256(sourceUrl.getBytes(StandardCharsets.UTF_8)));
	}
	
	/** A deterministic identifier for one document import against one document. */
	public static String documentImportIri(String documentIri, String parserVersion) {
		if (documentIri == null || documentIri.isEmpty()) {
			throw new IllegalArgumentException("documentIri must not be empty");
		}
		if (parserVersion == null || parserVersion.isEmpty()) {
			throw new IllegalArgumentException("parserVersion must not be empty");
		}
		return mint(
				"document-import",
				shortSha256((documentIri + "\n" + parserVersion).getBytes(StandardCharsets.UTF_8)));
	}

	/** A generic TEF entry resource within a document. */
	public static String tefEntryIri(String documentIri, int ordinal) {
		if (ordinal < 0) {
			throw new IllegalArgumentException("ordinal must be >= 0");
		}
		return mint("tef-entry", documentKey(documentIri) + ":" + ordinal);
	}
	
	/**
	 * Interprets a source reference key. This is the single place where the
	 * provisional identifier scheme for project IDs is decided.
	 */
	public static String referenceIri(String key) {
		if (key == null) {
			throw new IllegalArgumentException("Reference key must not be null");
		}
		String trimmed = key.trim();
		if (trimmed.startsWith("WSPROJECT-")) {
			return mintData("project", trimmed);
		}
		if (trimmed.startsWith("WSITEM-")) {
			return mintData("item", trimmed);
		}
		return mintData("part", trimmed);
	}
	
	/** The part of a minted IRI after the common prefix, used to scope blank nodes. */
	public static String documentKey(String documentIri) {
		if (documentIri == null || !documentIri.startsWith(TAG_PREFIX)) {
			throw new IllegalArgumentException("Not a togetl tag URI: " + documentIri);
		}
		return documentIri.substring(TAG_PREFIX.length());
	}
	
	/** The hash segment of a document IRI, safe for use in blank node labels. */
	public static String documentHash(String documentIri) {
		String key = documentKey(documentIri);
		if (!key.startsWith("document:")) {
			throw new IllegalArgumentException("Not a document IRI: " + documentIri);
		}
		return key.substring("document:".length());
	}
	
	public static String sha256(byte[] bytes) {
		if (bytes == null) {
			throw new IllegalArgumentException("bytes must not be null");
		}
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is unavailable", e);
		}
		return hex(digest.digest(bytes));
	}
	
	public static String shortSha256(byte[] bytes) {
		return sha256(bytes).substring(0, 16);
	}
	
	/**
	 * Percent-encodes a key segment. Colons are kept because they are legal in
	 * a tag URI key and make nested identities readable.
	 */
	public static String encodeKey(String key) {
		byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
		StringBuilder out = new StringBuilder(bytes.length);
		for (byte b : bytes) {
			int c = b & 0xFF;
			if (isKeySafe(c)) {
				out.append((char) c);
			} else {
				out.append('%');
				out.append(HEX[c >>> 4]);
				out.append(HEX[c & 0x0F]);
			}
		}
		return out.toString();
	}
	
	private static boolean isKeySafe(int c) {
		if (c >= 'A' && c <= 'Z') return true;
		if (c >= 'a' && c <= 'z') return true;
		if (c >= '0' && c <= '9') return true;
		switch (c) {
			case '-': case '.': case '_': case '~': case ':':
				return true;
			default:
				return false;
		}
	}
	
	private static void checkType(String type) {
		if (type == null || type.isEmpty()) {
			throw new IllegalArgumentException("Identifier type must not be empty");
		}
		char first = type.charAt(0);
		if (first < 'a' || first > 'z') {
			throw new IllegalArgumentException("Identifier type must start with a lowercase letter: " + type);
		}
		for (int i = 1; i < type.length(); i++) {
			char c = type.charAt(i);
			if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-')) {
				throw new IllegalArgumentException("Identifier type must be a lowercase slug: " + type);
			}
		}
	}
	
	public static String hex(byte[] bytes) {
		char[] chars = new char[bytes.length * 2];
		for (int i = 0; i < bytes.length; i++) {
			int b = bytes[i] & 0xFF;
			chars[i * 2] = HEX[b >>> 4];
			chars[i * 2 + 1] = HEX[b & 0x0F];
		}
		return new String(chars);
	}
}
