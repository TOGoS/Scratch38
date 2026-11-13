package net.nuke24.togetl.tef;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.nuke24.togetl.ducer.DucerChunk;
import net.nuke24.togetl.ducer.DucerState2;
import net.nuke24.togetl.tef.LLChunks.Chunk;
import net.nuke24.togetl.tef.LLChunks.Comment;
import net.nuke24.togetl.tef.LLChunks.ContentPiece;
import net.nuke24.togetl.tef.LLChunks.Directive;
import net.nuke24.togetl.tef.LLChunks.Header;
import net.nuke24.togetl.tef.LLChunks.HeaderValuePiece;
import net.nuke24.togetl.tef.LLChunks.NewEntryLine;

/**
 * Reads a TEF document into entries, comments, and directives.
 *
 * <p>Input is consumed incrementally: each block goes through UTF-8 checking,
 * the lexer, the chunk merger, and the entry assembler before the next block
 * is read. Only the assembled entries are held in memory.</p>
 *
 * <p>Syntax errors are fatal. The read stops at the first one, and the
 * resulting {@link TEFReadException} names its location.</p>
 */
public final class TEFReader {
	private static final Charset UTF8 = Charset.forName("UTF-8");
	private static final int FEED_BLOCK_SIZE = 65536;
	private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
	
	private TEFReader() {
	}
	
	public static TEFDocument read(String sourceIri, byte[] input)
			throws IOException, TEFReadException {
		if (input == null) {
			throw new IllegalArgumentException("input must not be null");
		}
		return read(sourceIri, new ByteArrayInputStream(input));
	}
	
	public static TEFDocument read(String sourceIri, InputStream in)
			throws IOException, TEFReadException {
		if (sourceIri == null || sourceIri.isEmpty()) {
			throw new IllegalArgumentException("sourceIri must not be empty");
		}
		if (in == null) {
			throw new IllegalArgumentException("input must not be null");
		}
		
		Pipeline pipeline = new Pipeline(sourceIri);
		byte[] block = new byte[FEED_BLOCK_SIZE];
		int filled = fill(in, block);
		long byteLength = filled;
		boolean bom = startsWithBom(block, filled);
		int offset = bom ? UTF8_BOM.length : 0;
		boolean last = filled < block.length;
		while (true) {
			pipeline.feed(Arrays.copyOfRange(block, offset, filled));
			if (last) {
				break;
			}
			filled = fill(in, block);
			byteLength += filled;
			last = filled < block.length;
			offset = 0;
		}
		return pipeline.finish(byteLength, bom);
	}
	
	/** Reads until the buffer is full or the stream ends. */
	private static int fill(InputStream in, byte[] buffer) throws IOException {
		int total = 0;
		while (total < buffer.length) {
			int n = in.read(buffer, total, buffer.length - total);
			if (n < 0) {
				break;
			}
			total += n;
		}
		return total;
	}
	
	private static boolean startsWithBom(byte[] block, int filled) {
		return filled >= UTF8_BOM.length
				&& block[0] == UTF8_BOM[0]
				&& block[1] == UTF8_BOM[1]
				&& block[2] == UTF8_BOM[2];
	}
	
	/** The streaming stages, carried from one block to the next. */
	private static final class Pipeline {
		private final String sourceIri;
		private final Utf8Checker utf8 = new Utf8Checker();
		private final Assembler assembler;
		private DucerState2<byte[], Chunk[]> lexer = TEFParser.INIT;
		private DucerState2<Chunk[], Chunk[]> merger = LLChunkMerger.FULL;
		
		Pipeline(String sourceIri) {
			this.sourceIri = sourceIri;
			this.assembler = new Assembler(sourceIri);
		}
		
		void feed(byte[] bytes) throws TEFReadException {
			step(bytes, false);
		}
		
		TEFDocument finish(long byteLength, boolean bom) throws TEFReadException {
			step(new byte[0], true);
			return assembler.build(byteLength, bom);
		}
		
		private void step(byte[] bytes, boolean end) throws TEFReadException {
			try {
				utf8.check(bytes, end);
				lexer = lexer.process(new DucerChunk<byte[]>(bytes, end));
				merger = merger.process(new DucerChunk<Chunk[]>(lexer.output.payload, end));
				for (Chunk chunk : merger.output.payload) {
					assembler.accept(chunk);
				}
			} catch (TEFSyntaxException e) {
				throw new TEFReadException(
						sourceIri + " " + e.getSpan().toLocation() + ": " + e.getMessage());
			} catch (RuntimeException e) {
				throw new TEFReadException("TEF parse failed: " + e.getMessage(), e);
			}
		}
	}
	
	/**
	 * Checks UTF-8 across block boundaries. A sequence cut by a block boundary
	 * is carried into the next check, so only a few bytes are ever buffered.
	 */
	private static final class Utf8Checker {
		private static final int MAX_CARRY = 3;
		
		private final CharsetDecoder decoder = UTF8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT);
		private final CharBuffer chars = CharBuffer.allocate(FEED_BLOCK_SIZE + MAX_CARRY);
		private byte[] carry = new byte[0];
		/** Byte offset (in the BOM-stripped input) of carry[0]. */
		private long offset;
		private int line = 1;
		private int column = 1;
		
		void check(byte[] bytes, boolean end) throws TEFReadException {
			byte[] buf = new byte[carry.length + bytes.length];
			System.arraycopy(carry, 0, buf, 0, carry.length);
			System.arraycopy(bytes, 0, buf, carry.length, bytes.length);
			
			ByteBuffer in = ByteBuffer.wrap(buf);
			chars.clear();
			CoderResult result = decoder.decode(in, chars, end);
			if (result.isError() || (end && in.hasRemaining())) {
				throw invalidAt(buf, in.position());
			}
			if (end) {
				decoder.flush(chars);
			}
			
			int consumed = in.position();
			advance(buf, consumed);
			carry = Arrays.copyOfRange(buf, consumed, buf.length);
			offset += consumed;
		}
		
		private void advance(byte[] buf, int count) {
			for (int i = 0; i < count; i++) {
				if (buf[i] == '\n') {
					line++;
					column = 1;
				} else {
					column++;
				}
			}
		}
		
		private TEFSyntaxException invalidAt(byte[] buf, int index) {
			int savedLine = line;
			int savedColumn = column;
			advance(buf, index);
			TEFSpan span = TEFSpan.point(offset + index, line, column);
			line = savedLine;
			column = savedColumn;
			return new TEFSyntaxException("Invalid UTF-8 byte sequence", span);
		}
	}
	
	/** Turns merged chunks into entries, comments, and directives. */
	private static final class Assembler {
		private final String sourceIri;
		private final List<TEFEntry> entries = new ArrayList<TEFEntry>();
		private final List<TEFComment> documentComments = new ArrayList<TEFComment>();
		private final List<TEFDirective> documentDirectives = new ArrayList<TEFDirective>();
		private EntryBuilder current;
		
		Assembler(String sourceIri) {
			this.sourceIri = sourceIri;
		}
		
		void accept(Chunk chunk) {
			if (chunk instanceof NewEntryLine) {
				if (current != null) {
					entries.add(current.build());
				}
				NewEntryLine line = (NewEntryLine) chunk;
				current = new EntryBuilder(line.typeString, line.idString, line.getSpan());
			} else if (chunk instanceof Header) {
				if (current == null) {
					current = startImplicitEntry(chunk.getSpan());
				}
				Header header = (Header) chunk;
				current.addHeader(new TEFHeader(header.key, header.value, header.getSpan()));
			} else if (chunk instanceof ContentPiece) {
				if (current == null) {
					current = startImplicitEntry(chunk.getSpan());
				}
				ContentPiece piece = (ContentPiece) chunk;
				current.appendContent(new String(piece.data, UTF8));
				current.touch(piece.getSpan());
			} else if (chunk instanceof Comment) {
				Comment comment = (Comment) chunk;
				TEFComment value = new TEFComment(comment.text, comment.getSpan());
				if (current == null) {
					documentComments.add(value);
				} else {
					current.addComment(value);
				}
			} else if (chunk instanceof Directive) {
				Directive directive = (Directive) chunk;
				TEFDirective value = new TEFDirective(
						directive.name, directive.value, directive.raw, directive.getSpan());
				documentDirectives.add(value);
				if (current != null) {
					current.addDirective(value);
				}
			} else if (chunk instanceof HeaderValuePiece) {
				throw new TEFSyntaxException(
						"Unexpected header continuation", chunk.getSpan());
			}
		}
		
		TEFDocument build(long byteLength, boolean bom) {
			if (current != null) {
				entries.add(current.build());
				current = null;
			}
			return new TEFDocument(
					sourceIri,
					byteLength,
					bom,
					documentComments,
					documentDirectives,
					entries,
					Collections.<TEFIssue>emptyList());
		}
		
		private static EntryBuilder startImplicitEntry(TEFSpan span) {
			return new EntryBuilder("", "", span);
		}
	}
	
	private static final class EntryBuilder {
		private final String type;
		private final String value;
		private final TEFSpan startSpan;
		private TEFSpan endSpan;
		private final List<TEFHeader> headers = new ArrayList<TEFHeader>();
		private final StringBuilder content = new StringBuilder();
		private final List<TEFComment> comments = new ArrayList<TEFComment>();
		private final List<TEFDirective> directives = new ArrayList<TEFDirective>();
		
		EntryBuilder(String type, String value, TEFSpan span) {
			this.type = type;
			this.value = value;
			this.startSpan = span;
			this.endSpan = span;
		}
		
		void touch(TEFSpan span) {
			endSpan = TEFSpan.join(endSpan, span);
		}
		
		void addHeader(TEFHeader header) {
			headers.add(header);
			touch(header.getSpan());
		}
		
		void addComment(TEFComment comment) {
			comments.add(comment);
			touch(comment.getSpan());
		}
		
		void addDirective(TEFDirective directive) {
			directives.add(directive);
			touch(directive.getSpan());
		}
		
		void appendContent(String text) {
			content.append(text);
		}
		
		TEFEntry build() {
			return new TEFEntry(
					type,
					value,
					TEFSpan.join(startSpan, endSpan),
					headers,
					content.toString(),
					comments,
					directives);
		}
	}
}
