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
	
	public void testSchemeOnlyWithNoValueAtAll() {
		List<Tagged<HELOChunkType,ByteBlob>> input = new ArrayList<Tagged<HELOChunkType,ByteBlob>>();
		input.add(tagged(HELOChunkType.HEADER_NAME, "auth"));
		input.add(tagged(HELOChunkType.DELIMITER, " "));
		input.add(tagged(HELOChunkType.HEADER_VALUE, "NHS1"));
		
		List<Tagged<HELOAuthChunkType,ByteBlob>> result = HELOAuthTokenizer.tokenize(input);
		
		int i = 0;
		assertToken(result.get(i++), HELOAuthChunkType.AUTH_HEADER, "auth");
		assertToken(result.get(i++), HELOAuthChunkType.NORMAL, " ");
		assertToken(result.get(i++), HELOAuthChunkType.AUTH_SCHEME, "NHS1");
		assertEquals(i, result.size());
	}
	
	public void testNonAuthHeadersAreLeftAsNormal() {
		List<Tagged<HELOChunkType,ByteBlob>> input = new ArrayList<Tagged<HELOChunkType,ByteBlob>>();
		input.add(tagged(HELOChunkType.HEADER_NAME, "reqid"));
		input.add(tagged(HELOChunkType.DELIMITER, " "));
		input.add(tagged(HELOChunkType.HEADER_VALUE, "jeffk123"));
		input.add(tagged(HELOChunkType.DELIMITER, "\n"));
		
		List<Tagged<HELOAuthChunkType,ByteBlob>> result = HELOAuthTokenizer.tokenize(input);
		
		assertEquals(input.size(), result.size());
		for( int i=0; i<input.size(); ++i ) {
			assertToken(result.get(i), HELOAuthChunkType.NORMAL, contentString(input.get(i).content));
		}
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
		
		HELOAuthChunkType[] expectedTagsInOrder = {
			HELOAuthChunkType.NORMAL, // "#HELO"
			HELOAuthChunkType.NORMAL, // "/"
			HELOAuthChunkType.NORMAL, // "PUT"
			HELOAuthChunkType.NORMAL, // " "
			HELOAuthChunkType.NORMAL, // "/switch1/state"
			HELOAuthChunkType.NORMAL, // "\n"
			HELOAuthChunkType.AUTH_HEADER, // "auth"
			HELOAuthChunkType.NORMAL, // " "
			HELOAuthChunkType.AUTH_SCHEME, // "NHS1"
			HELOAuthChunkType.NORMAL, // " "
			HELOAuthChunkType.NONCE, // "12346"
			HELOAuthChunkType.NORMAL, // ";"
			HELOAuthChunkType.HASH, // "XXXX...XX"
			HELOAuthChunkType.NORMAL, // "\n"
			HELOAuthChunkType.NORMAL, // "\n"
			HELOAuthChunkType.NORMAL, // "on\n"
		};
		assertEquals(expectedTagsInOrder.length, auth.size());
		for( int i=0; i<expectedTagsInOrder.length; ++i ) {
			assertEquals("token " + i, expectedTagsInOrder[i], auth.get(i).tag);
		}
	}
}
