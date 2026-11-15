/*
 * Derived from TScript34, Copyright (c) 2023 Dan Stevens.
 * Licensed under the MIT License; see NOTICE.
 */
package net.nuke24.togetl.tef;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.nuke24.togetl.ducer.DucerChunk;
import net.nuke24.togetl.ducer.DucerState2;
import net.nuke24.togetl.ducer.Function;
import net.nuke24.togetl.tef.LLChunks.Chunk;
import net.nuke24.togetl.tef.LLChunks.Comment;
import net.nuke24.togetl.tef.LLChunks.ContentPiece;
import net.nuke24.togetl.tef.LLChunks.Directive;
import net.nuke24.togetl.tef.LLChunks.HeaderKey;
import net.nuke24.togetl.tef.LLChunks.HeaderValuePiece;
import net.nuke24.togetl.tef.LLChunks.NewEntryLine;
import net.nuke24.togetl.tef.util.ArrayUtil;

/**
 * Incremental TEF lexer. Derived from TScript34-P0014 (MIT), extended with
 * source spans, comments, and directives.
 *
 * <p>Offsets are absolute byte offsets into the (BOM-stripped) input.
 * Columns are one-based byte columns.</p>
 */
public final class TEFParser
		implements Function<DucerChunk<byte[]>, DucerState2<byte[], Chunk[]>> {
	private static final byte[] EMPTY_BYTES = new byte[0];
	private static final Chunk[] EMPTY_CHUNKS = new Chunk[0];
	private static final byte[] LF = new byte[]{'\n'};
	private static final DucerChunk<byte[]> PASSED = new DucerChunk<byte[]>(EMPTY_BYTES, true);
	
	public static final DucerState2<byte[], Chunk[]> INIT = new DucerState2<byte[], Chunk[]>(
			new TEFParser(State.HEADER, 1, 0, 0, null, EMPTY_BYTES, 0),
			PASSED,
			new DucerChunk<Chunk[]>(EMPTY_CHUNKS, false));
			
	private enum State {
		NEW_ENTRY,
		HEADER,
		HEADER_OR_CONTINUATION_LF,
		HEADER_OR_CONTINUATION_CRLF,
		HEADER_VALUE,
		POST_HEADER_KEY,
		CONTENT_BEGIN,
		CONTENT
	}
	
	private static final Pattern NEW_ENTRY_LINE_PATTERN =
			Pattern.compile("^=([^\\s]*)(?:$|\\s+([^\\r\\n]*))[\\r\\n]?$");
			
	private static final Charset UTF8 = Charset.forName("UTF-8");
	
	private final State state;
	private final int lineNum;
	private final long lineStartOffset;
	private final long baseOffset;
	private final TEFSpan pendingNewlineSpan;
	private final byte[] remaining;
	private final int remainingOffset;
	
	private TEFParser(
			State state,
			int lineNum,
			long lineStartOffset,
			long baseOffset,
			TEFSpan pendingNewlineSpan,
			byte[] remaining,
			int remainingOffset
	) {
		this.state = state;
		this.lineNum = lineNum;
		this.lineStartOffset = lineStartOffset;
		this.baseOffset = baseOffset;
		this.pendingNewlineSpan = pendingNewlineSpan;
		this.remaining = remaining;
		this.remainingOffset = remainingOffset;
	}
	
	private static String joiner(State state) {
		return state == State.HEADER_OR_CONTINUATION_CRLF ? "\r\n" : "\n";
	}
	
	private static boolean isHorizontalWhitespace(int b) {
		return b == ' ' || b == '\t';
	}
	
	private static boolean isWhitespace(int b) {
		return b == ' ' || b == '\t' || b == '\r' || b == '\n';
	}
	
	/**
	 * A compact header is one written as "key:value" with no space after the
	 * colon. The TEF spec requires the space, but music.txt uses the compact
	 * form for x-rdf-subject. Only accept it when the key has no whitespace and
	 * the value looks URI-like (contains a colon); otherwise the line is more
	 * likely content that lost its preceding blank line.
	 */
	private static boolean looksLikeTightHeader(byte[] remaining, int start, int colon) {
		if (colon <= start) {
			return false;
		}
		for (int i = start; i < colon; i++) {
			if (isWhitespace(remaining[i])) {
				return false;
			}
		}
		if (colon + 1 >= remaining.length) {
			return false;
		}
		int next = remaining[colon + 1];
		if (next == '\r' || next == '\n') {
			return false;
		}
		for (int i = colon + 1; i < remaining.length; i++) {
			if (remaining[i] == ':') {
				return true;
			}
			if (remaining[i] == '\n') {
				break;
			}
		}
		return false;
	}
	
	private static TEFSpan makeSpan(
			byte[] remaining,
			long baseOffset,
			int localStart,
			int localEnd,
			int startLine,
			long lineStartOffset
	) {
		long globalStart = baseOffset + localStart;
		int column = (int) (globalStart - lineStartOffset) + 1;
		int endLine = startLine;
		int endColumn = column;
		for (int i = localStart; i < localEnd; i++) {
			if (remaining[i] == '\n') {
				endLine++;
				endColumn = 1;
			} else {
				endColumn++;
			}
		}
		return new TEFSpan(globalStart, baseOffset + localEnd, startLine, column, endLine, endColumn);
	}
	
	@Override
	public DucerState2<byte[], Chunk[]> apply(DucerChunk<byte[]> input) {
		State state = this.state;
		int lineNum = this.lineNum;
		long lineStartOffset = this.lineStartOffset;
		long baseOffset = this.baseOffset;
		TEFSpan pendingNewlineSpan = this.pendingNewlineSpan;
		byte[] remaining = this.remaining;
		int remainingOffset = this.remainingOffset;
		final boolean isEnd = input.isEnd;
		
		if (input.payload.length > 0) {
			baseOffset += remainingOffset;
			remaining = ArrayUtil.concat(remaining, remainingOffset, input.payload);
			remainingOffset = 0;
		}
		
		List<Chunk> output = new ArrayList<Chunk>();
		Matcher matcher;
		int eolIndex;
		
		parse:
		while (remaining.length > remainingOffset) {
			int char0 = remaining[remainingOffset];
			
			switch (state) {
				case NEW_ENTRY: {
					assert char0 == '=';
					int start = remainingOffset;
					eolIndex = ArrayUtil.find(remaining, remainingOffset, LF, isEnd);
					if (eolIndex == -1 && isEnd) {
						eolIndex = remaining.length;
					} else if (eolIndex < 0) {
						break parse;
					}
					String line = new String(remaining, start, eolIndex - start, UTF8);
					matcher = NEW_ENTRY_LINE_PATTERN.matcher(line);
					if (!matcher.matches()) {
						throw new RuntimeException(
								"Entry line did not match pattern: " + line);
					}
					int spanEnd = Math.min(remaining.length, eolIndex + 1);
					output.add(NewEntryLine.of(
							matcher.group(1),
							matcher.group(2),
							makeSpan(remaining, baseOffset, start, spanEnd, lineNum, lineStartOffset)));
					remainingOffset = spanEnd;
					lineStartOffset = baseOffset + remainingOffset;
					++lineNum;
					pendingNewlineSpan = null;
					state = State.HEADER;
					continue parse;
				}
				
				case HEADER:
				case HEADER_OR_CONTINUATION_LF:
				case HEADER_OR_CONTINUATION_CRLF: {
					if (isHorizontalWhitespace(char0)) {
						if (state == State.HEADER) {
							int column = (int) (baseOffset + remainingOffset - lineStartOffset) + 1;
							throw new TEFSyntaxException(
									"Unexpected indented line; headers must not be indented, "
									+ "and content must follow a blank line",
									TEFSpan.point(baseOffset + remainingOffset, lineNum, column));
						}
						if (pendingNewlineSpan != null) {
							output.add(new HeaderValuePiece(joiner(state), pendingNewlineSpan));
							pendingNewlineSpan = null;
						} else {
							int column = (int) (baseOffset + remainingOffset - lineStartOffset) + 1;
							output.add(new HeaderValuePiece(
									joiner(state),
									TEFSpan.point(baseOffset + remainingOffset, lineNum, column)));
						}
						++remainingOffset;
						state = State.HEADER_VALUE;
						continue parse;
					} else if (char0 == '=') {
						pendingNewlineSpan = null;
						if (remaining.length >= remainingOffset + 2) {
							if (remaining[remainingOffset + 1] == '=') {
								++remainingOffset;
							} else if (!isEnd && remaining.length == remainingOffset + 1) {
								break parse;
							} else {
								state = State.NEW_ENTRY;
								continue parse;
							}
						} else if (!isEnd) {
							break parse;
						} else {
							state = State.NEW_ENTRY;
							continue parse;
						}
					} else if (char0 == '#') {
						int start = remainingOffset;
						int commentEnd = -1;
						for (int i = remainingOffset; i < remaining.length; i++) {
							if (remaining[i] == '\n') {
								commentEnd = i;
								break;
							}
						}
						if (commentEnd < 0) {
							if (!isEnd) break parse;
							commentEnd = remaining.length;
						}
						String text = new String(remaining, start, commentEnd - start, UTF8);
						int spanEnd = Math.min(remaining.length, commentEnd + 1);
						TEFSpan span = makeSpan(
								remaining, baseOffset, start, spanEnd, lineNum, lineStartOffset);
						if (LLChunks.isCommentLine(text)) {
							output.add(new Comment(text, span));
						} else {
							output.add(Directive.parse(text, span));
						}
						remainingOffset = spanEnd;
						lineStartOffset = baseOffset + remainingOffset;
						++lineNum;
						pendingNewlineSpan = null;
						state = State.HEADER;
						continue parse;
					} else if (char0 == '\r') {
						++remainingOffset;
						continue parse;
					} else if (char0 == '\n') {
						int newlineLocal = remainingOffset;
						++remainingOffset;
						lineStartOffset = baseOffset + newlineLocal + 1;
						++lineNum;
						pendingNewlineSpan = null;
						state = State.CONTENT_BEGIN;
						continue parse;
					}
					
					// Find the colon that ends the header key. The spec requires
					// whitespace after the colon because keys may contain colons.
					// If no such colon exists, recover the line either as a
					// compact header (key:value) or as content.
					int firstColon = -1;
					for (int i = remainingOffset; i < remaining.length; ++i) {
						if (remaining[i] == ':') {
							if (firstColon < 0) {
								firstColon = i;
							}
							if ((remaining.length >= i + 2 && isWhitespace(remaining[i + 1]))
									|| (remaining.length == i + 1 && isEnd)) {
								output.add(new HeaderKey(
										new String(remaining, remainingOffset, i - remainingOffset, UTF8),
										makeSpan(
												remaining,
												baseOffset,
												remainingOffset,
												i,
												lineNum,
												lineStartOffset)));
								remainingOffset = i + 1;
								pendingNewlineSpan = null;
								state = State.POST_HEADER_KEY;
								continue parse;
							}
						} else if (remaining[i] == '\n') {
							if (looksLikeTightHeader(remaining, remainingOffset, firstColon)) {
								int column = (int) (baseOffset + firstColon - lineStartOffset) + 1;
								throw new TEFSyntaxException(
										"Missing space after header colon; write 'key: value'",
										TEFSpan.point(baseOffset + firstColon, lineNum, column));
							}
							throw new TEFSyntaxException(
									"Header line has no 'key: value' colon; "
									+ "content must follow a blank line",
									makeSpan(
											remaining,
											baseOffset,
											remainingOffset,
											i + 1,
											lineNum,
											lineStartOffset));
						}
					}
					if (isEnd) {
						if (looksLikeTightHeader(remaining, remainingOffset, firstColon)) {
							int column = (int) (baseOffset + firstColon - lineStartOffset) + 1;
							throw new TEFSyntaxException(
									"Missing space after header colon; write 'key: value'",
									TEFSpan.point(baseOffset + firstColon, lineNum, column));
						}
						throw new TEFSyntaxException(
								"Header line has no 'key: value' colon; "
								+ "content must follow a blank line",
								makeSpan(
										remaining,
										baseOffset,
										remainingOffset,
										remaining.length,
										lineNum,
										lineStartOffset));
					}
					break parse;
				}
				
				case POST_HEADER_KEY:
					if (char0 == '\n') {
						int newlineLocal = remainingOffset;
						pendingNewlineSpan = makeSpan(
								remaining,
								baseOffset,
								newlineLocal,
								newlineLocal + 1,
								lineNum,
								lineStartOffset);
						++remainingOffset;
						lineStartOffset = baseOffset + newlineLocal + 1;
						++lineNum;
						state = State.HEADER_OR_CONTINUATION_LF;
						continue parse;
					}
					++remainingOffset;
					state = State.HEADER_VALUE;
					continue parse;
					
				case HEADER_VALUE: {
					eolIndex = ArrayUtil.find(remaining, remainingOffset, LF, isEnd);
					if (eolIndex < 0 && !isEnd) break parse;
					if (eolIndex == -1) eolIndex = remaining.length;
					int nextLineStart = eolIndex + 1;
					int valueEnd = eolIndex;
					boolean crlf = false;
					if (eolIndex > remainingOffset && remaining[eolIndex - 1] == '\r') {
						valueEnd = eolIndex - 1;
						crlf = true;
					}
					if (valueEnd > remainingOffset) {
						output.add(new HeaderValuePiece(
								new String(remaining, remainingOffset, valueEnd - remainingOffset, UTF8),
								makeSpan(
										remaining,
										baseOffset,
										remainingOffset,
										valueEnd,
										lineNum,
										lineStartOffset)));
					}
					int newlineStart = crlf ? eolIndex - 1 : eolIndex;
					int newlineEnd = Math.min(remaining.length, eolIndex + 1);
					pendingNewlineSpan = makeSpan(
							remaining,
							baseOffset,
							newlineStart,
							newlineEnd,
							lineNum,
							lineStartOffset);
					++lineNum;
					remainingOffset = Math.min(remaining.length, nextLineStart);
					lineStartOffset = baseOffset + remainingOffset;
					state = crlf ? State.HEADER_OR_CONTINUATION_CRLF : State.HEADER_OR_CONTINUATION_LF;
					continue parse;
				}
				
				case CONTENT_BEGIN:
					if (char0 == '=') {
						int nextChar = remaining.length >= remainingOffset + 2
								? remaining[remainingOffset + 1]
								: isEnd ? -1 : -2;
						if (nextChar == -2) break parse;
						if (nextChar == '=') {
							++remainingOffset;
						} else {
							state = State.NEW_ENTRY;
							continue parse;
						}
					}
					state = State.CONTENT;
					continue parse;
					
				case CONTENT: {
					int contentStartLine = lineNum;
					long contentStartLineOffset = lineStartOffset;
					int eofPseudoChar = isEnd ? -1 : -2;
					for (int i = remainingOffset; i < remaining.length; ++i) {
						char0 = remaining[i];
						if (char0 == '\n') {
							int char1 = remaining.length >= i + 2 ? remaining[i + 1] : eofPseudoChar;
							int char2 = remaining.length >= i + 3 ? remaining[i + 2] : eofPseudoChar;
							checkForEnd:
							switch (char1) {
								case -2:
									if (i > remainingOffset) {
										output.add(new ContentPiece(
												Arrays.copyOfRange(remaining, remainingOffset, i),
												makeSpan(
														remaining,
														baseOffset,
														remainingOffset,
														i,
														contentStartLine,
														contentStartLineOffset)));
										remainingOffset = i;
									}
									break parse;
								case '=':
									switch (char2) {
										case -2:
											if (i > remainingOffset) {
												output.add(new ContentPiece(
														Arrays.copyOfRange(remaining, remainingOffset, i),
														makeSpan(
																remaining,
																baseOffset,
																remainingOffset,
																i,
																contentStartLine,
																contentStartLineOffset)));
												remainingOffset = i;
											}
											break parse;
										case '=': {
											int contentEnd = i + 1;
											output.add(new ContentPiece(
													Arrays.copyOfRange(remaining, remainingOffset, contentEnd),
													makeSpan(
															remaining,
															baseOffset,
															remainingOffset,
															contentEnd,
															contentStartLine,
															contentStartLineOffset)));
											remainingOffset = contentEnd;
											lineStartOffset = baseOffset + contentEnd;
											++lineNum;
											state = State.CONTENT_BEGIN;
											continue parse;
										}
										default:
											output.add(new ContentPiece(
													Arrays.copyOfRange(remaining, remainingOffset, i),
													makeSpan(
															remaining,
															baseOffset,
															remainingOffset,
															i,
															contentStartLine,
															contentStartLineOffset)));
											remainingOffset = i + 1;
											lineStartOffset = baseOffset + remainingOffset;
											++lineNum;
											state = State.NEW_ENTRY;
											continue parse;
									}
								default:
									++lineNum;
									lineStartOffset = baseOffset + i + 1;
									break checkForEnd;
							}
						}
					}
					output.add(new ContentPiece(
							Arrays.copyOfRange(remaining, remainingOffset, remaining.length),
							makeSpan(
									remaining,
									baseOffset,
									remainingOffset,
									remaining.length,
									contentStartLine,
									contentStartLineOffset)));
					remainingOffset = remaining.length;
					break parse;
				}
				
				default:
					throw new IllegalStateException("Unhandled parser state: " + state);
			}
		}
		
		return new DucerState2<byte[], Chunk[]>(
				new TEFParser(
						state,
						lineNum,
						lineStartOffset,
						baseOffset,
						pendingNewlineSpan,
						remaining,
						remainingOffset),
				PASSED,
				new DucerChunk<Chunk[]>(output.toArray(new Chunk[output.size()]), isEnd));
	}
}
