package net.nuke24.scratch38.s0029;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a raw #HELO message (a single contiguous buffer) into tagged spans,
 * per https://www.nuke24.net/docs/2025/HELO.html. Produced tokens are zero-copy
 * views into the original buffer; concatenating all tokens' content reproduces
 * the original message, except for header-value continuation tabs, which the
 * spec says are not part of the value they continue.
 * 
 * This is a lenient-enough-to-work prototype, not a validating parser: a missing
 * "#HELO" magic throws, but most other malformed input just produces odd tokens
 * rather than an error (e.g. "#" directive lines aren't special-cased, since none
 * are yet defined by the spec).
 */
public class HELOTokenizer {
	private static final byte[] MAGIC = ByteArrayUtil.ascii("#HELO");
	
	private final byte[] buf;
	private final int end;
	private int pos;
	private final List<Tagged<HELOChunkType,ByteBlob>> tokens = new ArrayList<Tagged<HELOChunkType,ByteBlob>>();
	
	private HELOTokenizer(ByteChunk message) {
		this.buf = message.buffer;
		this.pos = message.offset;
		this.end = message.offset + message.length;
	}
	
	public static List<Tagged<HELOChunkType,ByteBlob>> tokenize(ByteChunk message) {
		HELOTokenizer t = new HELOTokenizer(message);
		t.run();
		return t.tokens;
	}
	
	public static List<Tagged<HELOChunkType,ByteBlob>> tokenize(byte[] message) {
		return tokenize(ByteChunk.of(message));
	}
	
	private boolean atEnd() { return pos >= end; }
	private byte at(int i) { return buf[i]; }
	private static boolean isDigit(byte b) { return b >= '0' && b <= '9'; }
	
	private void emit(HELOChunkType tag, int start, int stop) {
		if( stop > start ) tokens.add(new Tagged<HELOChunkType,ByteBlob>(tag, new ByteChunk(buf, start, stop-start)));
	}
	
	private void run() {
		parseFirstLine();
		if( !atEnd() ) parseHeadersAndPayload();
	}
	
	private void parseFirstLine() {
		int start = pos;
		for( int i=0; i<MAGIC.length; ++i ) {
			if( atEnd() || at(pos) != MAGIC[i] ) {
				throw new IllegalArgumentException("Message does not begin with \"#HELO\"");
			}
			++pos;
		}
		emit(HELOChunkType.HELO_MAGIC, start, pos);
		
		// Up to two "/segment" groups: version, then method; if only one is given,
		// guess based on whether it's all-digits (per the spec's worked examples).
		// Delimiters and segments are collected first so tags can be decided before
		// emitting, while still emitting everything in original left-to-right order.
		List<int[]> delimiters = new ArrayList<int[]>();
		List<int[]> segments = new ArrayList<int[]>();
		while( segments.size() < 2 && !atEnd() && at(pos) == '/' ) {
			int delimStart = pos;
			delimiters.add(new int[] { delimStart, ++pos });
			int segStart = pos;
			while( !atEnd() && at(pos) != '/' && at(pos) != ' ' && at(pos) != '\n' ) ++pos;
			segments.add(new int[] { segStart, pos });
		}
		HELOChunkType[] segmentTags = new HELOChunkType[segments.size()];
		if( segments.size() == 1 ) {
			int[] seg = segments.get(0);
			boolean numeric = seg[1] > seg[0];
			for( int i=seg[0]; i<seg[1] && numeric; ++i ) numeric = isDigit(at(i));
			segmentTags[0] = numeric ? HELOChunkType.HELO_VERSION : HELOChunkType.METHOD;
		} else if( segments.size() == 2 ) {
			segmentTags[0] = HELOChunkType.HELO_VERSION;
			segmentTags[1] = HELOChunkType.METHOD;
		}
		for( int i=0; i<segments.size(); ++i ) {
			emit(HELOChunkType.DELIMITER, delimiters.get(i)[0], delimiters.get(i)[1]);
			emit(segmentTags[i], segments.get(i)[0], segments.get(i)[1]);
		}
		
		if( !atEnd() && at(pos) == ' ' ) {
			emit(HELOChunkType.DELIMITER, pos, ++pos);
			int pathStart = pos;
			while( !atEnd() && at(pos) != '\n' ) ++pos;
			emit(HELOChunkType.RESOURCE_PATH, pathStart, pos);
		}
		
		if( !atEnd() && at(pos) == '\n' ) emit(HELOChunkType.DELIMITER, pos, ++pos);
	}
	
	private void parseHeadersAndPayload() {
		while( !atEnd() ) {
			if( at(pos) == '\n' ) {
				emit(HELOChunkType.DELIMITER, pos, ++pos);
				emit(HELOChunkType.PAYLOAD, pos, end);
				pos = end;
				return;
			}
			
			int nameStart = pos;
			while( !atEnd() && at(pos) != ' ' && at(pos) != '\n' ) ++pos;
			emit(HELOChunkType.HEADER_NAME, nameStart, pos);
			
			if( !atEnd() && at(pos) == ' ' ) {
				emit(HELOChunkType.DELIMITER, pos, ++pos);
				parseHeaderValue();
			}
			
			if( !atEnd() && at(pos) == '\n' ) emit(HELOChunkType.DELIMITER, pos, ++pos);
		}
	}
	
	/** Scans a header value, honoring "\n\t" as a non-value-terminating line continuation. */
	private void parseHeaderValue() {
		List<ByteBlob> pieces = new ArrayList<ByteBlob>();
		int segStart = pos;
		while( true ) {
			if( atEnd() || at(pos) == '\n' ) {
				boolean continuation = !atEnd() && pos+1 < end && at(pos+1) == '\t';
				// Continued pieces keep their linefeed; the final piece excludes it
				// (the caller emits that linefeed itself, as a DELIMITER token).
				int segStop = continuation ? pos+1 : pos;
				pieces.add(new ByteChunk(buf, segStart, segStop-segStart));
				if( !continuation ) break;
				pos = segStop + 1; // past the linefeed and the continuation tab
				segStart = pos;
				continue;
			}
			++pos;
		}
		ByteBlob value = ByteBlobs.concat(pieces);
		if( value.length() > 0 ) tokens.add(new Tagged<HELOChunkType,ByteBlob>(HELOChunkType.HEADER_VALUE, value));
	}
}
