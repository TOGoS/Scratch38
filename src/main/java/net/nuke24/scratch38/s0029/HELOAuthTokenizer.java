package net.nuke24.scratch38.s0029;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Re-tags the "auth" header's name and value from a generic {@link HELOChunkType}
 * token stream into {@link HELOAuthChunkType} spans (scheme/nonce/hash); every
 * other token is passed through untouched, tagged {@link HELOAuthChunkType#NORMAL}.
 * Adjacent NORMAL spans are merged, since callers shouldn't care how the "don't
 * care" parts of the message happened to get split up by earlier stages.
 */
public class HELOAuthTokenizer {
	private static final ByteChunk AUTH_HEADER_NAME = ByteChunk.of(ByteArrayUtil.ascii("auth"));
	private static final ByteChunk NHS1_SCHEME = ByteChunk.of(ByteArrayUtil.ascii("NHS1"));
	private static final Set<HELOAuthChunkType> MERGEABLE_TAGS = EnumSet.of(HELOAuthChunkType.NORMAL);
	
	public static List<Tagged<HELOAuthChunkType,ByteBlob>> tokenize(List<Tagged<HELOChunkType,ByteBlob>> tokens) {
		List<Tagged<HELOAuthChunkType,ByteBlob>> result = new ArrayList<Tagged<HELOAuthChunkType,ByteBlob>>();
		boolean inAuthHeader = false;
		for( Tagged<HELOChunkType,ByteBlob> token : tokens ) {
			if( token.tag == HELOChunkType.HEADER_NAME ) {
				inAuthHeader = ByteBlobs.contentEquals(token.content, AUTH_HEADER_NAME);
				result.add(new Tagged<HELOAuthChunkType,ByteBlob>(inAuthHeader ? HELOAuthChunkType.AUTH_HEADER : HELOAuthChunkType.NORMAL, token.content));
			} else if( token.tag == HELOChunkType.HEADER_VALUE && inAuthHeader ) {
				result.addAll(tokenizeAuthValue(token.content));
			} else {
				result.add(new Tagged<HELOAuthChunkType,ByteBlob>(HELOAuthChunkType.NORMAL, token.content));
			}
		}
		return TaggedLists.mergeMergeable(result, MERGEABLE_TAGS);
	}
	
	/** Splits an "auth" header's value into scheme/nonce/hash, or scheme + unknown data. */
	private static List<Tagged<HELOAuthChunkType,ByteBlob>> tokenizeAuthValue(ByteBlob value) {
		List<Tagged<HELOAuthChunkType,ByteBlob>> result = new ArrayList<Tagged<HELOAuthChunkType,ByteBlob>>();
		int length = value.length();
		int spaceIndex = ByteBlobs.indexOf(value, (byte)' ', 0);
		if( spaceIndex < 0 ) {
			addIfNonEmpty(result, HELOAuthChunkType.AUTH_SCHEME, value);
			return result;
		}
		
		ByteBlob scheme = ByteBlobs.slice(value, 0, spaceIndex);
		addIfNonEmpty(result, HELOAuthChunkType.AUTH_SCHEME, scheme);
		addIfNonEmpty(result, HELOAuthChunkType.NORMAL, ByteBlobs.slice(value, spaceIndex, spaceIndex+1));
		
		int dataStart = spaceIndex + 1;
		boolean knownScheme = ByteBlobs.contentEquals(scheme, NHS1_SCHEME);
		int semiIndex = knownScheme ? ByteBlobs.indexOf(value, (byte)';', dataStart) : -1;
		if( semiIndex < 0 ) {
			addIfNonEmpty(result, HELOAuthChunkType.AUTH_UNKNOWN_DATA, ByteBlobs.slice(value, dataStart, length));
			return result;
		}
		
		addIfNonEmpty(result, HELOAuthChunkType.NONCE, ByteBlobs.slice(value, dataStart, semiIndex));
		addIfNonEmpty(result, HELOAuthChunkType.NORMAL, ByteBlobs.slice(value, semiIndex, semiIndex+1));
		addIfNonEmpty(result, HELOAuthChunkType.HASH, ByteBlobs.slice(value, semiIndex+1, length));
		return result;
	}
	
	private static void addIfNonEmpty(List<Tagged<HELOAuthChunkType,ByteBlob>> result, HELOAuthChunkType tag, ByteBlob content) {
		if( content.length() > 0 ) result.add(new Tagged<HELOAuthChunkType,ByteBlob>(tag, content));
	}
}
