package net.nuke24.scratch38.s0029;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

public class HELOAuthTokenizerTest extends TestCase {
	private static ByteBlob content(String s) {
		return ByteChunk.of(ByteArrayUtil.ascii(s));
	}
	
	private static Tagged<HELOChunkType,ByteBlob> tagged(HELOChunkType tag, String s) {
		return new Tagged<HELOChunkType,ByteBlob>(tag, content(s));
	}
	
	private static String contentString(ByteBlob blob) {
		StringBuilder sb = new StringBuilder();
		for( ByteChunk c : blob.getChunks() ) {
			for( int i=0; i<c.length; ++i ) sb.append((char)(c.buffer[c.offset+i] & 0xFF));
		}
		return sb.toString();
	}
	
	private static void assertToken(Tagged<HELOAuthChunkType,ByteBlob> token, HELOAuthChunkType tag, String content) {
		assertEquals(tag, token.tag);
		assertEquals(content, contentString(token.content));
	}
	
	public void testSplitsKnownSchemeNonceAndHash() {
		List<Tagged<HELOChunkType,ByteBlob>> input = new ArrayList<Tagged<HELOChunkType,ByteBlob>>();
		input.add(tagged(HELOChunkType.HEADER_NAME, "auth"));
		input.add(tagged(HELOChunkType.DELIMITER, " "));
		input.add(tagged(HELOChunkType.HEADER_VALUE, "NHS1 12346;XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"));
		input.add(tagged(HELOChunkType.DELIMITER, "\n"));
		
		List<Tagged<HELOAuthChunkType,ByteBlob>> result = HELOAuthTokenizer.tokenize(input);
		
		int i = 0;
		assertToken(result.get(i++), HELOAuthChunkType.AUTH_HEADER, "auth");
		assertToken(result.get(i++), HELOAuthChunkType.NORMAL, " ");
		assertToken(result.get(i++), HELOAuthChunkType.AUTH_SCHEME, "NHS1");
		assertToken(result.get(i++), HELOAuthChunkType.NORMAL, " ");
		assertToken(result.get(i++), HELOAuthChunkType.NONCE, "12346");
		assertToken(result.get(i++), HELOAuthChunkType.NORMAL, ";");
		assertToken(result.get(i++), HELOAuthChunkType.HASH, "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
		assertToken(result.get(i++), HELOAuthChunkType.NORMAL, "\n");
		assertEquals(i, result.size());
	}
	
	public void testUnrecognizedSchemeBecomesUnknownData() {
		List<Tagged<HELOChunkType,ByteBlob>> input = new ArrayList<Tagged<HELOChunkType,ByteBlob>>();
		input.add(tagged(HELOChunkType.HEADER_NAME, "auth"));
		input.add(tagged(HELOChunkType.DELIMITER, " "));
		input.add(tagged(HELOChunkType.HEADER_VALUE, "FUTURE1 whatever-this-scheme-uses"));
		
		List<Tagged<HELOAuthChunkType,ByteBlob>> result = HELOAuthTokenizer.tokenize(input);
		
		int i = 0;
		assertToken(result.get(i++), HELOAuthChunkType.AUTH_HEADER, "auth");
		assertToken(result.get(i++), HELOAuthChunkType.NORMAL, " ");
		assertToken(result.get(i++), HELOAuthChunkType.AUTH_SCHEME, "FUTURE1");
		assertToken(result.get(i++), HELOAuthChunkType.NORMAL, " ");
		assertToken(result.get(i++), HELOAuthChunkType.AUTH_UNKNOWN_DATA, "whatever-this-scheme-uses");
		assertEquals(i, result.size());
	}
	
	public void testKnownSchemeWithoutSemicolonFallsBackToUnknownData() {
		List<Tagged<HELOChunkType,ByteBlob>> input = new ArrayList<Tagged<HELOChunkType,ByteBlob>>();
		input.add(tagged(HELOChunkType.HEADER_NAME, "auth"));
		input.add(tagged(HELOChunkType.DELIMITER, " "));
		input.add(tagged(HELOChunkType.HEADER_VALUE, "NHS1 malformed-no-semicolon"));
		
		List<Tagged<HELOAuthChunkType,ByteBlob>> result = HELOAuthTokenizer.tokenize(input);
		
		int i = 0;
		assertToken(result.get(i++), HELOAuthChunkType.AUTH_HEADER, "auth");
		assertToken(result.get(i++), HELOAuthChunkType.NORMAL, " ");
		assertToken(result.get(i++), HELOAuthChunkType.AUTH_SCHEME, "NHS1");
		assertToken(result.get(i++), HELOAuthChunkType.NORMAL, " ");
		assertToken(result.get(i++), HELOAuthChunkType.AUTH_UNKNOWN_DATA, "malformed-no-semicolon");
		assertEquals(i, result.size());
	}
	
	/**
	 * Assuming for now that a single token in the auth header
	 * is not useful as a 'scheme'.  If it turns out that it is,
	 * we can change this to expect the chunk type to be AUTH_SCHEME.
	*/
	public void testSchemeOnlyWithNoValueAtAll() {
		List<Tagged<HELOChunkType,ByteBlob>> input = new ArrayList<Tagged<HELOChunkType,ByteBlob>>();
		input.add(tagged(HELOChunkType.HEADER_NAME, "auth"));
		input.add(tagged(HELOChunkType.DELIMITER, " "));
		input.add(tagged(HELOChunkType.HEADER_VALUE, "NHS1"));
		
		List<Tagged<HELOAuthChunkType,ByteBlob>> result = HELOAuthTokenizer.tokenize(input);
		
		int i = 0;
		assertToken(result.get(i++), HELOAuthChunkType.AUTH_HEADER, "auth");
		assertToken(result.get(i++), HELOAuthChunkType.NORMAL, " ");
		assertToken(result.get(i++), HELOAuthChunkType.AUTH_UNKNOWN_DATA, "NHS1");
		assertEquals(i, result.size());
	}
	
	public void testNonAuthHeadersAreMergedIntoOneNormalSpan() {
		List<Tagged<HELOChunkType,ByteBlob>> input = new ArrayList<Tagged<HELOChunkType,ByteBlob>>();
		input.add(tagged(HELOChunkType.HEADER_NAME, "reqid"));
		input.add(tagged(HELOChunkType.DELIMITER, " "));
		input.add(tagged(HELOChunkType.HEADER_VALUE, "jeffk123"));
		input.add(tagged(HELOChunkType.DELIMITER, "\n"));
		
		List<Tagged<HELOAuthChunkType,ByteBlob>> result = HELOAuthTokenizer.tokenize(input);
		
		// Adjacent NORMAL tokens merge, regardless of how the source tokenizer split them up.
		assertEquals(1, result.size());
		assertToken(result.get(0), HELOAuthChunkType.NORMAL, "reqid jeffk123\n");
	}
	
	public void testEndToEndWithHELOTokenizer() {
		String message =
			"#HELO/PUT /switch1/state\n" +
			"auth NHS1 12346;XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\n" +
			"\n" +
			"on\n";
		List<Tagged<HELOChunkType,ByteBlob>> helo = HELOTokenizer.tokenize(ByteArrayUtil.ascii(message));
		List<Tagged<HELOAuthChunkType,ByteBlob>> auth = HELOAuthTokenizer.tokenize(helo);
		
		// Content is preserved exactly; only tags/splitting change.
		StringBuilder heloContent = new StringBuilder();
		for( Tagged<HELOChunkType,ByteBlob> t : helo ) heloContent.append(contentString(t.content));
		StringBuilder authContent = new StringBuilder();
		for( Tagged<HELOAuthChunkType,ByteBlob> t : auth ) authContent.append(contentString(t.content));
		assertEquals(heloContent.toString(), authContent.toString());
		
		// Merging collapses runs of adjacent NORMAL tokens, so this isn't sensitive to
		// exactly how HELOTokenizer split up the non-auth parts of the message.
		int i = 0;
		assertToken(auth.get(i++), HELOAuthChunkType.NORMAL, "#HELO/PUT /switch1/state\n");
		assertToken(auth.get(i++), HELOAuthChunkType.AUTH_HEADER, "auth");
		assertToken(auth.get(i++), HELOAuthChunkType.NORMAL, " ");
		assertToken(auth.get(i++), HELOAuthChunkType.AUTH_SCHEME, "NHS1");
		assertToken(auth.get(i++), HELOAuthChunkType.NORMAL, " ");
		assertToken(auth.get(i++), HELOAuthChunkType.NONCE, "12346");
		assertToken(auth.get(i++), HELOAuthChunkType.NORMAL, ";");
		assertToken(auth.get(i++), HELOAuthChunkType.HASH, "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
		assertToken(auth.get(i++), HELOAuthChunkType.NORMAL, "\n\non\n");
		assertEquals(i, auth.size());
	}
}
