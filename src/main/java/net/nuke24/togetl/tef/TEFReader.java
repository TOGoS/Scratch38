package net.nuke24.togetl.tef;

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
import java.util.List;
import java.util.Optional;

import net.nuke24.togetl.ducer.DucerChunk;
import net.nuke24.togetl.ducer.DucerState2;
import net.nuke24.togetl.tef.LLChunks.Chunk;
import net.nuke24.togetl.tef.LLChunks.Comment;
import net.nuke24.togetl.tef.LLChunks.ContentPiece;
import net.nuke24.togetl.tef.LLChunks.Directive;
import net.nuke24.togetl.tef.LLChunks.Header;
import net.nuke24.togetl.tef.LLChunks.HeaderValuePiece;
import net.nuke24.togetl.tef.LLChunks.NewEntryLine;
import net.nuke24.togetl.util.Sink;

/**
 * Reads a TEF document, pushing its structure to a {@link TEFSink} as it goes.
 *
 * <p>Input is consumed in blocks: each block goes through UTF-8 checking, the
 * lexer, the chunk merger, and entry assembly before the next block is read.
 * Nothing is retained after it has been handed to the sink.</p>
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
	
	/** Reads a whole document into memory. Prefer the streaming overloads for large input. */
	public static TEFDocument read(String sourceIri, byte[] input)
			throws IOException, TEFReadException {
		if (input == null) {
			throw new IllegalArgumentException("input must not be null");
		}
		Collector collector = new Collector();
		Sink<byte[]> parser = parser(sourceIri, collector);
		parser.accept(Optional.of(input));
		parser.accept(Optional.<byte[]>empty());
		return new TEFDocument(
				sourceIri,
				input.length,
				hasUtf8Bom(input),
				collector.comments,
				collector.directives,
				collector.entries);
	}
	
	public static void read(String sourceIri, InputStream in, TEFSink sink)
			throws IOException, TEFReadException {
		if (in == null) {
			throw new IllegalArgumentException("input must not be null");
		}
		Sink<byte[]> parser = parser(sourceIri, sink);
		byte[] block = new byte[FEED_BLOCK_SIZE];
		int n;
		while ((n = in.read(block)) >= 0) {
			if (n > 0) {
				parser.accept(Optional.of(Arrays.copyOf(block, n)));
			}
		}
		parser.accept(Optional.<byte[]>empty());
	}
	
	/**
	 * Returns a stage that takes the document's bytes in blocks of any size,
	 * ending with {@code Optional.empty()}, and pushes its structure to the sink.
	 */
	public static Sink<byte[]> parser(String sourceIri, TEFSink sink) {
		if (sourceIri == null || sourceIri.isEmpty()) {
			throw new IllegalArgumentException("sourceIri must not be empty");
		}
		if (sink == null) {
			throw new IllegalArgumentException("sink must not be null");
		}
		return new Pipeline(sourceIri, sink);
	}
	
	private static boolean hasUtf8Bom(byte[] input) {
		return input.length >= UTF8_BOM.length
				&& input[0] == UTF8_BOM[0]
				&& input[1] == UTF8_BOM[1]
				&& input[2] == UTF8_BOM[2];
	}
	
	/**
	 * The streaming stages, carried from one block to the next. The first
	 * three bytes are held back until they can be checked for a BOM, so the
	 * block sizes do not matter.
	 */
	private static final class Pipeline implements Sink<byte[]> {
		private final String sourceIri;
		private final Utf8Checker utf8 = new Utf8Checker();
		private final Assembler assembler;
		private DucerState2<byte[], Chunk[]> lexer = TEFParser.INIT;
		private DucerState2<Chunk[], Chunk[]> merger = LLChunkMerger.FULL;
		private byte[] head = new byte[0];
		private boolean started;
		
		Pipeline(String sourceIri, TEFSink sink) {
			this.sourceIri = sourceIri;
			this.assembler = new Assembler(sink);
		}
		
		@Override
		public void accept(Optional<byte[]> item) throws IOException, TEFReadException {
			if (!started) {
				if (!item.isPresent()) {
					start(head, true);
					return;
				}
				head = concat(head, item.get());
				if (head.length < UTF8_BOM.length) {
					return;
				}
				start(head, false);
				return;
			}
			if (item.isPresent()) {
				step(item.get(), false);
			} else {
				step(new byte[0], true);
				assembler.finish();
			}
		}
		
		private void start(byte[] bytes, boolean end) throws IOException, TEFReadException {
			started = true;
			byte[] body = hasUtf8Bom(bytes)
					? Arrays.copyOfRange(bytes, UTF8_BOM.length, bytes.length)
					: bytes;
			head = null;
			step(body, end);
			if (end) {
				assembler.finish();
			}
		}
		
		private static byte[] concat(byte[] a, byte[] b) {
			byte[] out = Arrays.copyOf(a, a.length + b.length);
			System.arraycopy(b, 0, out, a.length, b.length);
			return out;
		}
		
		private void step(byte[] bytes, boolean end) throws IOException, TEFReadException {
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
		private final CharsetDecoder decoder = UTF8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT);
		private final CharBuffer chars = CharBuffer.allocate(4096);
		private byte[] carry = new byte[0];
		/** Byte offset (in the BOM-stripped input) of carry[0]. */
		private long offset;
		private int line = 1;
		private int column = 1;
		
		void check(byte[] bytes, boolean end) throws TEFSyntaxException {
			byte[] buf = new byte[carry.length + bytes.length];
			System.arraycopy(carry, 0, buf, 0, carry.length);
			System.arraycopy(bytes, 0, buf, carry.length, bytes.length);
			
			ByteBuffer in = ByteBuffer.wrap(buf);
			CoderResult result;
			do {
				chars.clear();
				result = decoder.decode(in, chars, end);
				if (result.isError()) {
					throw invalidAt(buf, in.position());
				}
			} while (result.isOverflow());
			if (end && in.hasRemaining()) {
				throw invalidAt(buf, in.position());
			}
			if (end) {
				while (decoder.flush(chars).isOverflow()) {
					chars.clear();
				}
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
	
	/** Turns merged chunks into entries, comments, and directives for the sink. */
	private static final class Assembler {
		private final TEFSink sink;
		private EntryBuilder current;
		
		Assembler(TEFSink sink) {
			this.sink = sink;
		}
		
		void accept(Chunk chunk) throws IOException {
			if (chunk instanceof NewEntryLine) {
				emitCurrent();
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
					sink.documentComment(value);
				} else {
					current.addComment(value);
				}
			} else if (chunk instanceof Directive) {
				Directive directive = (Directive) chunk;
				TEFDirective value = new TEFDirective(
						directive.name, directive.value, directive.raw, directive.getSpan());
				sink.documentDirective(value);
				if (current != null) {
					current.addDirective(value);
				}
			} else if (chunk instanceof HeaderValuePiece) {
				throw new TEFSyntaxException(
						"Unexpected header continuation", chunk.getSpan());
			}
		}
		
		void finish() throws IOException {
			emitCurrent();
		}
		
		private void emitCurrent() throws IOException {
			if (current != null) {
				sink.entry(current.build());
				current = null;
			}
		}
		
		private static EntryBuilder startImplicitEntry(TEFSpan span) {
			return new EntryBuilder("", "", span);
		}
	}
	
	/** Collects everything into a TEFDocument for callers that want it whole. */
	private static final class Collector implements TEFSink {
		final List<TEFComment> comments = new ArrayList<TEFComment>();
		final List<TEFDirective> directives = new ArrayList<TEFDirective>();
		final List<TEFEntry> entries = new ArrayList<TEFEntry>();
		
		@Override
		public void documentComment(TEFComment comment) {
			comments.add(comment);
		}
		
		@Override
		public void documentDirective(TEFDirective directive) {
			directives.add(directive);
		}
		
		@Override
		public void entry(TEFEntry entry) {
			entries.add(entry);
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
