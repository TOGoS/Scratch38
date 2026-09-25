package net.nuke24.scratch38.s0029;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static net.nuke24.scratch38.s0029.StringUtil.byteChunk;

public class SignatureUtil {
	static ByteChunk NHS1_SCHEME = byteChunk("NHS1");
	static ByteChunk PLACEHOLDER_HASH = byteChunk("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
	static ByteChunk NEWLINE = byteChunk("\n");
	static ByteChunk SPACE = byteChunk(" ");
	static ByteChunk SEMICOLON = byteChunk(";");
	static ByteChunk AUTH_HEADER_NAME = byteChunk("auth");
	
	static <T> ByteBlob content(List<Tagged<T,ByteBlob>> tokens) {
		ArrayList<ByteBlob> blobs = new ArrayList<>();
		for( Tagged<T,ByteBlob> token : tokens ) {
			blobs.add(token.content);
		}
		return ByteBlobs.concat(blobs);
	}
	
	static List<Tagged<HELOChunkType,ByteBlob>> addPlaceholderSignature(ByteChunk nonce, List<Tagged<HELOChunkType,ByteBlob>> tokens) {
		int insertAtPosition = 0;
		findInsertPosition: for( int i = tokens.size()-1; i >= 0; --i ) {
			Tagged<HELOChunkType,ByteBlob> token = tokens.get(i);
			switch( token.tag ) {
			case PAYLOAD:
			case DELIMITER:
				break;
			default:
				insertAtPosition = i+1;
				break findInsertPosition;
			}
		}
		
		List<Tagged<HELOChunkType,ByteBlob>> rewritten = new ArrayList<>();
		for( int i=0; i<tokens.size(); ++i ) {
			if( i == insertAtPosition ) {
				if( insertAtPosition > 0 ) rewritten.add(new Tagged<>(HELOChunkType.DELIMITER, NEWLINE));
				rewritten.add(new Tagged<>(HELOChunkType.HEADER_NAME, AUTH_HEADER_NAME));
				rewritten.add(new Tagged<>(HELOChunkType.DELIMITER, SPACE));
				rewritten.add(new Tagged<>(HELOChunkType.HEADER_VALUE, ByteBlobs.concat(Arrays.<ByteBlob>asList(
					NHS1_SCHEME, SPACE, nonce, SEMICOLON, PLACEHOLDER_HASH
				))));
			}
			rewritten.add(tokens.get(i));
		}
		return rewritten;
	}
	
	static class NoExistingSignatureException extends IllegalArgumentException {
		public NoExistingSignatureException(String message) {
			super(message);
		}
	}
	
	static List<Tagged<HELOAuthChunkType,ByteBlob>> replaceSignature(ByteChunk nonce, ByteChunk signature, List<Tagged<HELOAuthChunkType,ByteBlob>> tokens) {
		List<Tagged<HELOAuthChunkType,ByteBlob>> replacedTokens = new ArrayList<>();
		boolean hasNonce = false;
		boolean hasPlaceholderHash = false;
		
		for( Tagged<HELOAuthChunkType,ByteBlob> token : tokens ) {
			if( token.tag == HELOAuthChunkType.NONCE ) {
				if( !nonce.equals(token.content) ) {
					throw new IllegalArgumentException("Message already has a nonce which is not the one I was passed!");
				}
				hasNonce = true;
			}
			if( token.tag == HELOAuthChunkType.HASH ) {
				token = new Tagged<HELOAuthChunkType, ByteBlob>(HELOAuthChunkType.HASH, signature);
				hasPlaceholderHash = true;
			}
			replacedTokens.add(token);
		}
		
		if( !hasNonce ) throw new NoExistingSignatureException("No nonce token present in message");
		if( !hasPlaceholderHash ) throw new NoExistingSignatureException("No hash token present in message");
		
		return replacedTokens;
	}
	
	static ByteBlob sign(
		ByteChunk nonce,
		List<Tagged<HELOChunkType,ByteBlob>> tokens,
		Function<ByteBlob, ByteChunk> hashFunction
	) {
		List<Tagged<HELOAuthChunkType,ByteBlob>> authTokens = HELOAuthTokenizer.tokenize(tokens);
		try {
			List<Tagged<HELOAuthChunkType,ByteBlob>> halfSigned = replaceSignature(nonce, PLACEHOLDER_HASH, authTokens);
			ByteChunk signature = hashFunction.apply(content(halfSigned));
			return content(replaceSignature(nonce, signature, halfSigned));
		} catch( NoExistingSignatureException e ) {
			// Ok that's fine; then we'll add it.
		}
		
		List<Tagged<HELOChunkType,ByteBlob>> halfSigned = addPlaceholderSignature(nonce, tokens);
		ByteChunk signature = hashFunction.apply(content(halfSigned));
		return content(replaceSignature(nonce, signature, HELOAuthTokenizer.tokenize(halfSigned)));
	}
}
