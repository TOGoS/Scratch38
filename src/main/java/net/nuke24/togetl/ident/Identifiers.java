package net.nuke24.togetl.ident;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.nuke24.togetl.Namespaces;

/**
 * Mints the tag URIs used for instances and derived records.
 *
 * <p>The namespaces are fixed by {@link Namespaces}. Changing them would
 * silently re-identify every instance, so they are constants rather than
 * options.</p>
 */
public final class Identifiers {
	private static final char[] HEX = "0123456789abcdef".toCharArray();
	
	private Identifiers() {
	}
	
	/**
	 * Mints {@code tag:nuke24.net,2026-11:togetl:<type>:<key>}. The type is
	 * expected to be a lowercase slug; the key is percent-encoded outside
	 * {@code A-Za-z0-9-._~:}.
	 */
	public static String mint(String type, String key) {
		return mintNamespace(Namespaces.TOGETL_NS, type, key);
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
	 * The document IRI is the source URI itself, so a document's identity is its
	 * source URI: the bitprint of its content when that is derived, or the URI
	 * given explicitly. The content hash is also recorded as {@code togetl:sha256}.
	 */
	public static String documentIri(String source) {
		if (source == null || source.isEmpty()) {
			throw new IllegalArgumentException("source must not be empty");
		}
		return source;
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
		return documentIri + "#tef-entry-" + ordinal;
	}
	
	static final Pattern   WSWHATEVER_PATTERN = Pattern.compile("^(?:WSBATCH|WSITEM|WSPROJECT|WSTASK|WSTYPE)-(\\d+)$");
	static final Pattern GFM_PARTNAME_PATTERN = Pattern.compile("^gfm(\\d+)$");
	static final Pattern  BT_PARTNAME_PATTERN = Pattern.compile( "^bt(\\d+)$");
	static final Pattern   P_PARTNAME_PATTERN = Pattern.compile(  "^p(\\d+)$");
	
	/**
	 * Interprets a source reference key. This is the single place where the
	 * provisional identifier scheme for project IDs is decided.
	 */
	public static String shortNameToUri(String shortName) {
		if( shortName == null ) {
			throw new IllegalArgumentException("Reference key must not be null");
		}
		assert( shortName.equals(shortName.trim()) );
		Matcher m;
		if( (m = WSWHATEVER_PATTERN.matcher(shortName)).matches() ) {
			return Namespaces.WSITEM_NS + m.group(1);
		}
		// TODO: These should probably only really be assumed within the context of
		// certain files, since they're such short prefixes.
		if( (m = GFM_PARTNAME_PATTERN.matcher(shortName)).matches() ) {
			return Namespaces.PARTS3D_PARTS_NS + (500 + Integer.valueOf(m.group(1)));
		}
		if( (m =  BT_PARTNAME_PATTERN.matcher(shortName)).matches() ) {
			return Namespaces.PARTS3D_PARTS_NS + (600 + Integer.valueOf(m.group(1)));
		}
		if( (m =   P_PARTNAME_PATTERN.matcher(shortName)).matches() ) {
			return Namespaces.PARTS3D_PARTS_NS + (  0 + Integer.valueOf(m.group(1)));
		}
		throw new IllegalArgumentException("Don't know how tu URN-ify '"+shortName+"'");
	}
	
	/** The part of a minted IRI after the common prefix, used to scope blank nodes. */
	public static String documentKey(String documentIri) {
		if (documentIri == null || documentIri.isEmpty()) {
			throw new IllegalArgumentException("documentIri must not be empty");
		}
		return documentIri;
	}
	
	/** The hash segment of a document IRI, safe for use in blank node labels. */
	public static String documentHash(String documentIri) {
		String key = documentKey(documentIri);
		return shortSha256(key.getBytes(StandardCharsets.UTF_8));
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
