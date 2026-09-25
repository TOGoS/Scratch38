package net.nuke24.scratch38.s0029;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import junit.framework.TestCase;

public class SignatureUtilTest extends TestCase {
	private static String contentString(ByteBlob blob) {
		StringBuilder sb = new StringBuilder();
		for( ByteChunk c : blob.getChunks() ) {
			for( int i=0; i<c.length; ++i ) sb.append((char)(c.buffer[c.offset+i] & 0xFF));
		}
		return sb.toString();
	}
	
	private static ByteChunk chunk(String s) {
		return StringUtil.byteChunk(s);
	}
	
	public void testContent_concatenatesTokenContents() {
		List<Tagged<HELOChunkType,ByteBlob>> tokens = new ArrayList<Tagged<HELOChunkType,ByteBlob>>();
		tokens.add(new Tagged<HELOChunkType,ByteBlob>(HELOChunkType.HELO_MAGIC, chunk("#HELO")));
		tokens.add(new Tagged<HELOChunkType,ByteBlob>(HELOChunkType.DELIMITER, chunk("\n")));
		assertEquals("#HELO\n", contentString(SignatureUtil.content(tokens)));
	}
	
	public void testAddPlaceholderSignature_rawContent() {
		String message =
			"#HELO/PUT /switch1/state\n" +
			"\n" +
			"on\n";
		List<Tagged<HELOChunkType,ByteBlob>> tokens = HELOTokenizer.tokenize(ByteArrayUtil.ascii(message));
		List<Tagged<HELOChunkType,ByteBlob>> withPlaceholder = SignatureUtil.addPlaceholderSignature(chunk("12346"), tokens);
		
		String expected =
			"#HELO/PUT /switch1/state\n" +
			"auth NHS1 12346;XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\n" +
			"\n" +
			"on\n";
		assertEquals(expected, contentString(SignatureUtil.content(withPlaceholder)));
	}
	
	/**
	 * This is the test that would have caught the bug: addPlaceholderSignature was
	 * emitting the scheme/nonce/semicolon/hash as five separate HEADER_VALUE tokens
	 * instead of one combined one, so when re-tokenized by HELOAuthTokenizer, each
	 * fragment got treated as if it were an entire (space-less) header value -- none
	 * of them ended up tagged NONCE or HASH.
	 */
	public void testAddPlaceholderSignature_producesTokensThatAuthTokenizerCanParse() {
		String message =
			"#HELO/PUT /switch1/state\n" +
			"\n" +
			"on\n";
		List<Tagged<HELOChunkType,ByteBlob>> tokens = HELOTokenizer.tokenize(ByteArrayUtil.ascii(message));
		List<Tagged<HELOChunkType,ByteBlob>> withPlaceholder = SignatureUtil.addPlaceholderSignature(chunk("12346"), tokens);
		
		List<Tagged<HELOAuthChunkType,ByteBlob>> authTokens = HELOAuthTokenizer.tokenize(withPlaceholder);
		Tagged<HELOAuthChunkType,ByteBlob> nonceToken = null;
		Tagged<HELOAuthChunkType,ByteBlob> hashToken = null;
		for( Tagged<HELOAuthChunkType,ByteBlob> t : authTokens ) {
			if( t.tag == HELOAuthChunkType.NONCE ) nonceToken = t;
			if( t.tag == HELOAuthChunkType.HASH ) hashToken = t;
		}
		assertNotNull("Expected a NONCE token", nonceToken);
		assertEquals("12346", contentString(nonceToken.content));
		assertNotNull("Expected a HASH token", hashToken);
		assertEquals("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX", contentString(hashToken.content));
	}
	
	public void testReplaceSignature_replacesPlaceholderHash() {
		String message =
			"#HELO/PUT /switch1/state\n" +
			"auth NHS1 12346;XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\n" +
			"\n" +
			"on\n";
		List<Tagged<HELOAuthChunkType,ByteBlob>> authTokens = HELOAuthTokenizer.tokenize(HELOTokenizer.tokenize(ByteArrayUtil.ascii(message)));
		ByteChunk realHash = chunk("7a83499ddfaad3c92862bdecbf017f0f68d2cf3a");
		
		List<Tagged<HELOAuthChunkType,ByteBlob>> replaced = SignatureUtil.replaceSignature(chunk("12346"), realHash, authTokens);
		
		String expected =
			"#HELO/PUT /switch1/state\n" +
			"auth NHS1 12346;7a83499ddfaad3c92862bdecbf017f0f68d2cf3a\n" +
			"\n" +
			"on\n";
		assertEquals(expected, contentString(SignatureUtil.content(replaced)));
	}
	
	public void testReplaceSignature_throwsWhenNoNonceToken() {
		List<Tagged<HELOAuthChunkType,ByteBlob>> authTokens = new ArrayList<Tagged<HELOAuthChunkType,ByteBlob>>();
		authTokens.add(new Tagged<HELOAuthChunkType,ByteBlob>(HELOAuthChunkType.NORMAL, chunk("#HELO\n")));
		try {
			SignatureUtil.replaceSignature(chunk("12346"), chunk("abc"), authTokens);
			fail("Expected NoExistingSignatureException");
		} catch( SignatureUtil.NoExistingSignatureException e ) {
			// expected
		}
	}
	
	public void testReplaceSignature_throwsWhenNonceDoesNotMatch() {
		String message =
			"#HELO/PUT /switch1/state\n" +
			"auth NHS1 99999;XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\n" +
			"\n" +
			"on\n";
		List<Tagged<HELOAuthChunkType,ByteBlob>> authTokens = HELOAuthTokenizer.tokenize(HELOTokenizer.tokenize(ByteArrayUtil.ascii(message)));
		try {
			SignatureUtil.replaceSignature(chunk("12346"), chunk("abc"), authTokens);
			fail("Expected IllegalArgumentException for mismatched nonce");
		} catch( IllegalArgumentException e ) {
			// expected
		}
	}
	
	public void testSign_addsAuthHeaderWhenNoneExists() {
		String message =
			"#HELO/PUT /switch1/state\n" +
			"\n" +
			"on\n";
		List<Tagged<HELOChunkType,ByteBlob>> tokens = HELOTokenizer.tokenize(ByteArrayUtil.ascii(message));
		final ByteChunk fakeHash = chunk("YYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY");
		final ByteBlob[] hashedContent = new ByteBlob[1];
		Function<ByteBlob,ByteChunk> hashFn = content -> {
			hashedContent[0] = content;
			return fakeHash;
		};
		
		ByteBlob signed = SignatureUtil.sign(chunk("12346"), tokens, hashFn);
		
		String expectedHalfSigned =
			"#HELO/PUT /switch1/state\n" +
			"auth NHS1 12346;XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\n" +
			"\n" +
			"on\n";
		assertEquals(expectedHalfSigned, contentString(hashedContent[0]));
		
		String expectedSigned =
			"#HELO/PUT /switch1/state\n" +
			"auth NHS1 12346;YYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY\n" +
			"\n" +
			"on\n";
		assertEquals(expectedSigned, contentString(signed));
	}
	
	public void testSign_replacesExistingPlaceholderHash() {
		String message =
			"#HELO/PUT /switch1/state\n" +
			"auth NHS1 12346;XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\n" +
			"\n" +
			"on\n";
		List<Tagged<HELOChunkType,ByteBlob>> tokens = HELOTokenizer.tokenize(ByteArrayUtil.ascii(message));
		final ByteChunk fakeHash = chunk("YYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY");
		final ByteBlob[] hashedContent = new ByteBlob[1];
		Function<ByteBlob,ByteChunk> hashFn = content -> {
			hashedContent[0] = content;
			return fakeHash;
		};
		
		ByteBlob signed = SignatureUtil.sign(chunk("12346"), tokens, hashFn);
		
		// The message already had the placeholder hash, so it's fed to the hash function unchanged.
		assertEquals(message, contentString(hashedContent[0]));
		
		String expectedSigned =
			"#HELO/PUT /switch1/state\n" +
			"auth NHS1 12346;YYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY\n" +
			"\n" +
			"on\n";
		assertEquals(expectedSigned, contentString(signed));
	}
}
