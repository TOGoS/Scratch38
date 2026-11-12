package net.nuke24.togetl.tef;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
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
import net.nuke24.togetl.tef.LLChunks.SyntaxError;
import net.nuke24.togetl.util.Tolerance;

/**
 * Reads a TEF document into entries, comments, directives, and issues.
 *
 * <p>The lexer works on bytes; this class strips a UTF-8 BOM, validates the
 * remaining input, drives the incremental parser, merges chunks, and
 * assembles entries. Recoverable problems become {@link TEFIssue}s under
 * {@link Tolerance#WARN} and {@link Tolerance#LENIENT}; under
 * {@link Tolerance#STRICT} the first problem aborts the read.</p>
 */
public final class TEFReader {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final byte[] EMPTY_BYTES = new byte[0];
    private static final int FEED_BLOCK_SIZE = 65536;

    private TEFReader() {
    }

    public static TEFDocument read(String sourceIri, byte[] input) throws TEFReadException {
        return read(sourceIri, input, Tolerance.WARN);
    }

    public static TEFDocument read(String sourceIri, byte[] input, Tolerance tolerance)
            throws TEFReadException {
        if (sourceIri == null || sourceIri.isEmpty()) {
            throw new IllegalArgumentException("sourceIri must not be empty");
        }
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        if (tolerance == null) {
            throw new IllegalArgumentException("tolerance must not be null");
        }

        long byteLength = input.length;
        boolean bom = hasUtf8Bom(input);
        byte[] bytes = bom ? Arrays.copyOfRange(input, 3, input.length) : input;

        List<TEFIssue> issues = new ArrayList<TEFIssue>();
        checkUtf8(bytes, issues, tolerance);

        List<Chunk> rawChunks = parseChunks(bytes);
        Chunk[] mergedChunks = mergeChunks(rawChunks);

        return assemble(sourceIri, byteLength, bom, bytes, mergedChunks, issues, tolerance);
    }

    public static TEFDocument readFile(String sourceIri, File file) throws IOException, TEFReadException {
        return readFile(sourceIri, file, Tolerance.WARN);
    }

    public static TEFDocument readFile(String sourceIri, File file, Tolerance tolerance)
            throws IOException, TEFReadException {
        return read(sourceIri, Files.readAllBytes(file.toPath()), tolerance);
    }

    private static boolean hasUtf8Bom(byte[] input) {
        return input.length >= 3
                && (input[0] & 0xFF) == 0xEF
                && (input[1] & 0xFF) == 0xBB
                && (input[2] & 0xFF) == 0xBF;
    }

    private static void checkUtf8(byte[] bytes, List<TEFIssue> issues, Tolerance tolerance)
            throws TEFReadException {
        int invalidOffset = findInvalidUtf8Offset(bytes);
        if (invalidOffset < 0) {
            return;
        }
        TEFSpan span = spanAt(bytes, invalidOffset);
        addIssue(
                new TEFIssue(
                        TEFIssue.Severity.ERROR,
                        "invalid-utf8",
                        "Invalid UTF-8 byte sequence",
                        span,
                        null),
                issues,
                tolerance);
    }

    private static List<Chunk> parseChunks(byte[] bytes) throws TEFReadException {
        List<Chunk> chunks = new ArrayList<Chunk>();
        DucerState2<byte[], Chunk[]> state = TEFParser.INIT;
        try {
            for (int offset = 0; offset < bytes.length; offset += FEED_BLOCK_SIZE) {
                int end = Math.min(bytes.length, offset + FEED_BLOCK_SIZE);
                byte[] block = Arrays.copyOfRange(bytes, offset, end);
                state = state.process(new DucerChunk<byte[]>(block, false));
                collectChunks(state.output, chunks);
            }
            state = state.process(new DucerChunk<byte[]>(EMPTY_BYTES, true));
            collectChunks(state.output, chunks);
        } catch (RuntimeException e) {
            throw new TEFReadException("TEF parse failed: " + e.getMessage(), e);
        }
        return chunks;
    }

    private static void collectChunks(DucerChunk<Chunk[]> output, List<Chunk> chunks) {
        if (output == null || output.payload == null) {
            return;
        }
        Collections.addAll(chunks, output.payload);
    }

    private static Chunk[] mergeChunks(List<Chunk> rawChunks) throws TEFReadException {
        try {
            Chunk[] array = rawChunks.toArray(new Chunk[rawChunks.size()]);
            DucerState2<Chunk[], Chunk[]> state =
                    LLChunkMerger.FULL.process(new DucerChunk<Chunk[]>(array, true));
            return state.output.payload;
        } catch (RuntimeException e) {
            throw new TEFReadException("TEF chunk merge failed: " + e.getMessage(), e);
        }
    }

    private static TEFDocument assemble(
            String sourceIri,
            long byteLength,
            boolean bom,
            byte[] bytes,
            Chunk[] chunks,
            List<TEFIssue> issues,
            Tolerance tolerance
    ) throws TEFReadException {
        List<TEFEntry> entries = new ArrayList<TEFEntry>();
        List<TEFComment> documentComments = new ArrayList<TEFComment>();
        List<TEFDirective> documentDirectives = new ArrayList<TEFDirective>();

        EntryBuilder current = null;
        for (Chunk chunk : chunks) {
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
                if (current == null) {
                    current = startImplicitEntry(chunk.getSpan());
                }
                HeaderValuePiece piece = (HeaderValuePiece) chunk;
                current.appendOrphanContent(recoverOrphanText(piece, bytes));
                current.touch(piece.getSpan());
            } else if (chunk instanceof SyntaxError) {
                SyntaxError error = (SyntaxError) chunk;
                addIssue(
                        new TEFIssue(
                                TEFIssue.Severity.ERROR,
                                codeForSyntaxError(error.message),
                                error.message,
                                error.getSpan(),
                                null),
                        issues,
                        tolerance);
            }
        }
        if (current != null) {
            entries.add(current.build());
        }

        return new TEFDocument(
                sourceIri,
                byteLength,
                bom,
                documentComments,
                documentDirectives,
                entries,
                issues);
    }

    private static EntryBuilder startImplicitEntry(TEFSpan span) {
        return new EntryBuilder("", "", span);
    }

    /**
     * Reconstructs the original text of an orphan continuation block from its
     * byte span. The parser discards the leading whitespace byte, so using the
     * span keeps the source text intact. If the span is unusable, fall back to
     * the parsed value.
     */
    private static String recoverOrphanText(HeaderValuePiece piece, byte[] bytes) {
        TEFSpan span = piece.getSpan();
        if (span != null
                && span != TEFSpan.NONE
                && span.byteStart >= 0
                && span.byteEnd >= span.byteStart
                && span.byteEnd <= bytes.length) {
            int start = (int) span.byteStart;
            int end = (int) span.byteEnd;
            return new String(bytes, start, end - start, UTF8);
        }
        return piece.data;
    }

    private static String codeForSyntaxError(String message) {
        if (message.startsWith("Illegal header continuation")) {
            return "orphan-continuation";
        }
        if (message.startsWith("Improperly terminated header key")) {
            return "unterminated-header-key";
        }
        if (message.startsWith("Missing space after header colon")) {
            return "tight-header-colon";
        }
        if (message.startsWith("Header line has no colon")) {
            return "header-without-colon";
        }
        return "syntax-error";
    }

    private static void addIssue(TEFIssue issue, List<TEFIssue> issues, Tolerance tolerance)
            throws TEFReadException {
        if (tolerance == Tolerance.STRICT) {
            throw new TEFReadException(formatIssue(issue));
        }
        issues.add(issue);
    }

    private static String formatIssue(TEFIssue issue) {
        String location = issue.getSpan() == null
                ? "-"
                : issue.getSpan().toLocation().toString();
        return location + ": " + issue.getCode() + ": " + issue.getMessage();
    }

    private static TEFSpan spanAt(byte[] bytes, int offset) {
        int line = 1;
        int column = 1;
        for (int i = 0; i < offset; i++) {
            if (bytes[i] == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return TEFSpan.point(offset, line, column);
    }

    /**
     * @return the offset of the first invalid UTF-8 byte, or -1 if the whole
     *         array is valid UTF-8.
     */
    private static int findInvalidUtf8Offset(byte[] bytes) {
        int i = 0;
        while (i < bytes.length) {
            int b = bytes[i] & 0xFF;
            if (b < 0x80) {
                i++;
                continue;
            }

            int continuationBytes;
            int minimumCodePoint;
            if ((b & 0xE0) == 0xC0) {
                continuationBytes = 1;
                minimumCodePoint = 0x80;
            } else if ((b & 0xF0) == 0xE0) {
                continuationBytes = 2;
                minimumCodePoint = 0x800;
            } else if ((b & 0xF8) == 0xF0) {
                continuationBytes = 3;
                minimumCodePoint = 0x10000;
            } else {
                return i;
            }

            if (i + continuationBytes >= bytes.length) {
                return i;
            }

            int[] initialMasks = {0x7F, 0x1F, 0x0F, 0x07};
            int codePoint = b & initialMasks[continuationBytes];
            for (int j = 1; j <= continuationBytes; j++) {
                int continuation = bytes[i + j] & 0xFF;
                if ((continuation & 0xC0) != 0x80) {
                    return i;
                }
                codePoint = (codePoint << 6) | (continuation & 0x3F);
            }

            if (codePoint < minimumCodePoint
                    || codePoint > 0x10FFFF
                    || (codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
                return i;
            }
            i += continuationBytes + 1;
        }
        return -1;
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

        void appendOrphanContent(String text) {
            if (content.length() > 0) {
                content.append('\n');
            }
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
