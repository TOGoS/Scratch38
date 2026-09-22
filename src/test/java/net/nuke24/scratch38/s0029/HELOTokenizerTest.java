package net.nuke24.scratch38.s0029;

import java.util.List;

import junit.framework.TestCase;

public class HELOTokenizerTest extends TestCase {
	private static List<Tagged<HELOChunkType,ByteBlob>> tokenize(String s) {
		return HELOTokenizer.tokenize(ByteArrayUtil.ascii(s));
	}
	
	private static String contentString(ByteBlob blob) {
		StringBuilder sb = new StringBuilder();
		for( ByteChunk c : blob.getChunks() ) {
			for( int i=0; i<c.length; ++i ) sb.append((char)(c.buffer[c.offset+i] & 0xFF));
		}
		return sb.toString();
	}
	
	private static String reconstruct(List<Tagged<HELOChunkType,ByteBlob>> tokens) {
		StringBuilder sb = new StringBuilder();
		for( Tagged<HELOChunkType,ByteBlob> token : tokens ) sb.append(contentString(token.content));
		return sb.toString();
	}
	
	private static void assertToken(Tagged<HELOChunkType,ByteBlob> token, HELOChunkType tag, String content) {
		assertEquals(tag, token.tag);
		assertEquals(content, contentString(token.content));
	}
	
	public void testSimplestMessage() {
		List<Tagged<HELOChunkType,ByteBlob>> tokens = tokenize("#HELO");
		assertEquals(1, tokens.size());
		assertToken(tokens.get(0), HELOChunkType.HELO_MAGIC, "#HELO");
	}
	
	public void testSensorAnnouncement() {
		String message =
			"#HELO //ab-cd-ef-01-23-45/\n" +
			"\n" +
			"temperature1 20C\n" +
			"humidity1 35%\n";
		List<Tagged<HELOChunkType,ByteBlob>> tokens = tokenize(message);
		assertEquals(message, reconstruct(tokens));
		
		int i = 0;
		assertToken(tokens.get(i++), HELOChunkType.HELO_MAGIC, "#HELO");
		assertToken(tokens.get(i++), HELOChunkType.DELIMITER, " ");
		assertToken(tokens.get(i++), HELOChunkType.RESOURCE_PATH, "//ab-cd-ef-01-23-45/");
		assertToken(tokens.get(i++), HELOChunkType.DELIMITER, "\n");
		assertToken(tokens.get(i++), HELOChunkType.DELIMITER, "\n");
		assertToken(tokens.get(i++), HELOChunkType.PAYLOAD, "temperature1 20C\nhumidity1 35%\n");
		assertEquals(i, tokens.size());
	}
	
	public void testVersionAndMethod() {
		String message = "#HELO/1234/PUT /switch1\n\noff";
		List<Tagged<HELOChunkType,ByteBlob>> tokens = tokenize(message);
		assertEquals(message, reconstruct(tokens));
		
		int i = 0;
		assertToken(tokens.get(i++), HELOChunkType.HELO_MAGIC, "#HELO");
		assertToken(tokens.get(i++), HELOChunkType.DELIMITER, "/");
		assertToken(tokens.get(i++), HELOChunkType.HELO_VERSION, "1234");
		assertToken(tokens.get(i++), HELOChunkType.DELIMITER, "/");
		assertToken(tokens.get(i++), HELOChunkType.METHOD, "PUT");
		assertToken(tokens.get(i++), HELOChunkType.DELIMITER, " ");
		assertToken(tokens.get(i++), HELOChunkType.RESOURCE_PATH, "/switch1");
		assertToken(tokens.get(i++), HELOChunkType.DELIMITER, "\n");
		assertToken(tokens.get(i++), HELOChunkType.DELIMITER, "\n");
		assertToken(tokens.get(i++), HELOChunkType.PAYLOAD, "off");
		assertEquals(i, tokens.size());
	}
	
	public void testSingleSlashSegmentGuessedAsMethodWhenNonNumeric() {
		String message =
			"#HELO/PUT /switch1/state\n" +
			"auth NHS1 12346;XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\n" +
			"\n" +
			"on\n";
		List<Tagged<HELOChunkType,ByteBlob>> tokens = tokenize(message);
		assertEquals(message, reconstruct(tokens));
		
		int i = 0;
		assertToken(tokens.get(i++), HELOChunkType.HELO_MAGIC, "#HELO");
		assertToken(tokens.get(i++), HELOChunkType.DELIMITER, "/");
		assertToken(tokens.get(i++), HELOChunkType.METHOD, "PUT");
		assertToken(tokens.get(i++), HELOChunkType.DELIMITER, " ");
		assertToken(tokens.get(i++), HELOChunkType.RESOURCE_PATH, "/switch1/state");
		assertToken(tokens.get(i++), HELOChunkType.DELIMITER, "\n");
		assertToken(tokens.get(i++), HELOChunkType.HEADER_NAME, "auth");
		assertToken(tokens.get(i++), HELOChunkType.DELIMITER, " ");
		assertToken(tokens.get(i++), HELOChunkType.HEADER_VALUE, "NHS1 12346;XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
		assertToken(tokens.get(i++), HELOChunkType.DELIMITER, "\n");
		assertToken(tokens.get(i++), HELOChunkType.DELIMITER, "\n");
		assertToken(tokens.get(i++), HELOChunkType.PAYLOAD, "on\n");
		assertEquals(i, tokens.size());
	}
	
	public void testResourcePathContainingSlashesAndNoMethod() {
		String message =
			"#HELO /supported-auth-schemes/NHS1\n" +
			"\n" +
			"name NHS1\n" +
			"nonce-range 12345 12400\n";
		List<Tagged<HELOChunkType,ByteBlob>> tokens = tokenize(message);
		assertEquals(message, reconstruct(tokens));
		
		int i = 0;
		assertToken(tokens.get(i++), HELOChunkType.HELO_MAGIC, "#HELO");
		assertToken(tokens.get(i++), HELOChunkType.DELIMITER, " ");
		assertToken(tokens.get(i++), HELOChunkType.RESOURCE_PATH, "/supported-auth-schemes/NHS1");
		assertToken(tokens.get(i++), HELOChunkType.DELIMITER, "\n");
		assertToken(tokens.get(i++), HELOChunkType.DELIMITER, "\n");
		assertToken(tokens.get(i++), HELOChunkType.PAYLOAD, "name NHS1\nnonce-range 12345 12400\n");
		assertEquals(i, tokens.size());
	}
	
	public void testHeadersWithNoBlankLineOrPayload() {
		String message = "#HELO/GET /something\nreqid jeffk123\n";
		List<Tagged<HELOChunkType,ByteBlob>> tokens = tokenize(message);
		assertEquals(message, reconstruct(tokens));
		
		int i = 0;
		assertToken(tokens.get(i++), HELOChunkType.HELO_MAGIC, "#HELO");
		assertToken(tokens.get(i++), HELOChunkType.DELIMITER, "/");
		assertToken(tokens.get(i++), HELOChunkType.METHOD, "GET");
		assertToken(tokens.get(i++), HELOChunkType.DELIMITER, " ");
		assertToken(tokens.get(i++), HELOChunkType.RESOURCE_PATH, "/something");
		assertToken(tokens.get(i++), HELOChunkType.DELIMITER, "\n");
		assertToken(tokens.get(i++), HELOChunkType.HEADER_NAME, "reqid");
		assertToken(tokens.get(i++), HELOChunkType.DELIMITER, " ");
		assertToken(tokens.get(i++), HELOChunkType.HEADER_VALUE, "jeffk123");
		assertToken(tokens.get(i++), HELOChunkType.DELIMITER, "\n");
		assertEquals(i, tokens.size());
		assertEquals(HELOChunkType.HEADER_VALUE, tokens.get(8).tag);
	}
	
	public void testHeaderValueLineContinuation() {
		String message = "#HELO\nname value1\n\tvalue2\n\n";
		List<Tagged<HELOChunkType,ByteBlob>> tokens = tokenize(message);
		
		Tagged<HELOChunkType,ByteBlob> valueToken = null;
		for( Tagged<HELOChunkType,ByteBlob> token : tokens ) {
			if( token.tag == HELOChunkType.HEADER_VALUE ) valueToken = token;
		}
		assertNotNull(valueToken);
		assertEquals("value1\nvalue2", contentString(valueToken.content));
		// The continuation tab is not part of the value, so it's not just one contiguous chunk.
		assertEquals(2, valueToken.content.getChunks().size());
		
		// The tab is excluded from the value, so reconstruction won't reproduce it byte-for-byte.
		assertEquals(message.replace("\n\t", "\n"), reconstruct(tokens));
	}
	
	public void testMissingMagicThrows() {
		try {
			tokenize("nope this isn't a HELO message");
			fail("Expected IllegalArgumentException for missing #HELO magic");
		} catch( IllegalArgumentException e ) {
			// expected
		}
	}
}
